package com.aminfathi.nava.domain

import kotlin.test.*
import kotlinx.serialization.json.*

class TourismTest {
    private fun hospital(id: String, city: String, services: List<String>, languages: List<String>, min: Double?, currency: String = "USD", access: String = "") =
        Hospital(id = id, name = id, country = "Demonstration Country", city = city, services = services, languages = languages, internationalDesk = true, currency = currency, indicativeMin = min, indicativeMax = min?.plus(1000), indicativeNote = "demo", accessibilityNote = access, demonstration = true)
    private fun quote(hospital: String, lines: List<QuoteLine>, total: Double) =
        CareQuote(requestId = "r", hospitalId = hospital, treatment = "IVF demonstration", lines = lines, exclusions = listOf("Medication"), currency = "USD", total = total, expiresOn = "2026-12-01", demonstration = true)

    @Test fun legacyPatientWithoutJourneyStillOpens() {
        val encoded = Json { encodeDefaults = true }.encodeToString(Patient.serializer(), Patient(id = "p", nickname = "A"))
        val objectJson = Json.parseToJsonElement(encoded).jsonObject.toMutableMap()
        listOf("journey", "findings", "careSummary", "savedHospitals", "savedDoctors", "careRequests", "trip", "notices", "conciergeLog").forEach { objectJson.remove(it) }
        val decoded = Json.decodeFromString(Patient.serializer(), JsonObject(objectJson).toString())
        assertEquals("A", decoded.nickname)
        assertEquals("", decoded.journey.residenceCountry)
        assertEquals(1, decoded.schemaVersion)
    }

    @Test fun documentGateDropsValuesMissingFromThePage() {
        val ocr = "AMH 1.2 ng/mL collected 2024-03-01"
        val kept = DocumentGate.accept(ocr, "file", listOf(
            FindingDraft("tests.amh", "1.2", "ng/mL", "AMH 1.2 ng/mL", "en", "high"),
            FindingDraft("tests.fsh", "8", "IU/L", "FSH 8 IU/L", "en", "high")
        ))
        assertEquals(1, kept.size)
        assertEquals("1.2", kept.first().value)
        assertEquals(FindingCheck.UNVERIFIED, kept.first().verification)
    }

    @Test fun conflictingDocumentIsMarkedAgainstTheInterview() {
        val patient = Patient(nickname = "A", facts = listOf(Fact(fieldId = "tests.amh", value = "2.0", sourceMessageId = "m", language = "en")))
        val marked = DocumentGate.conflicts(listOf(ClinicalFinding(attachmentId = "file", label = "tests.amh", value = "1.2", evidence = "AMH 1.2")), patient.currentFacts())
        assertEquals("tests.amh", marked.first().conflictsWith)
    }

    @Test fun budgetAndCityFilterAreDeterministic() {
        val rows = listOf(
            hospital("north", "Harbor City", listOf("IVF"), listOf("fa", "en"), 4000.0, access = "step free"),
            hospital("dear", "Harbor City", listOf("IVF"), listOf("en"), 20000.0),
            hospital("away", "Otherport", listOf("IVF"), listOf("fa"), 3000.0),
            hospital("iui", "Harbor City", listOf("IUI"), listOf("fa"), 1000.0)
        )
        val matched = ProviderDiscovery.hospitals(rows, Pathway.IVF, "Harbor City", "fa", "8000", "USD", true)
        assertEquals(listOf("north"), matched.map { it.hospital.id })
        assertTrue(matched.first().reasons.containsAll(listOf("service", "city", "language", "budget", "accessibility")))
    }

    @Test fun repeatIvfMatchesListedPracticeWithoutRankingOutcomes() {
        val rows = listOf(
            Clinician(id = "a", hospitalId = "h", name = "A", languages = listOf("ar"), practiceAreas = listOf("repeat IVF"), demonstration = true),
            Clinician(id = "b", hospitalId = "h", name = "B", languages = listOf("en"), practiceAreas = listOf("IUI"), demonstration = true)
        )
        val matched = ProviderDiscovery.clinicians(rows, "h", "ar", true)
        assertEquals("practice_listed", matched.first { it.clinician.id == "a" }.reasons.last())
        assertFalse(matched.first().reasons.any { it.contains("better") || it.contains("superior") })
    }

