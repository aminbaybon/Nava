package com.aminfathi.nava.domain

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EngineTest {
    private fun proposal(id:String,value:String,evidence:String=value,unit:String="",status:Status=Status.ANSWERED)=Proposal(id,value,unit,"",status,Source.PATIENT_REPORT,evidence,false)
    private fun add(p:Patient,id:String,value:String,unit:String="",status:Status=Status.ANSWERED):Patient {
        val m=Message(role="user",text=value.ifBlank { "unknown" })
        return MemoryReducer.apply(p,listOf(proposal(id,value,m.text,unit,status)),m,"en")
    }
    @Test fun unknownAMHIsNeverNormal() {
        val p=add(Patient(nickname="Fictional"),"tests.amh","I do not know",status=Status.UNKNOWN)
        assertEquals(Status.UNKNOWN,p.currentFacts()["tests.amh"]!!.status)
        assertFalse(MemoryReducer.summary(p).contains("normal"))
    }
    @Test fun preservesCorrectionHistoryUntilPatientResolves() {
        val first=add(Patient(nickname="Test"),"fertility.ivf_count","2")
        val second=add(first,"fertility.ivf_count","3")
        assertEquals(Status.CONFLICTING,second.currentFacts()["fertility.ivf_count"]!!.status)
        assertEquals("2",second.facts.first().value)
        val resolved=MemoryReducer.correct(second,Specification.base.first{it.id=="fertility.ivf_count"},"3","",Status.ANSWERED,"en")
        assertEquals(3,resolved.facts.size)
        assertTrue(resolved.currentFacts()["fertility.ivf_count"]!!.reviewed)
        assertEquals(Status.ANSWERED,resolved.currentFacts()["fertility.ivf_count"]!!.status)
    }
    @Test fun rejectsUnsupportedFactsAndInventedEvidence() {
        val p=Patient(nickname="Test");val m=Message(role="user",text="I am unsure")
        val result=MemoryReducer.apply(p,listOf(proposal("tests.amh","4","AMH was 4",unit="ng/mL"),proposal("diagnosis.fake","yes","I am unsure")),m,"en")
        assertTrue(result.facts.isEmpty())
    }
    @Test fun duplicateExtractionDoesNotAppendFacts() {
        val m=Message(role="user",text="2");val proposals=listOf(proposal("fertility.ivf_count","2"))
        val p=MemoryReducer.apply(Patient(nickname="Test"),proposals,m,"en")
        val again=MemoryReducer.apply(p,proposals,m,"en")
        assertEquals(p.facts,again.facts)
    }
    @Test fun previousIVFBranchesForEachCycle() {
        val p=add(Patient(nickname="Test"),"fertility.ivf_count","2")
        val fields=Specification.fields(p)
        assertTrue(fields.any {it.id=="ivf.cycle.2.outcome"})
        assertFalse(fields.any {it.id=="ivf.cycle.3.outcome"})
        assertEquals(22,fields.count {it.id.startsWith("ivf.cycle.")})
    }
    @Test fun iuiAndPregnancyRepeatersAreIndependent() {
        val p=add(add(Patient(nickname="Test",pathway=Pathway.IUI),"fertility.iui_count","1"),"obstetric.count","2")
        val fields=Specification.fields(p)
        assertEquals(8,fields.count {it.id.startsWith("iui.cycle.")})
        assertEquals(6,fields.count {it.id.startsWith("obstetric.pregnancy.")})
        assertFalse(fields.any {it.id=="ivf.plan"})
    }
    @Test fun switchingPathwayPreservesOldCycles() {
        val p=add(Patient(nickname="Test"),"fertility.ivf_count","1").copy(pathway=Pathway.IUI)
        assertTrue(Specification.fields(p).any {it.id=="ivf.cycle.1.outcome"})
    }
    @Test fun skippedSensitiveFieldsAreNotRepeated() {
        val p=add(Patient(nickname="Test"),"sexual.history","Prefer not",status=Status.SKIPPED)
        assertFalse(Coverage.pending(p).any {it.id=="sexual.history"})
        assertTrue(Coverage.unresolved(p).any {it.id=="sexual.history"})
    }
    @Test fun unknownAndNegativeAreDistinct() {
        val p=add(Patient(nickname="Test"),"medication.allergies","I do not know",status=Status.UNKNOWN)
        val negative=add(Patient(nickname="Test"),"medication.allergies","No known allergies",status=Status.ANSWERED_NEGATIVE)
        assertNotEquals(p.currentFacts()["medication.allergies"]!!.status,negative.currentFacts()["medication.allergies"]!!.status)
    }
    @Test fun numericUnitsAndRangesRequireClarification() {
        val field=Specification.base.first {it.id=="tests.amh"}
        assertEquals(Status.NEEDS_CLARIFICATION,FactValidator.validate(field,proposal(field.id,"5")))
        assertEquals(Status.NEEDS_CLARIFICATION,FactValidator.validate(field,proposal(field.id,"-1",unit="ng/mL")))
        assertEquals(Status.ANSWERED,FactValidator.validate(field,proposal(field.id,"5",unit="ng/mL")))
    }
    @Test fun datesAreNotGuessed() {
        val field=Specification.base.first {it.id=="tests.amh"}
        val p=proposal(field.id,"2",unit="ng/mL").copy(date="1404/02/10")
        assertEquals(Status.NEEDS_CLARIFICATION,FactValidator.validate(field,p))
    }
    @Test fun nonFiniteAndFractionalCountsFail() {
        val field=Specification.base.first{it.id=="fertility.ivf_count"}
        listOf("NaN","Infinity","1.5","-1","1000").forEach { value->assertEquals(Status.NEEDS_CLARIFICATION,FactValidator.validate(field,proposal(field.id,value))) }
    }
    @Test fun askedTopicIsNotRepeatedWhenNothingWasStored() {
        val age=Specification.base.first { it.id=="background.age" }
        val p=Patient(nickname="Test",pathway=Pathway.IUI,sessions=listOf(Session(currentField=age.id,asked=listOf(age.id))))
        assertNull(p.currentFacts()[age.id])
        assertNotEquals(age.id,Coverage.next(p)?.id)
        assertEquals(Coverage.remaining(Patient(nickname="Test",pathway=Pathway.IUI))-1,Coverage.remaining(p))
        assertTrue(Coverage.pending(p).any { it.id==age.id })
    }
    @Test fun clarificationStaysOnRecordWithoutRepeatingTheQuestion() {
        val age=Specification.base.first { it.id=="background.age" }
        val message=Message(role="user",text="about thirty")
        val p=MemoryReducer.apply(Patient(nickname="Test",pathway=Pathway.IUI),listOf(proposal(age.id,"about thirty",message.text)),message,"fa")
        assertEquals(Status.NEEDS_CLARIFICATION,p.currentFacts()[age.id]!!.status)
        assertNotEquals(age.id,Coverage.next(p)?.id)
        assertTrue(Coverage.pending(p).any { it.id==age.id })
    }
    @Test fun conflictStaysOnRecordWithoutRepeatingTheQuestion() {
        val age=Specification.base.first { it.id=="background.age" }
        val p=add(add(Patient(nickname="Test",pathway=Pathway.IUI),age.id,"30"),age.id,"31")
        assertEquals(Status.CONFLICTING,p.currentFacts()[age.id]!!.status)
        assertNotEquals(age.id,Coverage.next(p)?.id)
        assertTrue(Coverage.pending(p).any { it.id==age.id })
    }
    @Test fun conditionalQuestionsWaitForApplicability() {
        val p=Patient(nickname="Test")
        assertFalse(Coverage.pending(p).any {it.domain=="menstrual"})
        val yes=add(p,"background.menses","true")
        assertTrue(Coverage.pending(yes).any {it.domain=="menstrual"})
        val no=add(p,"background.menses","false")
        assertFalse(Coverage.pending(no).any {it.domain=="menstrual"})
    }
    @Test fun partnerFactsAreAlwaysSecondhand() {
        val p=add(Patient(nickname="Test"),"partner.medical","Healthy as far as I know")
        assertEquals(Source.SECONDHAND,p.currentFacts()["partner.medical"]!!.source)
    }
    @Test fun independentPatientsHaveIndependentFacts() {
        val a=add(Patient(nickname="A"),"fertility.ivf_count","2")
        val b=Patient(nickname="B")
        assertNotEquals(a.id,b.id);assertTrue(b.facts.isEmpty())
    }
    @Test fun processDeathRoundTripPreservesPendingMessageAndProvenance() {
        val m=Message(role="user",text="2",state=MessageState.PENDING)
        val original=add(Patient(nickname="Test",sessions=listOf(Session(messages=listOf(m)))),"fertility.ivf_count","2")
        val restored=Json.decodeFromString<Patient>(Json.encodeToString(original))
        assertEquals(original,restored);assertEquals(MessageState.PENDING,restored.session.messages.single().state)
    }
    @Test fun noAutonomousCompleteness() { assertFalse(Coverage.complete(Patient(nickname="Test"))) }
    @Test fun urgentPhrasesWorkAcrossScripts() {
        listOf("severe abdominal pain","تنگی نفس","نزيف شديد","سانس لینے میں دشواری","nəfəs darlığı","هەناسەتەنگی","tengasiya nefesê","ուժեղ ցավ").forEach {assertTrue(SafetyGuard.needsAttention(it),it)}
    }
    @Test fun doseAdviceIsRejected() { assertFalse(SafetyGuard.allowsReply("Take 10 mg tonight"));assertTrue(SafetyGuard.allowsReply("Which medication did your clinician prescribe?")) }
    @Test fun doseAdviceIsBlockedAcrossScripts() {
        listOf("۵۰ میلی‌گرم مصرف کن", "تناول 20 ملغ", "take 5 units").forEach {assertFalse(SafetyGuard.allowsReply(it))}
    }
    @Test fun packetQuotesPrintedLinesOnly() {
        val text="AMH 1.2 ng/mL\nHealthy lifestyle\nLH 4\nسونوگرافی رحم"
        val ivf=PacketTemplate.fill(Pathway.IVF,listOf(text))
        assertEquals(listOf("AMH 1.2 ng/mL"),ivf.first { it.first=="detail_amh" }.second)
        assertEquals(listOf("LH 4"),ivf.first { it.first=="detail_lh" }.second)
        assertTrue(ivf.none { "Healthy" in it.second.joinToString() })
        assertTrue(ivf.any { it.first=="detail_embryos" })
        val iui=PacketTemplate.fill(Pathway.IUI,listOf(text))
        assertTrue(iui.any { it.first=="detail_tubes" })
        assertTrue(iui.none { it.first=="detail_embryos" })
        assertEquals(listOf("سونوگرافی رحم"),iui.first { it.first=="detail_ultrasound" }.second)
    }
    @Test fun olderRecordWithoutFilesStillLoads() {
        val p=Json { ignoreUnknownKeys=false }.decodeFromString<Patient>("""{"nickname":"N","schemaVersion":1}""")
        assertTrue(p.attachments.isEmpty());assertTrue(p.packets.isEmpty());assertEquals(1,p.schemaVersion)
    }
    @Test fun stableSpecificationHasUniqueIdsAndSources() {
        val p=add(add(Patient(nickname="Test"),"fertility.ivf_count","3"),"fertility.iui_count","2")
        val f=Specification.fields(p)
        assertEquals(f.size,f.map {it.id}.distinct().size)
        assertTrue(f.all {it.sources.isNotEmpty() && it.intent.isNotBlank()})
    }
}
