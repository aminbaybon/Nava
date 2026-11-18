package com.aminfathi.nava.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Medical-tourism types and deterministic rules.
 * Listing text, prices and credentials come from records supplied to these functions.
 * Nothing here invents a hospital, a clinician, a price or a clinical recommendation.
 */

@Serializable enum class CompanionPlan { ALONE, PARTNER, FAMILY }
@Serializable enum class StayType { HOTEL, SERVICED_APARTMENT, FURNISHED_RENTAL, PATIENT_ORIENTED, FAMILY_FRIENDLY }
@Serializable enum class RequestStatus { DRAFT, SUBMITTED, UNDER_REVIEW, MORE_INFORMATION_REQUIRED, QUOTATION_RECEIVED, ACCEPTED, DECLINED, CANCELLED, EXPIRED }
@Serializable enum class CareActor { AI_ASSISTANT, HOSPITAL_COORDINATOR, PHYSICIAN, SYSTEM, PATIENT }
@Serializable enum class FindingCheck { UNVERIFIED, CONFIRMED, REJECTED }
@Serializable enum class JourneyMarker { PROFILE, HISTORY, DOCUMENTS, HOSPITALS, DOCTOR, REQUEST, QUOTATION, PLAN, TRAVEL }
@Serializable enum class MarkerState { DONE, CURRENT, UPCOMING }

@Serializable data class JourneyProfile(
    val residenceCountry: String = "", val nationality: String = "",
    val destinationCountry: String = "", val destinationCity: String = "",
    val travelWindow: String = "", val budgetMin: String = "", val budgetMax: String = "",
    val budgetCurrency: String = "USD", val companions: CompanionPlan = CompanionPlan.ALONE,
    val companionCount: Int = 0, val accessibility: String = "", val updatedAt: String = ""
)

@Serializable data class ClinicalFinding(
    val id: String = id(), val attachmentId: String, val label: String, val value: String,
    val unit: String = "", val evidence: String, val language: String = "",
    val confidence: String = "unscored", val verification: FindingCheck = FindingCheck.UNVERIFIED,
    val extractedAt: String = now(), val conflictsWith: String = ""
)

@Serializable data class SummaryLine(val fieldId: String, val value: String)
@Serializable data class CareSummary(
    val concern: String = "", val history: List<SummaryLine> = emptyList(),
    val treatments: List<SummaryLine> = emptyList(), val results: List<SummaryLine> = emptyList(),
    val conditions: List<SummaryLine> = emptyList(), val missing: List<String> = emptyList(),
    val questions: List<String> = emptyList(), val englishNote: String = "",
    val englishIsMachine: Boolean = false, val assembledAt: String = now()
)

@Serializable data class Hospital(
    val id: String, val name: String, val country: String, val city: String, val address: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val website: String = "",
    val services: List<String> = emptyList(), val departments: List<String> = emptyList(),
    val languages: List<String> = emptyList(), val internationalDesk: Boolean = false,
    val accreditation: String = "", val facilities: String = "", val accessibilityNote: String = "",
    val currency: String = "", val indicativeMin: Double? = null, val indicativeMax: Double? = null,
    val indicativeNote: String = "", val demonstration: Boolean = true, val photoUrls: List<String> = emptyList()
)

@Serializable data class Clinician(
    val id: String, val hospitalId: String, val name: String, val title: String = "",
    val specialty: String = "", val qualifications: String = "", val experienceNote: String = "",
    val languages: List<String> = emptyList(), val biography: String = "",
    val practiceAreas: List<String> = emptyList(), val consultOptions: String = "",
    val consultPrice: Double? = null, val consultCurrency: String = "",
    val demonstration: Boolean = true, val photoUrl: String = ""
)

@Serializable data class Stay(
    val id: String, val name: String, val address: String = "", val city: String = "", val country: String = "",
    val type: StayType = StayType.HOTEL, val nightly: Double? = null, val currency: String = "",
    val hospitalId: String = "", val listedDistanceKm: Double? = null, val listedTravelMinutes: Int? = null,
    val distanceSource: String = "", val amenities: List<String> = emptyList(), val accessibility: String = "",
    val kitchen: Boolean = false, val rooms: Int = 1, val guests: Int = 1, val suggestedNights: Int = 14,
    val transport: String = "", val bookingUrl: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val demonstration: Boolean = true,
    val photoUrls: List<String> = emptyList()
)

