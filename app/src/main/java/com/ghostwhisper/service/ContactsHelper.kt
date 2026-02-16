package com.ghostwhisper.service

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

/**
 * Helper to cross-reference scanned WhatsApp member names with device contacts to retrieve verified
 * phone numbers.
 */
class ContactsHelper(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "ContactsHelper"
    }

    /** A matched contact with confidence score. */
    data class MatchedContact(
            val displayName: String,
            val phoneNumber: String,
            val confidence: Float // 0.0 to 1.0
    )

    /**
     * Cross-reference a list of scanned names with device contacts.
     *
     * @param scannedNames Raw names from WhatsApp group info
     * @return List of matched contacts with phone numbers
     */
    fun matchContacts(scannedNames: List<String>): List<MatchedContact> {
        val matches = mutableListOf<MatchedContact>()

        for (name in scannedNames) {
            val contact = findContactByName(name)
            if (contact != null) {
                matches.add(contact)
            }
        }

        Log.d(TAG, "Matched ${matches.size}/${scannedNames.size} contacts")
        return matches
    }

    /**
     * Find a contact by display name and return the first phone number. Uses fuzzy matching —
     * normalized lowercase comparison.
     */
    private fun findContactByName(name: String): MatchedContact? {
        val normalizedSearch = name.trim().lowercase()

        // Search contacts by display name
        val cursor: Cursor? =
                contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(
                                ContactsContract.Contacts._ID,
                                ContactsContract.Contacts.DISPLAY_NAME,
                                ContactsContract.Contacts.HAS_PHONE_NUMBER
                        ),
                        "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
                        arrayOf("%${normalizedSearch}%"),
                        null
                )

        cursor?.use {
            while (it.moveToNext()) {
                val contactId =
                        it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val displayName =
                        it.getString(
                                it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                        )
                                ?: continue
                val hasPhone =
                        it.getInt(
                                it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        )

                if (hasPhone > 0) {
                    val phone = getPhoneNumber(contactId)
                    if (phone != null) {
                        // Calculate match confidence
                        val confidence =
                                calculateConfidence(normalizedSearch, displayName.lowercase())
                        return MatchedContact(displayName, normalizePhone(phone), confidence)
                    }
                }
            }
        }

        return null
    }

    /** Get the first phone number for a contact ID. */
    private fun getPhoneNumber(contactId: String): String? {
        val phoneCursor: Cursor? =
                contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                )

        phoneCursor?.use {
            if (it.moveToFirst()) {
                return it.getString(
                        it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                )
            }
        }

        return null
    }

    /** Normalize phone number to international format. */
    private fun normalizePhone(phone: String): String {
        val digits = phone.replace(Regex("[^+\\d]"), "")
        return if (digits.startsWith("+")) digits
        else if (digits.startsWith("0")) "+91${digits.substring(1)}" else "+91$digits"
    }

    /** Calculate confidence score between search name and contact name. */
    private fun calculateConfidence(search: String, contactName: String): Float {
        return when {
            search == contactName -> 1.0f
            contactName.startsWith(search) -> 0.9f
            contactName.contains(search) -> 0.7f
            else -> 0.5f
        }
    }

    /** Retrieve contact details from a Contact URI (returned by PickContact intent). */
    fun getContactFromUri(contactUri: android.net.Uri): MatchedContact? {
        val cursor = contentResolver.query(contactUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                if (idIndex != -1 && nameIndex != -1 && hasPhoneIndex != -1) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex)
                    val hasPhone = it.getInt(hasPhoneIndex) > 0

                    if (hasPhone) {
                        val phoneNumber = getPhoneNumber(id)
                        if (phoneNumber != null) {
                            return MatchedContact(
                                    displayName = name ?: "Unknown",
                                    phoneNumber = normalizePhone(phoneNumber),
                                    confidence = 1.0f
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    // Public method to normalize phone numbers for external use
    fun normalizePhoneNumber(phone: String): String {
        return normalizePhone(phone)
    }
}
