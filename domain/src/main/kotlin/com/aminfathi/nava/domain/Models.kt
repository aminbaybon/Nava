package com.aminfathi.nava.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

fun id(): String = UUID.randomUUID().toString()
fun now(): String = Instant.now().toString()
@Serializable enum class Pathway { IVF, IUI }
@Serializable enum class Status { NOT_ASKED, ANSWERED, ANSWERED_NEGATIVE, UNKNOWN, SKIPPED, NEEDS_CLARIFICATION, NOT_APPLICABLE, CONFLICTING }
@Serializable enum class Source { PATIENT_REPORT, REPORTED_CLINICIAN_DIAGNOSIS, PATIENT_SUPPLIED_TEST, SECONDHAND }
@Serializable enum class Kind { TEXT, INTEGER, DECIMAL, DATE, BOOLEAN }
@Serializable enum class MessageState { PENDING, COMMITTED, FAILED }
@Serializable data class Fact(
    val id: String = id(), val fieldId: String, val value: String = "", val unit: String = "",
    val date: String = "", val status: Status = Status.ANSWERED, val source: Source = Source.PATIENT_REPORT,
    val sourceMessageId: String, val evidence: String = "", val language: String,
    val updatedAt: String = now(), val reviewed: Boolean = false, val correction: Boolean = false,
    val supersedes: String? = null, val sessionId: String = ""
)
@Serializable data class Message(val id: String = id(), val role: String, val text: String, val createdAt: String = now(), val state: MessageState = MessageState.COMMITTED)
@Serializable data class Consent(val version: String = "2026-11-18.1", val acceptedAt: String = now())
@Serializable data class Session(val id: String = id(), val createdAt: String = now(), val messages: List<Message> = emptyList(), val currentField: String? = null, val asked: List<String> = emptyList(), val finished: Boolean = false)
@Serializable data class ReportSnapshot(val id: String = id(), val createdAt: String = now(), val language: String, val text: String, val structuredJson: String, val pathway: String = "")
@Serializable enum class AttachmentKind { DOCUMENT, LAB }
@Serializable data class Attachment(
    val id: String = id(), val kind: AttachmentKind, val name: String, val mime: String,
    val createdAt: String = now(), val ocr: String = "", val description: String = "",
    val pageCount: Int = 0, val pagesRead: Int = 0
)
@Serializable data class Patient(
    val id: String = id(), val nickname: String, val pathway: Pathway = Pathway.IVF,
    val stage: String = "preparing", val consent: Consent? = null, val facts: List<Fact> = emptyList(),
    val sessions: List<Session> = listOf(Session()), val reports: List<ReportSnapshot> = emptyList(),
    val attachments: List<Attachment> = emptyList(), val packets: List<ReportSnapshot> = emptyList(),
    val schemaVersion: Int = 1, val revision: Long = 0
) {
    val session: Session get() = sessions.last()
    fun withSession(s: Session) = copy(sessions = sessions.dropLast(1) + s)
    fun currentFacts(): Map<String, Fact> = facts.associateBy { it.fieldId }
}
@Serializable data class Proposal(val fieldId: String, val value: String, val unit: String, val date: String, val status: Status, val source: Source, val evidence: String, val correction: Boolean)
@Serializable data class Extraction(val facts: List<Proposal>, val urgent: Boolean, val adviceRequested: Boolean)
@Serializable data class Field(
    val id: String, val domain: String, val intent: String, val kind: Kind = Kind.TEXT,
    val required: Boolean = true, val sensitive: Boolean = false, val pathway: Pathway? = null,
    val dependsOn: String? = null, val expected: String? = null, val units: List<String> = emptyList(),
    val min: Double? = null, val max: Double? = null, val sources: List<String> = listOf("ASRM2026", "WHO2025"),
    val followUp: String = "Clarify ambiguity, date and units only if known; allow unknown or skip."
)