@Serializable data class FxTable(
    val base: String = "USD", val perUsd: Map<String, Double> = emptyMap(),
    val source: String = "", val asOf: String = ""
)

@Serializable data class QuoteLine(val description: String, val amount: Double, val included: Boolean = true)
@Serializable data class CareQuote(
    val id: String = id(), val requestId: String, val hospitalId: String, val doctorId: String = "",
    val treatment: String, val lines: List<QuoteLine> = emptyList(), val exclusions: List<String> = emptyList(),
    val currency: String, val total: Double, val expiresOn: String, val paymentTerms: String = "",
    val extraNeeded: String = "", val qualifications: String = "", val demonstration: Boolean = true,
    val issuedAt: String = now()
) {
    fun consistent(): Boolean = currency.isNotBlank() && treatment.isNotBlank() && expiresOn.isNotBlank() &&
        abs(lines.filter { it.included }.sumOf { it.amount } - total) < 0.02 && lines.all { it.description.isNotBlank() }
}

@Serializable data class CareMessage(
    val id: String = id(), val requestId: String, val actor: CareActor, val sender: String,
    val body: String, val createdAt: String = now()
)

@Serializable data class CareRequest(
    val id: String = id(), val pathway: Pathway = Pathway.IVF, val hospitalId: String = "",
    val doctorId: String = "", val note: String = "", val documentIds: List<String> = emptyList(),
    val includeSummary: Boolean = false, val authorized: Boolean = false,
    val status: RequestStatus = RequestStatus.DRAFT, val createdAt: String = now(), val updatedAt: String = now(),
    val quotations: List<CareQuote> = emptyList(), val messages: List<CareMessage> = emptyList()
)

@Serializable data class TripPlan(
    val arrivalDate: String = "", val transfer: String = "", val nights: Int = 0,
    val transportEstimate: String = "", val otherExpense: String = "", val otherCurrency: String = "USD",
    val checklist: Map<String, Boolean> = emptyMap(), val emergencyContact: String = "", val notes: String = ""
)

@Serializable data class CareNotice(val id: String = id(), val code: String, val createdAt: String = now(), val read: Boolean = false)

@Serializable data class HospitalMatch(val hospital: Hospital, val reasons: List<String>)
@Serializable data class ClinicianMatch(val clinician: Clinician, val reasons: List<String>)
@Serializable data class ConciergeBrief(val code: String, val entries: List<String> = emptyList())
@Serializable data class BudgetRow(
    val code: String, val amount: Double?, val currency: String, val origin: String,
    val displayAmount: Double?, val displayCurrency: String, val approximate: Boolean
)
@Serializable data class MarkerStatus(val marker: JourneyMarker, val state: MarkerState)

data class FindingDraft(val label: String, val value: String, val unit: String, val evidence: String, val language: String, val confidence: String)

/** Drops any extracted value whose evidence is not present in the source text. */
object DocumentGate {
    fun accept(ocr: String, attachmentId: String, drafts: List<FindingDraft>, language: String = ""): List<ClinicalFinding> {
        val source = ocr.replace(Regex("\\s+"), " ")
        return drafts.mapNotNull { draft ->
            val evidence = draft.evidence.trim().replace(Regex("\\s+"), " ")
            val value = draft.value.trim()
            if (value.isEmpty() || evidence.isEmpty() || !source.contains(evidence)) null
            else ClinicalFinding(
                attachmentId = attachmentId, label = draft.label.trim().take(80), value = value.take(500),
                unit = draft.unit.trim().take(40), evidence = evidence.take(500),
                language = draft.language.ifBlank { language }.take(12),
                confidence = draft.confidence.ifBlank { "unscored" }.take(16)
            )
        }
    }
    fun conflicts(findings: List<ClinicalFinding>, facts: Map<String, Fact>): List<ClinicalFinding> = findings.map { finding ->
        val fact = facts[finding.label]
        if (fact != null && fact.value.isNotBlank() && !fact.value.equals(finding.value, ignoreCase = true)) finding.copy(conflictsWith = fact.fieldId)
        else finding.copy(conflictsWith = "")
    }
}

