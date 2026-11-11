package com.aura.aura_ui.automation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class DataTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactsResolver,
) {

    fun findFiles(query: String, mime: String?, limit: Int): ToolResult {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val selection = buildString {
            append(MediaStore.Files.FileColumns.DISPLAY_NAME).append(" LIKE ?")
            if (mime != null) append(" AND ").append(MediaStore.Files.FileColumns.MIME_TYPE).append(" LIKE ?")
        }
        val args = buildList {
            add("%$query%")
            if (mime != null) add("$mime%")
        }.toTypedArray()
        val results = mutableListOf<kotlinx.serialization.json.JsonObject>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args,
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val mimeIndex = cursor.getColumnIndexOrThrow(projection[2])
                val sizeIndex = cursor.getColumnIndexOrThrow(projection[3])
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idIndex)
                    results += buildJsonObject {
                        put("name", cursor.getString(nameIndex) ?: "")
                        put("mime", cursor.getString(mimeIndex) ?: "")
                        put("size", cursor.getLong(sizeIndex))
                        put("uri", MediaStore.Files.getContentUri("external", id).toString())
                    }
                }
            }
        }
        return ToolResult.ok(
            observed = buildJsonObject {
                put("files", buildJsonArray { results.forEach { add(it) } })
                put("count", results.size)
            },
        )
    }

    fun openFile(uri: String?, path: String?): ToolResult {
        val target: Uri = when {
            uri != null -> Uri.parse(uri)
            path != null -> {
                val file = File(path)
                if (!file.exists()) return ToolResult.fail(ToolError.notFound("File not found: $path"))
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            else -> return ToolResult.fail(ToolError("invalid_args", "Provide uri or path"))
        }
        val mime = context.contentResolver.getType(target) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(target, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            ToolResult.ok(changed = true, observed = buildJsonObject { put("opened", target.toString()) })
        }.getOrElse { ToolResult.fail(ToolError.notFound("No app can open this file")) }
    }

    fun clipboard(action: String, text: String?): ToolResult {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return when (action) {
            "get" -> ToolResult.ok(
                observed = buildJsonObject {
                    put("text", manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty())
                },
            )
            "set" -> {
                manager.setPrimaryClip(ClipData.newPlainText("AURA", text.orEmpty()))
                ToolResult.ok(changed = true)
            }
            else -> ToolResult.fail(ToolError("invalid_args", "Unknown clipboard action $action"))
        }
    }

    suspend fun resolveContact(query: String, limit: Int): ToolResult {
        val matches = contacts.resolve(query, limit)
        return ToolResult.ok(
            observed = buildJsonObject {
                put(
                    "contacts",
                    buildJsonArray {
                        matches.forEach { match ->
                            add(buildJsonObject {
                                put("id", match.contactId)
                                put("name", match.name)
                                put("score", match.score)
                                put("numbers", buildJsonArray { match.numbers.forEach { add(it) } })
                                put("emails", buildJsonArray { match.emails.forEach { add(it) } })
                            })
                        }
                    },
                )
                put("count", matches.size)
            },
        )
    }

    suspend fun dial(number: String?, contact: String?, directCall: Boolean): ToolResult {
        val resolvedNumber = number ?: contact?.let { query ->
            contacts.resolve(query, 1).firstOrNull()?.numbers?.firstOrNull()
        } ?: return ToolResult.fail(ToolError.notFound("No number or contact matched"))
        if (directCall) {
            return ToolResult.fail(
                ToolError.unsupported("Direct calling is disabled in AURA Lite; opening the dialer instead"),
            )
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(resolvedNumber)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ToolResult.ok(changed = true, observed = buildJsonObject { put("number", resolvedNumber) })
        }.getOrElse { ToolResult.fail(ToolError.internal(it.message ?: "Dialer failed")) }
    }

    suspend fun composeSms(to: String?, body: String?, send: Boolean): ToolResult {
        if (send) {
            return ToolResult.fail(ToolError.unsupported("Sending SMS directly is disabled; AURA opens the composer"))
        }
        val number = to?.let { target ->
            if (target.any { it.isDigit() } && !target.any { it.isLetter() }) target
            else contacts.resolve(target, 1).firstOrNull()?.numbers?.firstOrNull()
        } ?: return ToolResult.fail(ToolError.notFound("No recipient matched"))
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!body.isNullOrBlank()) putExtra("sms_body", body)
        }
        return runCatching {
            context.startActivity(intent)
            ToolResult.ok(changed = true, observed = buildJsonObject { put("to", number) })
        }.getOrElse { ToolResult.fail(ToolError.internal(it.message ?: "SMS composer failed")) }
    }
}
