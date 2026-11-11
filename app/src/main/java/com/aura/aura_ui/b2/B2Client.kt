package com.aura.aura_ui.b2

import android.content.Context
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.data.prefs.SecureKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

/**
 * Thin S3-compatible client for the user's own Backblaze B2 bucket.
 *
 * Nothing is in git or in the APK; the bucket name and endpoint come from [AuraSettings] and the
 * application key id + key come from [SecureKeyStore]. When either is missing, every call returns
 * a friendly `Result.failure` instead of throwing.
 */
@Singleton
class B2Client @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AuraSettings,
    private val keys: SecureKeyStore,
) {
    data class Config(
        val endpoint: String,          // e.g. s3.us-east-005.backblazeb2.com (no scheme)
        val bucket: String,
        val keyId: String,
        val applicationKey: String,
    )

    fun config(): Config? {
        val endpoint = settings.b2Endpoint.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (endpoint.isBlank()) return null
        val bucket = settings.b2BucketName.trim()
        val keyId = keys.b2KeyId
        val key = keys.b2ApplicationKey
        if (bucket.isBlank() || keyId == null || key == null) return null
        return Config(endpoint, bucket, keyId, key)
    }

    sealed class B2Error(message: String) : Exception(message) {
        class NotConfigured : B2Error("Backblaze B2 is not configured — set a bucket, endpoint and application key in Settings → Backblaze")
        class Http(val code: Int, detail: String) : B2Error("$code — $detail")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun ping(): Result<String> = call("GET", key = "", query = mapOf("list-type" to "2", "prefix" to "users/", "max-keys" to "1"), payload = ByteArray(0), contentType = null)
        .fold(
            onSuccess = { body -> Result.success("Bucket reachable (${xmlTag(body, "KeyCount") ?: "?"} objects listed)") },
            onFailure = { e -> Result.failure(e) },
        )

    suspend fun list(prefix: String, maxKeys: Int = 20): Result<List<String>> =
        call(
            method = "GET",
            key = "",
            query = mapOf("list-type" to "2", "prefix" to prefix, "max-keys" to maxKeys.toString()),
            payload = ByteArray(0),
            contentType = null,
        ).map { body -> Regex("<Key>([^<]+)</Key>").findAll(body.toString(Charsets.UTF_8)).map { it.groupValues[1] }.toList() }

    suspend fun exists(key: String): Result<Boolean> =
        call("HEAD", key, emptyMap(), ByteArray(0), null).fold(
            onSuccess = { Result.success(true) },
            onFailure = { e -> if (e is B2Error.Http && e.code == 404) Result.success(false) else Result.failure(e) },
        )

    suspend fun upload(objectKey: String, bytes: ByteArray, contentType: String): Result<String> =
        call("PUT", objectKey, emptyMap(), bytes, contentType.ifBlank { "application/octet-stream" })
            .map { objectKey }

    suspend fun download(objectKey: String): Result<ByteArray> =
        call("GET", objectKey, emptyMap(), ByteArray(0), null)

    /** Raw call: returns the body as bytes or throws B2Error. */
    private suspend fun call(
        method: String,
        key: String,
        query: Map<String, String>,
        payload: ByteArray,
        contentType: String?,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cfg = config() ?: return@withContext Result.failure(B2Error.NotConfigured())
        runCatching {
            val path = if (key.isBlank()) "/${cfg.bucket}"
            else "/${cfg.bucket}/" + key.split('/').joinToString("/") { S3v4.uriEncode(it) }
            val signed = S3v4.sign(
                method = method,
                bucket = cfg.bucket,
                key = if (key.isBlank()) "" else key,
                query = query,
                payload = payload,
                accessKeyId = cfg.keyId,
                secretAccessKey = cfg.applicationKey,
                endpoint = cfg.endpoint,
                now = Instant.now(),
            )
            val builder = Request.Builder()
                .url("https://${cfg.endpoint}$path" + if (query.isEmpty()) "" else "?" + query.entries.map { (k, v) -> "$k=${S3v4.uriEncode(v)}" }.joinToString("&"))
                .method(method, if (payload.isNotEmpty() || method == "PUT" || method == "POST") payload.toRequestBody(contentType?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { it.toMediaType() }.getOrNull() } ?: "application/octet-stream".toMediaType()) else null)
            (signed.headers).forEach { (k, v) -> builder.header(k, v) }
            if (contentType != null) builder.header("content-type", contentType)
            http.newCall(builder.build()).execute().use { response ->
                val body = response.body?.bytes() ?: ByteArray(0)
                if (response.isSuccessful) body
                else {
                    val (code, message) = S3v4.extractXmlError(body.toString(Charsets.UTF_8))
                    throw B2Error.Http(response.code, message)
                }
            }
        }
    }

    private fun xmlTag(body: ByteArray, tag: String): String? =
        Regex("<$tag>([^<]+)</$tag>").find(body.toString(Charsets.UTF_8))?.groupValues?.get(1)
}