object SummaryAssembler {
    private val historyIds = listOf("fertility.duration", "fertility.prior_attempts", "fertility.diagnoses", "fertility.ivf_count", "fertility.iui_count")
    private val treatmentIds = listOf("fertility.other_treatment", "ivf.plan", "iui.plan")
    private val conditionIds = listOf("medical.chronic", "medical.surgery", "medical.cancer", "medication.current", "medication.allergies")
    fun build(patient: Patient): CareSummary {
        val facts = patient.currentFacts()
        fun lines(ids: List<String>) = ids.mapNotNull { id -> facts[id]?.takeIf { it.value.isNotBlank() && it.status != Status.CONFLICTING }?.let { SummaryLine(id, it.value) } }
        val confirmed = patient.findings.filter { it.verification == FindingCheck.CONFIRMED }
        val results = confirmed.map { SummaryLine(it.label.ifBlank { "document" }, listOf(it.value, it.unit).filter { part -> part.isNotBlank() }.joinToString(" ")) }
        val missing = buildList {
            if (facts["background.concern"]?.value.isNullOrBlank()) add("missing_concern")
            if (confirmed.isEmpty() && patient.findings.any { it.verification == FindingCheck.UNVERIFIED }) add("unconfirmed_findings")
            if (patient.findings.any { it.conflictsWith.isNotBlank() && it.verification != FindingCheck.REJECTED }) add("conflicting_findings")
            if (patient.attachments.isEmpty()) add("no_documents")
        }
        val questions = buildList {
            add("clinician_must_review")
            if (missing.contains("conflicting_findings")) add("review_conflicts")
            if (patient.findings.any { it.verification == FindingCheck.UNVERIFIED }) add("confirm_unverified")
        }
        return CareSummary(
            concern = facts["background.concern"]?.value.orEmpty(),
            history = lines(historyIds), treatments = lines(treatmentIds), results = results,
            conditions = lines(conditionIds), missing = missing, questions = questions,
            englishNote = patient.careSummary?.englishNote.orEmpty(),
            englishIsMachine = patient.careSummary?.englishIsMachine == true
        )
    }
}

object ProviderDiscovery {
    fun hospitals(hospitals: List<Hospital>, pathway: Pathway, city: String, language: String, budgetMax: String, budgetCurrency: String, needsAccess: Boolean): List<HospitalMatch> {
        val cap = budgetMax.toDoubleOrNull()
        return hospitals.mapNotNull { hospital ->
            if (pathway.name !in hospital.services) return@mapNotNull null
            if (city.isNotBlank() && !hospital.city.equals(city.trim(), ignoreCase = true)) return@mapNotNull null
            if (cap != null && hospital.currency.equals(budgetCurrency, ignoreCase = true) && hospital.indicativeMin != null && hospital.indicativeMin > cap) return@mapNotNull null
            val reasons = buildList {
                add("service")
                if (city.isNotBlank()) add("city")
                if (language.isNotBlank() && hospital.languages.any { it.equals(language, true) || it.startsWith(language, true) }) add("language")
                if (hospital.internationalDesk) add("international")
                if (cap != null && hospital.indicativeMax != null && hospital.currency.equals(budgetCurrency, true) && hospital.indicativeMax <= cap) add("budget")
                if (needsAccess && hospital.accessibilityNote.isNotBlank()) add("accessibility")
            }
            HospitalMatch(hospital, reasons)
        }
    }
    fun clinicians(clinicians: List<Clinician>, hospitalId: String, language: String, priorIvf: Boolean): List<ClinicianMatch> =
        clinicians.filter { hospitalId.isBlank() || it.hospitalId == hospitalId }.map { clinician ->
            val reasons = buildList {
                if (hospitalId.isNotBlank() && clinician.hospitalId == hospitalId) add("hospital")
                if (language.isNotBlank() && clinician.languages.any { it.equals(language, true) || it.contains(language, true) }) add("language")
                if (priorIvf && clinician.practiceAreas.any { area -> area.contains("repeat", true) || area.contains("complex", true) }) add("practice_listed")
            }
            ClinicianMatch(clinician, reasons)
        }.sortedByDescending { it.reasons.size }
    fun stays(stays: List<Stay>, hospitalId: String, guests: Int, kitchen: Boolean?): List<Stay> =
        stays.filter { stay ->
            (hospitalId.isBlank() || stay.hospitalId == hospitalId) && stay.guests >= guests && (kitchen != true || stay.kitchen)
        }.sortedBy { it.listedDistanceKm ?: Double.MAX_VALUE }
}

