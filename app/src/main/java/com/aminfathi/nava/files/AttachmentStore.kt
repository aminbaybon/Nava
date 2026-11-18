package com.aminfathi.nava.files

import android.content.Context
import com.aminfathi.nava.security.Vault
import java.io.File

class AttachmentStore(private val context: Context, private val vault: Vault) {
    private fun safe(id: String) = id.replace(Regex("[^a-zA-Z0-9-]"), "")
    private fun file(patientId: String, attachmentId: String) = File(context.filesDir, "attachments/${safe(patientId)}/${safe(attachmentId)}")
    private fun aad(patientId: String, attachmentId: String) = "attachment:${safe(patientId)}:${safe(attachmentId)}"
    fun save(patientId: String, attachmentId: String, bytes: ByteArray) = vault.sealTo(file(patientId, attachmentId), aad(patientId, attachmentId), bytes)
    fun load(patientId: String, attachmentId: String): ByteArray = vault.openFrom(file(patientId, attachmentId), aad(patientId, attachmentId))
    fun delete(patientId: String, attachmentId: String) { file(patientId, attachmentId).delete() }
    fun deletePatient(patientId: String) { File(context.filesDir, "attachments/${safe(patientId)}").deleteRecursively() }
    fun deleteAll() { File(context.filesDir, "attachments").deleteRecursively() }
}
