# Clinical coverage and source register

Specification version: **2026-11-18.1**. Evidence-informed authoring, **not clinician approved**. The source list is a research provenance register, not society endorsement or a clinical-use license.

The supplied requirements defined the requested history domains. The following primary sources were checked to ground their scope and distinguish intake from examination, investigations and treatment decisions. The app does not implement treatment recommendations from these documents.

| Source ID | Primary source | How it informed the implementation |
|---|---|---|
| ASRM2026 | [Fertility evaluation of infertile women, 2026](https://www.asrm.org/practice-guidance/practice-committee-documents/fertility-evaluation-of-infertile-women-a-committee-opinion-2026) | Scope of shared reproductive, medical, family and contextual history; tailored applicability rather than assuming every patient has the same reproductive role. |
| WHO2025 | [Guideline for prevention, diagnosis and treatment of infertility](https://www.who.int/publications/i/item/9789240115774), published 28 November 2025 | Comprehensive evaluation context and inclusive fertility care. History collection is separated from clinical diagnosis and management. |
| ESHRE_UI2023 | [Unexplained infertility guideline](https://www.eshre.eu/Guidelines-and-Legal/Guidelines/Unexplained-infertility), [full guideline](https://www.eshre.eu/-/media/sitecore-files/Guidelines/UI/UI-guideline_-Final.pdf) | Existing assessments and previous IUI history; no automated diagnosis of unexplained infertility or treatment selection. |
| ESHRE_OS2025_2026update | [Ovarian stimulation guideline](https://www.eshre.eu/OSGuideline), [September 2026 updated PDF](https://www.eshre.eu/-/media/sitecore-files/Guidelines/COS/2025/ESHRE-OS-guideline-updateSept-2026v23.pdf) | IVF cycle chronology and patient-reported stimulation, response, retrieval, outcomes and complications. No doses or protocol selection. The live page notes a temporarily removed biosimilar section; it is not used here. |
| AUA2024 | [AUA/ASRM amended 2024 guideline](https://www.auanet.org/documents/Guidelines/PDF/2024%20Guidelines/Male%20Infertility%20Unabridged%20Final.pdf), [accessible ASRM Part I](https://www.asrm.org/practice-guidance/practice-committee-documents/diagnosis-and-treatment-of-infertility-in-men-auaasrm-guideline-part-i-2020/) | Contributor reproductive/urologic/medical history and already available semen results. The amended PDF was indexed but direct retrieval returned 403; the ASRM history source was accessible. Detailed 2024 amendment review remains a release task. |
| ACOG2019 | [Prepregnancy counseling](https://www.acog.org/clinical/clinical-guidance/committee-opinion/articles/2019/01/prepregnancy-counseling) | Preconception context, existing genetic information and optional personal safety discussion. Indexed source was available; full-page retrieval was blocked. Full current-document review remains required. |
| RCOG_OHSS | [OHSS patient information](https://www.rcog.org.uk/for-the-public/browse-our-patient-information/ovarian-hyperstimulation-syndrome/) | Escalation wording for concerning symptoms after treatment. The leaflet is dated July 2016; the app must not represent it as a newly validated triage algorithm. |

## Coverage mapping

| Domain | Fields / branches | Applicability and important limits |
|---|---|---|
| Background | Age, concern, referral, menstrual applicability, partner/donor context | No legal ID, marital or heterosexual assumption. |
| Fertility | Duration, past attempts, reported diagnoses, tracking, prior treatment and cycle counts | IVF counts include cancelled stimulation cycles; a transfer is not silently counted as a new stimulation cycle. |
| Menstrual | Menarche, last period, pattern, bleeding, pain, endocrine symptoms | Only when patient states menstrual history applies. Non-Gregorian/ambiguous dates are not guessed. |
| Obstetric | Number of pregnancies, postpartum context and repeatable pregnancy date/outcome/complications | Sensitive histories can be skipped. Loss, ectopic and other outcomes remain patient-reported. |
| Gynecologic | Conditions, infection, cervix, pain, contraception | Contextual and sensitive questions; no new diagnoses. |
| Medical / surgery | Chronic disease, cancer/exposure, mental health, hospital admissions, surgery | Narrative fields capture supplied details; clinician must review completeness. |
| Medicines / allergies | Current medicines, supplements, allergy and reaction history | Names/doses only as supplied. Unknown differs from explicit negative. |
| Family / genetics | Family history and existing genetic information | Optional sensitive detail; no new testing recommendation. |
| Lifestyle / emotional | Nicotine, substances, habits, exposure, stress, barriers | Nonjudgmental, sensitive and skippable. |
| Sexual | Relevant sexual history and optional safety concerns | No assumption of intercourse; do not demand trauma details. |
| Contributor | Fertility, urologic, medical, medication, sexual, lifestyle | Conditional; every fact is marked secondhand. Separate partner-login consent is not implemented. |
| Existing tests | Available results, AMH/AFC/FSH/LH/estradiol/TSH/prolactin, ultrasound, tubes, semen and screening | Optional; original value, units/date and reported source. No normal/abnormal interpretation. |
| IVF | Existing plan, reported risks; repeatable cycle date/protocol/response/retrieval/fertilization/embryos/PGT/transfer/outcome/storage/complications | Every reported cycle gets its own IDs. Technical details are optional if not known. |
| IUI | Existing plan/assessment; repeatable cycle date/stimulation/monitoring/trigger/insemination/sperm/outcome/complications | Records clinician-reported assessment; never decides suitability. |
| Completion | Current symptoms, questions and priorities | Deterministic coverage, explicit missing/uncertain information. |

The generated [JSON specification](clinical-specification.json) contains stable field IDs, type, required flag, dependencies, accepted units, bounds, follow-up policy and source IDs. Repeatable fields use a numbered template; runtime expands up to 30 reported cycles/pregnancies per type. Counts beyond this range require clarification rather than truncating the patient's clinical narrative silently.

## Meaning of statuses

ANSWERED and ANSWERED_NEGATIVE are different. UNKNOWN, SKIPPED and NOT_APPLICABLE settle a question so the app does not pressure the patient. NEEDS_CLARIFICATION and CONFLICTING remain open. Progress counts required applicable topics addressed; it does not reward claiming that an unknown test is normal. Optional existing tests never make the interview clinically mandatory or trigger an investigation order.

## Known clinical limitations

Several fields group related history into narratives, so topic coverage is not proof that every subquestion was individually asked. A qualified clinician must refine this draft specification into the intended local clinical protocol, including fine-grained test and medication schemas, translation, red-flag timing/context and patient comprehension testing. There is no specialist approval in this delivery. Model extraction can still be wrong even with schema/quote checks; unreviewed labels and correction workflows are essential, not a substitute for validation.