/** Role-checked request transitions. The model is not allowed to move these states. */
object RequestFlow {
    private val staff = setOf("COORDINATOR", "HOSPITAL_ADMIN", "PLATFORM_ADMIN")
    fun next(status: RequestStatus, action: String, role: String): RequestStatus {
        val staffRole = role in staff
        val result = when {
            role == "PATIENT" && status == RequestStatus.DRAFT && action == "SUBMIT" -> RequestStatus.SUBMITTED
            role == "PATIENT" && status == RequestStatus.MORE_INFORMATION_REQUIRED && action == "SUBMIT" -> RequestStatus.SUBMITTED
            role == "PATIENT" && status == RequestStatus.QUOTATION_RECEIVED && action == "ACCEPT" -> RequestStatus.ACCEPTED
            role == "PATIENT" && status == RequestStatus.QUOTATION_RECEIVED && action == "DECLINE" -> RequestStatus.DECLINED
            role == "PATIENT" && action == "CANCEL" && status in setOf(RequestStatus.DRAFT, RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW, RequestStatus.MORE_INFORMATION_REQUIRED, RequestStatus.QUOTATION_RECEIVED) -> RequestStatus.CANCELLED
            staffRole && status == RequestStatus.SUBMITTED && action == "START_REVIEW" -> RequestStatus.UNDER_REVIEW
            staffRole && status in setOf(RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW) && action == "NEED_INFO" -> RequestStatus.MORE_INFORMATION_REQUIRED
            staffRole && status in setOf(RequestStatus.UNDER_REVIEW, RequestStatus.MORE_INFORMATION_REQUIRED) && action == "ISSUE_QUOTE" -> RequestStatus.QUOTATION_RECEIVED
            staffRole && action == "CANCEL" && status !in setOf(RequestStatus.ACCEPTED, RequestStatus.DECLINED, RequestStatus.CANCELLED, RequestStatus.EXPIRED) -> RequestStatus.CANCELLED
            role == "SYSTEM" && status == RequestStatus.QUOTATION_RECEIVED && action == "EXPIRE" -> RequestStatus.EXPIRED
            else -> null
        }
        return result ?: error("Transition $action from $status is not allowed for $role")
    }
    fun merge(local: List<CareRequest>, remote: List<CareRequest>): List<CareRequest> {
        val remoteIds = remote.map { it.id }.toSet()
        val drafts = local.filter { it.status == RequestStatus.DRAFT && it.id !in remoteIds }
        return remote + drafts
    }
    fun noticeCodes(previous: List<CareRequest>, remote: List<CareRequest>): List<String> = buildList {
        remote.forEach { item ->
            val before = previous.find { it.id == item.id }
            if (before == null || before.status != item.status) {
                add(when (item.status) {
                    RequestStatus.SUBMITTED -> "request_submitted"
                    RequestStatus.MORE_INFORMATION_REQUIRED -> "documents_needed"
                    RequestStatus.QUOTATION_RECEIVED -> "quotation_received"
                    RequestStatus.UNDER_REVIEW -> "provider_response"
                    else -> "provider_response"
                })
            }
            if (item.messages.size > (before?.messages?.size ?: 0)) add("new_message")
            if (item.status == RequestStatus.QUOTATION_RECEIVED && before?.status != RequestStatus.QUOTATION_RECEIVED && item.quotations.any { it.expiresOn.isNotBlank() }) add("quotation_expiring")
        }
    }
}

