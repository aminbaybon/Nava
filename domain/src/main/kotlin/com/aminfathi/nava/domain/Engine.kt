package com.aminfathi.nava.domain

import java.time.LocalDate

object Coverage {
    private val settled = setOf(Status.ANSWERED, Status.ANSWERED_NEGATIVE, Status.UNKNOWN, Status.SKIPPED, Status.NOT_APPLICABLE)
    fun applicable(field: Field, facts: Map<String, Fact>): Boolean {
        if (field.dependsOn == null) return true
        val dependency = facts[field.dependsOn] ?: return false
        return dependency.status == Status.ANSWERED && dependency.value == field.expected
    }
    fun pending(patient: Patient): List<Field> {
        val facts = patient.currentFacts()
        return Specification.fields(patient).filter { applicable(it, facts) && facts[it.id]?.status !in settled }
    }
    /** Topics the interview will still ask. Stored facts and topics already asked are left for the record. */
    private fun queue(patient: Patient): List<Field> {
        val facts = patient.currentFacts()
        val asked = patient.session.asked.toSet()
        return pending(patient).filter { facts[it.id] == null && it.id !in asked }.sortedBy { !it.required }
    }
    fun next(patient: Patient): Field? = queue(patient).firstOrNull()
    fun remaining(patient: Patient): Int = queue(patient).size
    fun addressed(patient: Patient): Pair<Int,Int> {
        val facts = patient.currentFacts()
        val fields = Specification.fields(patient).filter { it.required && applicable(it,facts) }
        return fields.count { facts[it.id]?.status in settled } to fields.size
    }
    fun complete(patient: Patient): Boolean = pending(patient).none { it.required }
    fun unresolved(patient: Patient) = Specification.fields(patient).filter { field ->
        applicable(field, patient.currentFacts()) && patient.currentFacts()[field.id]?.status !in setOf(Status.ANSWERED,Status.ANSWERED_NEGATIVE,Status.NOT_APPLICABLE)
    }
}
object FactValidator {
    fun validate(field: Field, proposal: Proposal): Status {
        if(proposal.status in setOf(Status.NOT_ASKED, Status.CONFLICTING)) return Status.NEEDS_CLARIFICATION
        if(proposal.status !in setOf(Status.ANSWERED,Status.ANSWERED_NEGATIVE)) return proposal.status
        if(proposal.status == Status.ANSWERED_NEGATIVE) return if(proposal.value.isBlank()) Status.NEEDS_CLARIFICATION else proposal.status
        if(proposal.value.isBlank()) return Status.NEEDS_CLARIFICATION
        val valid = when(field.kind) {
            Kind.TEXT -> proposal.value.length <= 4000
            Kind.BOOLEAN -> proposal.value in listOf("true","false")
            Kind.DATE -> runCatching { LocalDate.parse(proposal.value) }.isSuccess
            Kind.INTEGER, Kind.DECIMAL -> {
                val n = proposal.value.toDoubleOrNull()
                n != null && n.isFinite() && (field.min == null || n >= field.min) && (field.max == null || n <= field.max) && (field.kind != Kind.INTEGER || n % 1.0 == 0.0)
            }
        }
        val unitOk = field.units.isEmpty() || proposal.unit in field.units
        val dateOk = proposal.date.isEmpty() || runCatching { LocalDate.parse(proposal.date) }.isSuccess
        return if(valid && unitOk && dateOk) proposal.status else Status.NEEDS_CLARIFICATION
    }
}
object MemoryReducer {
    /** All revisions are append-only. A differing machine proposal becomes a conflict, even if it claims correction. */
    fun apply(patient: Patient, proposals: List<Proposal>, message: Message, language: String): Patient {
        require(message.role == "user")
        var result = patient
        for(p in proposals.distinctBy { it.fieldId }) {
            if(result.facts.any { it.fieldId == p.fieldId && it.sourceMessageId == message.id }) continue
            val field = Specification.fields(result).find { it.id == p.fieldId } ?: continue
            if(p.evidence.isBlank() || !message.text.contains(p.evidence)) continue
            val previous = result.currentFacts()[p.fieldId]
            val validated = FactValidator.validate(field,p)
            val differs = previous != null && (previous.value != p.value || previous.unit != p.unit || previous.status != validated)
            val status = if(differs) Status.CONFLICTING else validated
            val fact = Fact(fieldId=p.fieldId, value=p.value, unit=p.unit, date=p.date, status=status,
                source=if(p.fieldId.startsWith("partner.")) Source.SECONDHAND else p.source,
                sourceMessageId=message.id, evidence=p.evidence, language=language, correction=p.correction,
                supersedes=previous?.id, sessionId=patient.session.id)
            result = result.copy(facts=result.facts+fact)
        }
        return result
    }
    fun correct(patient: Patient, field: Field, value: String, unit: String, status: Status, language: String): Patient {
        val message = Message(role="user", text=value.ifBlank { status.name })
        val proposal = Proposal(field.id,value,unit,"",status,Source.PATIENT_REPORT,message.text,true)
        val validated = FactValidator.validate(field,proposal)
        val fact = Fact(fieldId=field.id,value=value,unit=unit,status=validated,sourceMessageId=message.id,
            evidence=message.text,language=language,reviewed=true,correction=true,
            supersedes=patient.currentFacts()[field.id]?.id, sessionId=patient.session.id)
        return patient.copy(facts=patient.facts+fact).withSession(patient.session.copy(messages=patient.session.messages+message))
    }
    fun summary(patient: Patient): String = patient.currentFacts().values.joinToString("\n") {
        "${it.fieldId} = ${it.value} ${it.unit} [${it.status}; ${it.source}; ${if(it.reviewed) "patient reviewed" else "unreviewed"}; ${it.updatedAt}]"
    }.take(24000)
}
object SafetyGuard {
    // Conservative secondary screen, not a triage classifier. UI symptom checklist remains available offline.
    private val phrases = listOf("can't breathe","cannot breathe","difficulty breathing","severe abdominal pain","heavy bleeding","fainting","chest pain", "تنگی نفس","درد شدید شکم","خونریزی شدید","غش", "صعوبة التنفس","نزيف شديد","ألم شديد", "سانس لینے میں دشواری","شدید خون", "շնչառության դժվար","ուժեղ ցավ", "nəfəs darlığı","güclü qanaxma", "هەناسەتەنگی","خوێنبەربوونی زۆر", "tengasiya nefesê","xwînrêjiya giran")
    fun needsAttention(text: String): Boolean = phrases.any { text.lowercase().contains(it) }
    fun allowsReply(text: String): Boolean = text.length in 1..4000 &&
        !Regex("(?iu)[\\p{N}]+(?:[.,٫][\\p{N}]+)?\\s*(?:mg|mcg|μg|µg|iu|units|milligram|microgram|میلی[‌ ]?گرم|میکروگرم|واحد|ملغ|ملجم|مليغرام|مائیکروگرام|ملی گرام|մգ|մկգ)" ).containsMatchIn(text)
}
