package com.aminfathi.nava.reports

import android.content.Context
import com.aminfathi.nava.domain.*
import com.aminfathi.nava.ui.label
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PacketComposer {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    fun create(context: Context, patient: Patient, language: String): ReportSnapshot {
        val at = now()
        val texts = patient.attachments.map { it.ocr }.filter { it.isNotBlank() }
        val slots = PacketTemplate.fill(patient.pathway, texts)
        val text = buildString {
            appendLine("NAVA  /  " + context.label("packet_title") + "  /  " + patient.pathway.name)
            appendLine(patient.nickname + " • " + patient.pathway.name)
            appendLine(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag(language)).withZone(ZoneId.systemDefault()).format(Instant.parse(at)))
            appendLine(context.label("packet_disclaimer"))
            appendSection(context, "packet_documents", patient.attachments.filter { it.kind == AttachmentKind.DOCUMENT })
            appendSection(context, "packet_labs", patient.attachments.filter { it.kind == AttachmentKind.LAB })
            appendLine("\n" + context.label("packet_slots"))
            slots.forEach { (id, quotes) ->
                appendLine(context.label(id) + ":")
                if (quotes.isEmpty()) appendLine(context.label("packet_not_read"))
                else quotes.forEach { appendLine("• " + it) }
            }
        }
        val structured = buildJsonObject {
            put("kind", "file_packet")
            put("generated_at", at)
            put("language", language)
            put("patient_id", patient.id)
            put("nickname", patient.nickname)
            put("pathway", patient.pathway.name)
            put("clinical_review_required", true)
            putJsonArray("attachments") {
                patient.attachments.forEach { item ->
                    add(buildJsonObject {
                        put("id", item.id); put("kind", item.kind.name); put("name", item.name)
                        put("description", item.description); put("ocr", item.ocr)
                        put("page_count", item.pageCount); put("pages_read", item.pagesRead)
                    })
                }
            }
            put("slots", json.encodeToJsonElement(slots.map { mapOf("id" to it.first, "quotes" to it.second.joinToString("\n")) }))
        }
        return ReportSnapshot(createdAt = at, language = language, text = text, structuredJson = json.encodeToString(structured), pathway = patient.pathway.name)
    }
    private fun StringBuilder.appendSection(context: Context, title: String, items: List<Attachment>) {
        appendLine("\n" + context.label(title))
        if (items.isEmpty()) appendLine(context.label("not_provided"))
        items.forEach { item ->
            appendLine("— " + item.name)
            appendLine(item.description.ifBlank { context.label("not_provided") })
            appendLine(item.ocr.ifBlank { context.label("not_provided") })
            if (item.pageCount > item.pagesRead && item.pagesRead > 0) appendLine(context.label("packet_partial"))
        }
    }
}