object QuotationAnalysis {
    fun inclusionsDiffer(quotes: List<CareQuote>): Boolean {
        val sets = quotes.map { quote -> quote.lines.filter { it.included }.map { it.description.trim().lowercase() }.toSet() }
        return sets.distinct().size > 1
    }
    fun explain(requests: List<CareRequest>): ConciergeBrief {
        val quotes = requests.flatMap { it.quotations }
        if (quotes.size < 2) return ConciergeBrief("need_two_quotes")
        val lines = quotes.map { quote ->
            "${quote.hospitalId} · ${quote.total} ${quote.currency} · ${quote.lines.count { it.included }} · ${quote.exclusions.size} · ${quote.expiresOn}"
        }
        return ConciergeBrief(if (inclusionsDiffer(quotes)) "compare_quotes_differ" else "compare_quotes", lines)
    }
    fun exclusions(requests: List<CareRequest>): ConciergeBrief {
        val quote = requests.asReversed().flatMap { it.quotations }.lastOrNull() ?: return ConciergeBrief("no_quote")
        if (quote.exclusions.isEmpty()) return ConciergeBrief("exclusions_none")
        return ConciergeBrief("exclusions", quote.exclusions)
    }
}

object TravelConcierge {
    fun closest(stays: List<Stay>, hospitalId: String): ConciergeBrief {
        val stay = stays.filter { hospitalId.isBlank() || it.hospitalId == hospitalId }.filter { it.listedDistanceKm != null }.minByOrNull { it.listedDistanceKm!! }
            ?: return ConciergeBrief("no_distance")
        return ConciergeBrief("closest_stay", listOf(stay.name, stay.listedDistanceKm.toString(), stay.distanceSource))
    }
    fun specialistQuestions() = ConciergeBrief("specialist_questions")
}

object TreatmentRequestAgent {
    fun documentsNeeded(requests: List<CareRequest>): ConciergeBrief {
        val extra = requests.asReversed().firstNotNullOfOrNull { request ->
            request.quotations.firstOrNull { it.extraNeeded.isNotBlank() }?.extraNeeded
                ?: request.messages.lastOrNull { it.actor == CareActor.HOSPITAL_COORDINATOR }?.body?.takeIf { request.status == RequestStatus.MORE_INFORMATION_REQUIRED }
        }
        return if (extra.isNullOrBlank()) ConciergeBrief("documents_general") else ConciergeBrief("documents_needed", listOf(extra))
    }
    fun next(requests: List<CareRequest>): ConciergeBrief {
        val status = requests.maxByOrNull { it.updatedAt }?.status
        return ConciergeBrief("next_" + (status?.name?.lowercase() ?: "none"))
    }
}

/** One local decision per turn. Clinical interview remains in the existing orchestrator. */
object TourismOrchestrator {
    fun route(question: String, hospitals: List<Hospital>, clinicians: List<Clinician>, stays: List<Stay>, requests: List<CareRequest>, pathway: Pathway, city: String, language: String, hospitalId: String, priorIvf: Boolean): ConciergeBrief {
        val text = question.lowercase()
        fun has(vararg words: String) = words.any { text.contains(it) }
        return when {
            has("compare quote", "compare quotation", "compare the quote", "مقایسه پیش") || (has("compare", "مقایسه", "مقارنة") && has("quote", "quotation", "پیش‌فاکتور", "پیش فاکتور")) -> QuotationAnalysis.explain(requests)
            has("not included", "exclusion", "excluded", "شامل نیست", "غیر مشمول", "مستثن") -> QuotationAnalysis.exclusions(requests)
            has("what should i ask", "before traveling", "بپرسم", "قبل از سفر", "اسأل", "أسأل") -> TravelConcierge.specialistQuestions()
            has("document", "مدرک", "مدارک", "وثائق", "records need", "need to send") -> TreatmentRequestAgent.documentsNeeded(requests)
            has("closest", "accommodation", "hotel", "اقامت", "هتل", "نزدیک", "سکن", "فندق") -> TravelConcierge.closest(stays, hospitalId)
            has("doctor", "physician", "پزشک", "دکتر", "طبيب", "speak", "حرف") -> {
                val lang = when {
                    has("arabic", "عربی", "العربية") -> "ar"
                    has("persian", "farsi", "فارسی") -> "fa"
                    has("english", "انگلیسی") -> "en"
                    has("armenian", "ارمنی") -> "hy"
                    else -> language
                }
                val found = ProviderDiscovery.clinicians(clinicians, hospitalId, lang, priorIvf).filter { match -> match.reasons.contains("language") || lang.isBlank() }
                if (found.isEmpty()) ConciergeBrief("no_doctor_language")
                else ConciergeBrief("doctors", found.map { "${it.clinician.name} · ${it.clinician.languages.joinToString()}" })
            }
            has("hospital", "clinic", "بیمارستان", "کلینیک", "مستشفى", "ivf") -> {
                val found = ProviderDiscovery.hospitals(hospitals, pathway, city, language, "", "", false)
                if (found.isEmpty()) ConciergeBrief("no_hospital") else ConciergeBrief("hospitals", found.map { "${it.hospital.name} · ${it.hospital.city}" })
            }
            has("next", "بعد", "گام", "الخطوة") -> TreatmentRequestAgent.next(requests)
            else -> ConciergeBrief("unknown")
        }
    }
}

