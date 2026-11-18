package com.aminfathi.nava.domain

/** Versioned intake specification, not a testing or treatment recommendation. */
object Specification {
    const val VERSION = "2026-11-18.1"
    val base: List<Field> = buildList {
        fun f(id: String, domain: String, intent: String, kind: Kind = Kind.TEXT, required: Boolean = true,
              sensitive: Boolean = false, depends: String? = null, expected: String? = null,
              units: List<String> = emptyList(), min: Double? = null, max: Double? = null,
              sources: List<String> = listOf("ASRM2026", "WHO2025")) {
            add(Field(id, domain, intent, kind, required, sensitive, dependsOn=depends, expected=expected, units=units, min=min, max=max, sources=sources))
        }
        f("background.age", "background", "Age in years; do not request legal identity", Kind.INTEGER, min=0.0, max=120.0)
        f("background.concern", "background", "Main fertility concern, reason for visit and personal goals")
        f("background.referral", "background", "Referral or previous specialist consultation", required=false)
        f("background.menses", "background", "Is menstrual history applicable to your own body?", Kind.BOOLEAN)
        f("background.partner", "background", "Is a partner contributing sperm, with relevant history you can share?", Kind.BOOLEAN)
        f("background.donor", "background", "Partner, donor or other reproductive contribution context; avoid assumptions")
        f("fertility.duration", "fertility", "Duration of trying to conceive, if applicable; use NOT_APPLICABLE if not trying", Kind.DECIMAL, units=listOf("months", "years"), min=0.0, max=100.0)
        f("fertility.prior_attempts", "fertility", "Previous periods of trying and any prior conceptions")
        f("fertility.diagnoses", "fertility", "Previously reported fertility diagnoses and who made them; never infer a diagnosis")
        f("fertility.tracking", "fertility", "Ovulation tracking or timed attempts, if applicable", required=false)
        f("fertility.ivf_count", "fertility", "Total previous IVF cycles including cancelled cycles; avoid counting transfers as stimulation cycles", Kind.INTEGER, min=0.0, max=30.0)
        f("fertility.iui_count", "fertility", "Total previous IUI cycles including cancellations", Kind.INTEGER, min=0.0, max=30.0)
        f("fertility.other_treatment", "fertility", "Other previous fertility care and reported outcomes")
        val menstrual = listOf(
            "menarche" to "Age at first period", "last_period" to "First day of most recent period; preserve approximate or non-Gregorian dates as uncertain",
            "pattern" to "Typical cycle interval, variability, missed periods and any prolonged absence", "bleeding" to "Bleeding duration, heaviness and bleeding between periods",
            "pain" to "Period pain and its impact", "endocrine" to "Any reported excess hair, milk discharge, thyroid symptoms or hot flushes")
        menstrual.forEach { (key,intent) -> f("menstrual.$key", "menstrual", intent, depends="background.menses", expected="true") }
        f("obstetric.count", "obstetric", "Number of previous pregnancies, if applicable, including losses", Kind.INTEGER, min=0.0, max=30.0)
        f("obstetric.postpartum", "obstetric", "Relevant postpartum health or breastfeeding, if applicable", required=false)
        listOf(
            "conditions" to "Reported endometriosis, adenomyosis, fibroids, polyps, cysts or uterine differences",
            "infection" to "Prior pelvic infections or sexually transmitted infections, if comfortable",
            "cervix" to "Relevant cervical screening and procedures",
            "pain" to "Persistent pelvic pain or pain with intercourse, if applicable",
            "contraception" to "Previous contraception, timing of stopping and relevant difficulties"
        ).forEach { (k,q) -> f("gynecology.$k", "gynecology", q, sensitive=k=="infection" || k=="pain", depends="background.menses", expected="true") }
        listOf(
            "chronic" to "Chronic illnesses including thyroid, diabetes, blood pressure, autoimmune or clotting conditions",
            "cancer" to "History of cancer, chemotherapy, radiation or other fertility-affecting exposure",
            "mental_health" to "Relevant mental health history, if comfortable",
            "hospital" to "Relevant hospital admissions",
            "surgery" to "Abdominal, pelvic, ovarian, tubal, uterine or other surgery; dates and complications when known"
        ).forEach { (k,q) -> f("medical.$k", "medical", q, sensitive=k=="mental_health") }
        f("medication.current", "medication", "Current prescriptions, over-the-counter drugs, hormones and supplements; dose and timing only if supplied")
        f("medication.allergies", "medication", "Known allergies and reactions; distinguish no known allergies from unknown")
        f("family.history", "family", "Relevant inherited conditions, infertility, early menopause, losses, birth differences or familial cancers")
        f("family.genetics", "family", "Known carrier status, previous genetic counseling or tests; consanguinity only if relevant", required=false, sensitive=true, sources=listOf("ACOG2019", "WHO2025"))
        listOf("nicotine" to "Smoking, vaping or passive smoke exposure", "alcohol" to "Alcohol and recreational substances, without judgment", "habits" to "Nutrition, physical activity, sleep and major weight changes", "exposure" to "Workplace or environmental exposures", "stress" to "Emotional impact and available support", "barriers" to "Practical or financial barriers and communication preferences").forEach { (k,q) -> f("lifestyle.$k", "lifestyle", q, required=k!="barriers", sensitive=true) }
        f("sexual.history", "sexual", "If relevant and comfortable: timing or frequency of intercourse, pain or sexual difficulties", sensitive=true)
        f("sexual.safety", "sexual", "Optional: any safety, trauma or reproductive coercion concerns you wish the clinician to know", required=false, sensitive=true, sources=listOf("ACOG2019"))
        listOf("fertility" to "Contributor reproductive history and previous pregnancies", "urologic" to "Contributor testicular or urologic conditions, infections and surgery", "medical" to "Contributor medical, surgical and family history", "medication" to "Contributor medication, testosterone or anabolic steroid use", "sexual" to "Relevant erectile or ejaculatory difficulties", "lifestyle" to "Contributor lifestyle and occupational exposures").forEach { (k,q) -> f("partner.$k", "partner", "$q; mark all as secondhand", sensitive=true, depends="background.partner", expected="true", sources=listOf("AUA2024")) }
        f("tests.available", "tests", "Which fertility investigation results do you already have? No new tests are requested")
        listOf("amh" to listOf("ng/mL","pmol/L"), "afc" to listOf("count"), "fsh" to listOf("IU/L","mIU/mL"), "lh" to listOf("IU/L","mIU/mL"), "estradiol" to listOf("pg/mL","pmol/L"), "tsh" to listOf("mIU/L"), "prolactin" to listOf("ng/mL","mIU/L")).forEach { (k,u) -> f("tests.$k", "tests", "Existing $k result ONLY if available; value, original unit, date and source. Unknown is valid; no interpretation", Kind.DECIMAL, required=false, units=u, min=0.0, max=100000.0) }
        listOf("ultrasound" to "Existing pelvic ultrasound or uterine cavity findings", "tubes" to "Existing tubal assessment such as HSG or HyCoSy, date and clinician-reported findings", "semen" to "Existing semen analysis: date, laboratory, volume, concentration, motility and morphology with original units", "screening" to "Already completed relevant infection, genetic or metabolic screening").forEach { (k,q) -> f("tests.$k", "tests", q, required=false, sources=listOf("ESHRE_UI2023", "AUA2024", "WHO2025")) }
        f("symptoms.current", "symptoms", "Any current symptoms or concerns, including recent treatment complications")
        f("goals.questions", "goals", "Questions or priorities to take to the fertility specialist")
        add(Field("ivf.plan", "ivf", "Reason for IVF as explained by clinician, existing IVF/ICSI plans, preservation or donor context; no treatment selection", pathway=Pathway.IVF, sources=listOf("ESHRE_OS2025_2026update")))
        add(Field("ivf.risks", "ivf", "Previous ovarian response, reported OHSS or other complications and prior counseling", pathway=Pathway.IVF, sources=listOf("ESHRE_OS2025_2026update", "RCOG_OHSS")))
        add(Field("iui.plan", "iui", "Reason for IUI as explained by clinician, sperm source and existing plan", pathway=Pathway.IUI, sources=listOf("ESHRE_UI2023")))
        add(Field("iui.assessment", "iui", "Already documented ovulation, uterine, tubal and semen assessment; reported contraindications, no eligibility decision", pathway=Pathway.IUI, sources=listOf("ESHRE_UI2023")))
    }
    fun fields(patient: Patient): List<Field> = buildList {
        addAll(base.filter { it.pathway == null || it.pathway == patient.pathway })
        val facts = patient.currentFacts()
        fun count(key: String) = facts[key]?.takeIf { it.status == Status.ANSWERED }?.value?.toIntOrNull()?.coerceIn(0,30) ?: 0
        for (type in listOf("ivf","iui")) for (i in 1..count("fertility.${type}_count")) {
            val items = if (type == "ivf") listOf(
                "date" to "Approximate date and clinic", "protocol" to "Reported stimulation protocol, medications and duration", "response" to "Follicular response and monitoring, if known", "retrieval" to "Retrieval date, retrieved and mature egg counts, if known", "fertilization" to "IVF or ICSI and fertilization results", "embryos" to "Development, blastocysts and grading, if known", "pgt" to "PGT performed and patient-reported results, if any", "transfer" to "Fresh or frozen transfer, date and embryo count", "outcome" to "Pregnancy outcome or cancellation and clinician-reported reason", "storage" to "Embryos or gametes remaining in storage", "complications" to "Complications, OHSS, hospitalization or other concerns"
            ) else listOf("date" to "Approximate date and clinic", "stimulation" to "Natural or stimulated cycle; medications if known", "monitoring" to "Monitoring and follicle results if known", "trigger" to "Trigger medication and timing if known", "insemination" to "Insemination timing and sperm source", "sperm" to "Preparation and pre/post-wash parameters with units if known", "outcome" to "Pregnancy outcome or cancellation; clinician-reported reason", "complications" to "Any complications or concerns")
            items.forEach { (k,q) -> add(Field("$type.cycle.$i.$k", "${type}_cycles", "$type cycle $i: $q", required=k in setOf("date","outcome","complications"), sources=listOf(if(type=="ivf") "ESHRE_OS2025_2026update" else "ESHRE_UI2023"))) }
        }
        for(i in 1..count("obstetric.count")) listOf("date" to "Approximate pregnancy date, gestational age and conception context", "outcome" to "Live birth, loss, ectopic, termination or other outcome, using patient's terms", "complications" to "Complications, investigations, delivery method and postpartum issues if relevant").forEach { (k,q) -> add(Field("obstetric.pregnancy.$i.$k", "obstetric", "Pregnancy $i: $q", sensitive=true)) }
    }
}
