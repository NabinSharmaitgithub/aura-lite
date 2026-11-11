package com.aura.aura_ui.automation

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.aura.aura_ui.data.db.AuraContactDao
import com.aura.aura_ui.data.db.AuraContactEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.apache.commons.codec.language.DoubleMetaphone

data class ContactMatch(
    val contactId: String,
    val name: String,
    val numbers: List<String>,
    val emails: List<String>,
    val score: Int,
)

/**
 * Four-stage contact resolution: exact alias/name, normalised, phonetic (Double Metaphone),
 * then fuzzy. The cache stores phonetic keys so repeated lookups do not recompute them.
 */
@Singleton
class ContactsResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AuraContactDao,
) {
    private val metaphone = DoubleMetaphone()

    suspend fun resolve(query: String, limit: Int = 5): List<ContactMatch> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val cached = dao.all()
        exact(cached, q, limit).takeIf { it.isNotEmpty() }?.let { return it }
        normalised(cached, q, limit).takeIf { it.isNotEmpty() }?.let { return it }
        phonetic(cached, q, limit).takeIf { it.isNotEmpty() }?.let { return it }
        fuzzy(cached, q, limit).takeIf { it.isNotEmpty() }?.let { return it }

        // Cache is cold or stale: read the provider once.
        runCatching { sync() }
        val refreshed = dao.all()
        return exact(refreshed, q, limit)
            .ifEmpty { normalised(refreshed, q, limit) }
            .ifEmpty { phonetic(refreshed, q, limit) }
            .ifEmpty { fuzzy(refreshed, q, limit) }
    }

    suspend fun sync(): Int {
        val resolver = context.contentResolver
        val map = linkedMapOf<String, MutableContact>()
        queryPhones(resolver, map)
        queryEmails(resolver, map)
        val entities = map.values.map { contact ->
            val key = metaphoneKey(contact.name)
            AuraContactEntity(
                contactId = contact.id,
                displayName = contact.name,
                phoneNumbers = contact.numbers.toList(),
                emails = contact.emails.toList(),
                photoUri = null,
                phoneticKey = key,
                phoneticKeyAlt = if (key.length > 2) key.take(2) else key,
                aliases = emptyList(),
                lastSyncedAt = System.currentTimeMillis(),
            )
        }
        if (entities.isNotEmpty()) dao.upsertAll(entities)
        return entities.size
    }

    private fun queryPhones(resolver: ContentResolver, map: MutableMap<String, MutableContact>) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val numberIndex = cursor.getColumnIndexOrThrow(projection[2])
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    map.getOrPut(id) { MutableContact(id, name) }.numbers += number
                }
            }
        }
    }

    private fun queryEmails(resolver: ContentResolver, map: MutableMap<String, MutableContact>) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
        )
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val emailIndex = cursor.getColumnIndexOrThrow(projection[2])
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val email = cursor.getString(emailIndex) ?: continue
                    map.getOrPut(id) { MutableContact(id, name) }.emails += email
                }
            }
        }
    }

    private fun exact(all: List<AuraContactEntity>, q: String, limit: Int): List<ContactMatch> = all
        .filter {
            it.displayName.equals(q, ignoreCase = true) ||
                it.aliases.any { alias -> alias.equals(q, ignoreCase = true) }
        }
        .take(limit)
        .map { it.toMatch(100) }

    private fun normalised(all: List<AuraContactEntity>, q: String, limit: Int): List<ContactMatch> {
        val target = normalise(q)
        return all.filter { normalise(it.displayName) == target }
            .take(limit)
            .map { it.toMatch(90) }
    }

    private fun phonetic(all: List<AuraContactEntity>, q: String, limit: Int): List<ContactMatch> {
        val key = metaphoneKey(q)
        if (key.isBlank()) return emptyList()
        return all.filter { it.phoneticKey == key || it.phoneticKeyAlt == key }
            .take(limit)
            .map { it.toMatch(75) }
    }

    private fun fuzzy(all: List<AuraContactEntity>, q: String, limit: Int): List<ContactMatch> {
        val target = normalise(q)
        return all.mapNotNull { contact ->
            val name = normalise(contact.displayName)
            val score = when {
                name.contains(target) || target.contains(name) -> 60
                levenshtein(name, target) <= 2 -> 50
                else -> 0
            }
            if (score > 0) contact.toMatch(score) else null
        }.sortedByDescending { it.score }.take(limit)
    }

    fun metaphoneKey(name: String): String {
        val first = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        return runCatching { metaphone.doubleMetaphone(first).orEmpty() }.getOrDefault("")
    }

    private fun normalise(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun AuraContactEntity.toMatch(score: Int) = ContactMatch(
        contactId = contactId,
        name = displayName,
        numbers = phoneNumbers,
        emails = emails,
        score = score,
    )

    private class MutableContact(val id: String, val name: String) {
        val numbers = linkedSetOf<String>()
        val emails = linkedSetOf<String>()
    }
}