object JourneyMap {
    fun markers(patient: Patient): List<MarkerStatus> {
        val done = linkedSetOf<JourneyMarker>()
        val profile = patient.journey
        if (profile.residenceCountry.isNotBlank() && profile.destinationCountry.isNotBlank()) done += JourneyMarker.PROFILE
        if (patient.facts.isNotEmpty() || patient.session.messages.any { it.role == "user" }) done += JourneyMarker.HISTORY
        if (patient.findings.any { it.verification == FindingCheck.CONFIRMED } || patient.careSummary != null) done += JourneyMarker.DOCUMENTS
        if (patient.savedHospitals.isNotEmpty()) done += JourneyMarker.HOSPITALS
        if (patient.savedDoctors.isNotEmpty() || patient.careRequests.any { it.doctorId.isNotBlank() }) done += JourneyMarker.DOCTOR
        if (patient.careRequests.any { it.status != RequestStatus.DRAFT }) done += JourneyMarker.REQUEST
        if (patient.careRequests.any { it.quotations.isNotEmpty() }) done += JourneyMarker.QUOTATION
        if (patient.careRequests.any { it.status == RequestStatus.ACCEPTED }) done += JourneyMarker.PLAN
        if (patient.trip.arrivalDate.isNotBlank()) done += JourneyMarker.TRAVEL
        val current = JourneyMarker.entries.firstOrNull { it !in done }
        return JourneyMarker.entries.map { marker ->
            MarkerStatus(marker, when {
                marker in done -> MarkerState.DONE
                marker == current -> MarkerState.CURRENT
                else -> MarkerState.UPCOMING
            })
        }
    }
}

object Money {
    /** perUsd is how many units of that currency equal one US dollar. */
    fun convert(amount: Double, from: String, to: String, table: FxTable): Double? {
        if (from.equals(to, true)) return amount
        fun rate(code: String) = if (code.equals("USD", true) || code.equals(table.base, true)) 1.0 else table.perUsd.entries.firstOrNull { it.key.equals(code, true) }?.value
        val fromRate = rate(from) ?: return null
        val toRate = rate(to) ?: return null
        if (fromRate == 0.0) return null
        return amount / fromRate * toRate
    }
    fun budget(quote: CareQuote?, nightly: Double?, nights: Int, nightlyCurrency: String, transport: Double?, other: Double?, otherCurrency: String, display: String, rates: FxTable): List<BudgetRow> {
        fun row(code: String, amount: Double?, currency: String, origin: String): BudgetRow? {
            if (amount == null) return null
            val shown = convert(amount, currency, display, rates)
            return BudgetRow(code, amount, currency, origin, shown, display, approximate = origin != "provider_quote" || !currency.equals(display, true) || quote?.demonstration == true)
        }
        return listOfNotNull(
            quote?.let { row("quotation", it.total, it.currency, "provider_quote") },
            if (nightly != null && nights > 0) row("accommodation", nightly * nights, nightlyCurrency, "patient_estimate") else null,
            row("transport", transport, otherCurrency, "patient_estimate"),
            row("other", other, otherCurrency, "patient_estimate")
        )
    }
}

@Serializable data class CatalogFile(
    val hospitals: List<Hospital> = emptyList(), val clinicians: List<Clinician> = emptyList(),
    val stays: List<Stay> = emptyList(), val rates: FxTable = FxTable()
)

object SharePolicy {
    fun submissionAllowed(authorized: Boolean, hospitalId: String): Boolean = authorized && hospitalId.isNotBlank()
}
