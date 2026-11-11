package com.aura.aura_ui.b2

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal AWS Signature Version 4 signer for S3-compatible endpoints (Backblaze B2).
 *
 * Deliberately hand-rolled: adding the AWS SDK would cost megabytes and DEX limits for one
 * transport. This covers exactly what AURA needs — signed PUT/GET/HEAD with an empty or
 * arbitrary payload, path-style URLs and sorted query parameters.
 */
internal object S3v4 {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val REGION = "us-east-005"

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    /** RFC 3986 URI encoding as required by SigV4 (unreserved characters pass through). */
    fun uriEncode(value: String): String {
        val sb = StringBuilder()
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val unreserved = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code ||
                c in '0'.code..'9'.code || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (unreserved) sb.append(c.toChar()) else sb.append("%%%02X".format(c))
        }
        return sb.toString()
    }

    data class SignedRequest(
        val headers: Map<String, String>,
        val payloadHash: String,
    )

    /**
     * Signs a request against an S3-compatible endpoint. [path] is the object key path including
     * the leading "/bucket/key"; [query] is decoded parameters (they get sorted and encoded).
     */
    fun sign(
        method: String,
        bucket: String,
        key: String,
        query: Map<String, String> = emptyMap(),
        payload: ByteArray,
        accessKeyId: String,
        secretAccessKey: String,
        endpoint: String,
        now: Instant = Instant.now(),
    ): SignedRequest {
        // Derive the region from host s3.<region>.backblazeb2.com; default us-east-005.
        val region = Regex("^s3\\.([a-z0-9-]+)\\.backblazeb2\\.com$", RegexOption.IGNORE_CASE)
            .find(endpoint)?.groupValues?.get(1) ?: REGION

        val amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now)
        val dateStamp = amzDate.substring(0, 8)
        val payloadHash = sha256Hex(payload)

        // Path-style: /<bucket>/<key>, each segment URI-encoded, slashes preserved.
        val encodedKey = key.split('/').joinToString("/") { uriEncode(it) }
        val encodedBucket = uriEncode(bucket)
        val canonicalUri = if (key.isBlank()) "/" + encodedBucket else "/" + encodedBucket + "/" + encodedKey

        val canonicalQuery = query.entries
            .map { (k, v) -> uriEncode(k) to uriEncode(v) }
            .sortedBy { it.first }
            .joinToString("&") { "${it.first}=${it.second}" }

        val host = endpoint
        val headers = sortedMapOf(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
        )
        // Exactly one trailing newline after the header block; the request assembly adds the
        // empty line that separates it from the signed-headers line.
        val canonicalHeaders = headers.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n"
        val signedHeaders = headers.keys.joinToString(";")

        val canonicalRequest = buildString {
            append(method).append('\n')
            append(canonicalUri).append('\n')
            append(canonicalQuery).append('\n')
            append(canonicalHeaders)
            append(signedHeaders).append('\n')
            append(payloadHash)
        }

        val scope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign = buildString {
            append(ALGORITHM).append('\n')
            append(amzDate).append('\n')
            append(scope).append('\n')
            append(sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)))
        }

        var key = ("AWS4" + secretAccessKey).toByteArray(Charsets.UTF_8)
        key = hmacSha256(key, dateStamp.toByteArray(Charsets.UTF_8))
        key = hmacSha256(key, region.toByteArray(Charsets.UTF_8))
        key = hmacSha256(key, SERVICE.toByteArray(Charsets.UTF_8))
        key = hmacSha256(key, "aws4_request".toByteArray(Charsets.UTF_8))
        val signature = hmacSha256(key, stringToSign.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        val authorization = "$ALGORITHM Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"

        return SignedRequest(
            headers = headers + ("authorization" to authorization),
            payloadHash = payloadHash,
        )
    }

    /** Best-effort XML error extraction for readable failure messages. */
    fun extractXmlError(body: String): Pair<String, String> {
        fun tag(name: String): String? =
            Regex("<$name>([^<]+)</$name>").find(body)?.groupValues?.get(1)
        return (tag("Code") ?: "http_error") to (tag("Message") ?: body.take(200))
    }
}
