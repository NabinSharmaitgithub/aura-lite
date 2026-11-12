package com.aura.aura_ui.b2

import java.security.MessageDigest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S3v4Test {

    @Test
    fun `uriEncode keeps unreserved and encodes the rest`() {
        assertEquals("abcXYZ019-_.~", S3v4.uriEncode("abcXYZ019-_.~"))
        assertEquals("a%20b", S3v4.uriEncode("a b"))
        assertEquals("a%2Fb", S3v4.uriEncode("a/b"))
        assertEquals("%C3%A9", S3v4.uriEncode("é"))
    }

    @Test
    fun `sha256Hex matches the empty-string vector`() {
        val digest = S3v4.sha256Hex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", digest)
    }

    @Test
    fun `sha256Hex matches a known text vector`() {
        val digest = S3v4.sha256Hex("abc".toByteArray(Charsets.US_ASCII))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
    }

    @Test
    fun `signed request is deterministic for a fixed clock`() {
        val first = S3v4.sign(
            method = "GET",
            bucket = "aura-lite-storage",
            key = "users/hello.txt",
            query = emptyMap(),
            payload = ByteArray(0),
            accessKeyId = "AKID",
            secretAccessKey = "secret",
            endpoint = "s3.us-east-005.backblazeb2.com",
            now = Instant.parse("2026-11-12T00:00:00Z"),
        )
        val second = S3v4.sign(
            method = "GET",
            bucket = "aura-lite-storage",
            key = "users/hello.txt",
            query = emptyMap(),
            payload = ByteArray(0),
            accessKeyId = "AKID",
            secretAccessKey = "secret",
            endpoint = "s3.us-east-005.backblazeb2.com",
            now = Instant.parse("2026-11-12T00:00:00Z"),
        )
        assertEquals(first.headers["authorization"], second.headers["authorization"])
    }

    @Test
    fun `signed payload hash is sha256 of the body`() {
        val body = "hello".toByteArray()
        val signed = S3v4.sign(
            method = "PUT",
            bucket = "aura-lite-storage",
            key = "users/hello.txt",
            query = emptyMap(),
            payload = body,
            accessKeyId = "AKID",
            secretAccessKey = "secret",
            endpoint = "s3.us-east-005.backblazeb2.com",
            now = Instant.parse("2026-11-12T00:00:00Z"),
        )
        val expected = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
        assertEquals(expected, signed.headers["x-amz-content-sha256"])
        assertEquals(expected, signed.payloadHash)
    }

    @Test
    fun `signature changes with the secret`() {
        val base = signAt("secret-a")
        val other = signAt("secret-b")
        assertNotEquals(base, other)
    }

    @Test
    fun `region is derived from the endpoint host`() {
        val signed = signAt("secret", endpoint = "s3.eu-central-003.backblazeb2.com")
        val credential = signed.headers["authorization"]?.let {
            Regex("Credential=[^/]+/[^,]+").find(it)?.value ?: ""
        }.orEmpty()
        assertTrue(credential.contains("eu-central-003"))
    }

    @Test
    fun `list query is part of the signed canonical string`() {
        val a = signAt("secret", query = mapOf("list-type" to "2", "max-keys" to "1"))
        val b = signAt("secret", query = mapOf("list-type" to "2", "max-keys" to "2"))
        assertNotEquals(a, b)
    }

    private fun signAt(
        secret: String,
        endpoint: String = "s3.us-east-005.backblazeb2.com",
        query: Map<String, String> = emptyMap(),
    ) = S3v4.sign(
        method = "GET",
        bucket = "aura-lite-storage",
        key = "users/x",
        query = query,
        payload = ByteArray(0),
        accessKeyId = "AKID",
        secretAccessKey = secret,
        endpoint = endpoint,
        now = Instant.parse("2026-11-12T00:00:00Z"),
    )
}