    @Test fun patientCannotIssueAQuotation() {
        assertFails { RequestFlow.next(RequestStatus.UNDER_REVIEW, "ISSUE_QUOTE", "PATIENT") }
        assertEquals(RequestStatus.QUOTATION_RECEIVED, RequestFlow.next(RequestStatus.UNDER_REVIEW, "ISSUE_QUOTE", "COORDINATOR"))
        assertFails { RequestFlow.next(RequestStatus.UNDER_REVIEW, "ISSUE_QUOTE", "DOCTOR") }
    }

    @Test fun quoteMustMatchItsIncludedLines() {
        val sound = quote("h", listOf(QuoteLine("Stimulation", 1000.0), QuoteLine("Optional freezing", 200.0, included = false)), 1000.0)
        val drifted = sound.copy(total = 1200.0)
        assertTrue(sound.consistent())
        assertFalse(drifted.consistent())
        assertTrue(QuotationAnalysis.inclusionsDiffer(listOf(sound, quote("h2", listOf(QuoteLine("Consultation only", 100.0)), 100.0))))
    }

    @Test fun shareRequiresExplicitAuthorization() {
        assertFalse(SharePolicy.submissionAllowed(false, "h-northlight"))
        assertTrue(SharePolicy.submissionAllowed(true, "h-northlight"))
    }

    @Test fun conciergeUsesDirectoryRatherThanInventingOne() {
        val hospital = hospital("h-northlight", "Harbor City", listOf("IVF"), listOf("fa"), 4000.0).copy(name = "Northlight Demonstration Fertility Centre")
        val clinician = Clinician(id = "d", hospitalId = "h-northlight", name = "Dr Amira Nour (demonstration)", languages = listOf("ar"), demonstration = true)
        val stay = Stay(id = "s", name = "Quay Demonstration Apartment", hospitalId = "h-northlight", listedDistanceKm = 0.4, distanceSource = "administrator")
        val brief = TourismOrchestrator.route("Which doctors speak Arabic?", listOf(hospital), listOf(clinician), listOf(stay), emptyList(), Pathway.IVF, "Harbor City", "fa", "h-northlight", true)
        assertEquals("doctors", brief.code)
        assertTrue(brief.entries.single().contains("Amira"))
        val closest = TourismOrchestrator.route("Which accommodation is closest to my selected hospital?", listOf(hospital), emptyList(), listOf(stay), emptyList(), Pathway.IVF, "Harbor City", "fa", "h-northlight", false)
        assertEquals("closest_stay", closest.code)
    }

    @Test fun journeyMarkersFollowRealEvents() {
        val empty = JourneyMap.markers(Patient(nickname = "A"))
        assertEquals(MarkerState.CURRENT, empty.first { it.marker == JourneyMarker.PROFILE }.state)
        val moved = emptyPatient().copy(journey = JourneyProfile(residenceCountry = "Iran", destinationCountry = "Demonstration Country"), savedHospitals = listOf("h"))
        val markers = JourneyMap.markers(moved)
        assertEquals(MarkerState.DONE, markers.first { it.marker == JourneyMarker.PROFILE }.state)
        assertEquals(MarkerState.CURRENT, markers.first { it.marker == JourneyMarker.HISTORY }.state)
        assertEquals(MarkerState.DONE, markers.first { it.marker == JourneyMarker.HOSPITALS }.state)
    }

    @Test fun currencyConversionIsLabeledByTheCaller() {
        val table = FxTable(perUsd = mapOf("EUR" to 0.92), source = "demonstration", asOf = "2026-11-18")
        val euros = Money.convert(100.0, "USD", "EUR", table)
        assertEquals(92.0, euros)
        val rows = Money.budget(quote("h", listOf(QuoteLine("IVF", 1000.0)), 1000.0), 90.0, 2, "USD", null, null, "USD", "EUR", table)
        assertEquals("provider_quote", rows.first().origin)
        assertTrue(rows.first().approximate)
        assertEquals("patient_estimate", rows[1].origin)
    }

    private fun emptyPatient() = Patient(nickname = "A")
}
