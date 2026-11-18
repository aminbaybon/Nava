@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.aminfathi.nava.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aminfathi.nava.domain.*
import java.text.NumberFormat

@Composable fun JourneyDashboard(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val p=s.patient
    Page {
        Text(tr("journey_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("journey_intro"),style=MaterialTheme.typography.bodyLarge)
        if(s.hospitals.any { it.demonstration } || !s.directoryLive) Text(tr("demo_banner"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.tertiary)
        CityChips(s.hospitals.map{it.city}.distinct(),s.browseCity) { city -> vm.browseCity(city); open("hospitals") }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            s.patients.forEach { patient -> FilterChip(selected=patient.id==p?.id,onClick={vm.selectPatient(patient.id)},enabled=!s.busy,label={Text(patient.nickname)}) }
        }
        if(p==null) { Action(tr("new_profile")) { open("clinical") }; return@Page }
        JourneyEditor(p,!s.busy,vm::saveJourney)
        Panel {
            Text(tr("journey_stages"),style=MaterialTheme.typography.titleLarge)
            JourneyMap.markers(p).forEach { marker ->
                val label=tr("stage_"+marker.marker.name.lowercase())
                ListItem(headlineContent={Text(label)},supportingContent={Text(tr("marker_"+marker.state.name.lowercase()))},leadingContent={
                    Icon(when(marker.state){MarkerState.DONE->Icons.Outlined.CheckCircle;MarkerState.CURRENT->Icons.Outlined.RadioButtonChecked;else->Icons.Outlined.RadioButtonUnchecked},null)
                })
            }
        }
        Text(tr("suitability_limit"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Action(tr("action_interview")) { open("interview") }
        OutlinedButton(onClick={open("documents")},modifier=Modifier.fillMaxWidth()){Text(tr("action_documents"))}
        OutlinedButton(onClick={open("hospitals")},modifier=Modifier.fillMaxWidth()){Text(tr("action_hospitals"))}
        OutlinedButton(onClick={open("requests")},modifier=Modifier.fillMaxWidth()){Text(tr("action_requests"))}
        OutlinedButton(onClick={open("stays")},modifier=Modifier.fillMaxWidth()){Text(tr("action_stays"))}
        OutlinedButton(onClick={open("travel")},modifier=Modifier.fillMaxWidth()){Text(tr("action_travel"))}
        OutlinedButton(onClick={open("budget")},modifier=Modifier.fillMaxWidth()){Text(tr("action_budget"))}
        OutlinedButton(onClick={open("concierge")},modifier=Modifier.fillMaxWidth()){Text(tr("action_concierge"))}
        OutlinedButton(onClick={open("notices")},modifier=Modifier.fillMaxWidth()){Text(tr("action_notices")+" · "+p.notices.count{!it.read})}
        TextButton(onClick={open("clinical")}){Text(tr("clinical_home"))}
    }
}
@Composable private fun JourneyEditor(p:Patient,enabled:Boolean,save:(JourneyProfile)->Unit) {
    val j=p.journey
    var residence by remember(p.id,j.updatedAt){mutableStateOf(j.residenceCountry)}
    var nationality by remember(p.id,j.updatedAt){mutableStateOf(j.nationality)}
    var country by remember(p.id,j.updatedAt){mutableStateOf(j.destinationCountry)}
    var city by remember(p.id,j.updatedAt){mutableStateOf(j.destinationCity)}
    var window by remember(p.id,j.updatedAt){mutableStateOf(j.travelWindow)}
    var min by remember(p.id,j.updatedAt){mutableStateOf(j.budgetMin)}
    var max by remember(p.id,j.updatedAt){mutableStateOf(j.budgetMax)}
    var currency by remember(p.id,j.updatedAt){mutableStateOf(j.budgetCurrency)}
    var companions by remember(p.id,j.updatedAt){mutableStateOf(j.companions)}
    var count by remember(p.id,j.updatedAt){mutableStateOf(j.companionCount.toString())}
    var access by remember(p.id,j.updatedAt){mutableStateOf(j.accessibility)}
    Panel {
        Text(tr("profile_section"),style=MaterialTheme.typography.titleLarge)
        Text(tr("journey_progressive"),style=MaterialTheme.typography.bodySmall)
        Field(tr("residence_country"),residence,{residence=it})
        Field(tr("nationality"),nationality,{nationality=it}); Text(tr("nationality_hint"),style=MaterialTheme.typography.bodySmall)
        Field(tr("destination_country"),country,{country=it}); Field(tr("destination_city"),city,{city=it})
        Field(tr("travel_window"),window,{window=it}); Field(tr("budget_min"),min,{min=it}); Field(tr("budget_max"),max,{max=it}); Field(tr("budget_currency"),currency,{currency=it})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){ CompanionPlan.entries.forEach { plan -> FilterChip(selected=companions==plan,onClick={companions=plan},label={Text(tr("companion_"+plan.name.lowercase()))}) } }
        Field(tr("companion_count"),count,{count=it.filter{ch->ch.isDigit()}.take(2)})
        Field(tr("accessibility_need"),access,{access=it})
        Action(tr("save_journey"),enabled){ save(j.copy(residenceCountry=residence.trim(),nationality=nationality.trim(),destinationCountry=country.trim(),destinationCity=city.trim(),travelWindow=window.trim(),budgetMin=min.trim(),budgetMax=max.trim(),budgetCurrency=currency.trim().ifBlank{"USD"},companions=companions,companionCount=count.toIntOrNull()?:0,accessibility=access.trim())) }
    }
}
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit){ OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
@Composable fun DocumentsScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val p=s.patient
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri -> if(uri!=null) vm.addAttachment(uri,AttachmentKind.DOCUMENT) }
    Page {
        Text(tr("documents_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("documents_notice"))
        if(p==null){Text(tr("empty_record"));return@Page}
        OutlinedButton(onClick={pick.launch(arrayOf("image/*","application/pdf"))},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("upload_document"))}
        p.attachments.forEach { item ->
            Panel {
                Text(item.name,style=MaterialTheme.typography.titleMedium)
                if(item.description.isNotBlank()) Text(item.description)
                Row { TextButton(onClick={vm.readAttachment(item.id)},enabled=!s.busy){Text(tr(if(item.ocr.isBlank())"read_file" else "read_again"))}; TextButton(onClick={vm.extractFindings(item.id)},enabled=!s.busy && item.ocr.isNotBlank()){Text(tr("extract_findings"))} }
            }
        }
        Action(tr("summary_title")){ open("findings") }
    }
}
@Composable fun FindingsScreen(vm:AppViewModel,s:AppState) {
    val p=s.patient ?: return
    val summary=p.careSummary
    Page {
        Text(tr("summary_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("summary_basis"))
        if(p.findings.isEmpty()) Text(tr("empty_findings"))
        p.findings.forEach { finding ->
            Panel {
                Text(finding.label,style=MaterialTheme.typography.titleMedium)
                Text(listOf(finding.value,finding.unit).filter{it.isNotBlank()}.joinToString(" "))
                Text(tr("evidence")+": "+finding.evidence,style=MaterialTheme.typography.bodySmall)
                Text(tr("confidence")+": "+finding.confidence+" · "+tr("verification_"+finding.verification.name.lowercase()),style=MaterialTheme.typography.bodySmall)
                if(finding.conflictsWith.isNotBlank()) Text(tr("conflict_finding"),color=MaterialTheme.colorScheme.error)
                if(finding.verification==FindingCheck.UNVERIFIED) Row {
                    TextButton(onClick={vm.setFinding(finding.id,FindingCheck.CONFIRMED)},enabled=!s.busy){Text(tr("confirm_finding"))}
                    TextButton(onClick={vm.setFinding(finding.id,FindingCheck.REJECTED)},enabled=!s.busy){Text(tr("reject_finding"))}
                }
            }
        }
        Action(tr("build_summary"),!s.busy){vm.buildSummary()}
        summary?.let {
            Panel {
                Text(tr("summary_concern")); Text(it.concern.ifBlank{tr("not_provided")})
                SummaryBlock(tr("summary_history"),it.history,p)
                SummaryBlock(tr("summary_treatments"),it.treatments,p)
                SummaryBlock(tr("summary_results"),it.results,p)
                SummaryBlock(tr("summary_conditions"),it.conditions,p)
                Text(tr("missing_info"),style=MaterialTheme.typography.titleMedium)
                it.missing.forEach { code -> Text("• "+tr(code)) }
                Text(tr("clinician_questions"),style=MaterialTheme.typography.titleMedium)
                it.questions.forEach { code -> Text("• "+tr(code)) }
                if(it.englishNote.isNotBlank()) { Text(tr("summary_english")); if(it.englishIsMachine) Text(tr("machine_translation"),color=MaterialTheme.colorScheme.tertiary); Text(it.englishNote) }
                OutlinedButton(onClick=vm::translateSummary,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("summary_english"))}
            }
        }
    }
}
@Composable private fun SummaryBlock(title:String,lines:List<SummaryLine>,p:Patient) {
    if(lines.isEmpty()) return
    Text(title,style=MaterialTheme.typography.titleMedium)
    lines.forEach { line ->
        val field=Specification.fields(p).find { it.id==line.fieldId }
        Text((field?.let { LocalLocalized.current.fieldLabel(it) } ?: line.fieldId)+": "+line.value)
    }
}
@Composable private fun CityChips(cities:List<String>,selected:String,onPick:(String)->Unit) {
    if(cities.isEmpty()) return
    Text(tr("filter_city"),style=MaterialTheme.typography.titleLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=selected.isBlank(),onClick={onPick("")},label={Text(tr("all_cities"))})
        cities.forEach { name -> FilterChip(selected=selected==name,onClick={onPick(name)},label={Text(name)}) }
    }
}
@Composable fun HospitalsScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val p=s.patient
    val known=s.hospitals.map{it.city}.distinct()
    var city by remember(p?.id){mutableStateOf(s.browseCity)}
    LaunchedEffect(s.browseCity){ city=s.browseCity }
    var language by remember{mutableStateOf(s.preferences.language.substringBefore('-'))}
    val matches=ProviderDiscovery.hospitals(s.hospitals,p?.pathway ?: Pathway.IVF,city,language,p?.journey?.budgetMax.orEmpty(),p?.journey?.budgetCurrency ?: "USD",p?.journey?.accessibility?.isNotBlank()==true)
    Page {
        Text(tr("hospitals_title"),style=MaterialTheme.typography.headlineLarge)
        Text(if(s.directoryLive) tr("directory_live") else tr("directory_demo"))
        Text(tr("match_note"),style=MaterialTheme.typography.bodySmall)
        CityChips(known,city) { city=it; vm.browseCity(it) }
        Field(tr("filter_language"),language,{language=it})
        OutlinedButton(onClick=vm::refreshDirectory,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("refresh_directory"))}
        if(matches.isEmpty()) Text(tr("empty_hospitals"))
        matches.forEach { match -> HospitalCard(match.hospital,match.reasons,p?.savedHospitals?.contains(match.hospital.id)==true,s.compareHospitals.contains(match.hospital.id),!s.busy,{vm.toggleHospital(match.hospital.id)},{vm.toggleCompare(match.hospital.id)},{vm.focusHospital(match.hospital.id);open("hospital")}) }
        if(s.compareHospitals.size>1) Action(tr("compare")){open("compareHospitals")}
    }
}
@Composable private fun HospitalCard(hospital:Hospital,reasons:List<String>,saved:Boolean,compared:Boolean,enabled:Boolean,save:()->Unit,compare:()->Unit,open:()->Unit) {
    Panel {
        if(hospital.demonstration) Text(tr("demo_banner"),color=MaterialTheme.colorScheme.tertiary)
        Text(hospital.name,style=MaterialTheme.typography.titleLarge)
        Text(hospital.city+", "+hospital.country)
        Text(hospital.services.joinToString(" · ")+" · "+hospital.languages.joinToString())
        if(hospital.indicativeMin!=null) Text(tr("indicative_cost")+": "+hospital.indicativeMin+"–"+hospital.indicativeMax+" "+hospital.currency); Text(hospital.indicativeNote,style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ reasons.forEach { AssistChip(onClick={},label={Text(tr("reason_"+it))}) } }
        Row { TextButton(onClick=open){Text(tr("open_profile"))}; TextButton(onClick=save,enabled=enabled){Text(tr(if(saved)"saved" else "favorite"))}; TextButton(onClick=compare,enabled=enabled){Text(if(compared) tr("saved") else tr("compare"))} }
    }
}
@Composable fun HospitalScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val hospital=s.hospitals.find{it.id==s.focusHospital} ?: run { Page { Text(tr("empty_hospitals")) }; return }
    val doctors=s.clinicians.filter{it.hospitalId==hospital.id}
    Page {
        if(hospital.demonstration) Text(tr("demo_banner"),color=MaterialTheme.colorScheme.tertiary)
        Text(hospital.name,style=MaterialTheme.typography.headlineLarge)
        Text(hospital.address); if(hospital.website.isBlank()) Text(tr("no_website")) else Text(hospital.website)
        Text(tr("no_photo"),style=MaterialTheme.typography.bodySmall)
        Text(hospital.facilities)
        Text(tr("accreditation")+": "+hospital.accreditation.ifBlank{tr("accreditation_unverified")})
        if(hospital.internationalDesk) Text(tr("international_desk"))
        Action(tr("doctors_title")){ open("doctors") }
        Action(tr("request_title")){ vm.focusHospital(hospital.id); open("request") }
        doctors.forEach { doctor -> TextButton(onClick={vm.focusClinician(doctor.id);open("doctor")}){Text(doctor.name)} }
        val context=LocalContext.current
        if(hospital.latitude!=null && hospital.longitude!=null) OutlinedButton(onClick={ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${hospital.latitude},${hospital.longitude}?q=${hospital.latitude},${hospital.longitude}"))) },modifier=Modifier.fillMaxWidth()){Text(tr("open_map"))}
    }
}
@Composable fun DoctorsScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val p=s.patient
    val prior=(p?.currentFacts()?.get("fertility.ivf_count")?.value?.toIntOrNull() ?: 0) > 0
    val matches=ProviderDiscovery.clinicians(s.clinicians,s.focusHospital.orEmpty(),s.preferences.language.substringBefore('-'),prior)
    Page {
        Text(tr("doctors_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("doctor_limit"),style=MaterialTheme.typography.bodySmall)
        matches.forEach { match ->
            Panel {
                if(match.clinician.demonstration) Text(tr("doctor_demo"),color=MaterialTheme.colorScheme.tertiary)
                Text(match.clinician.name,style=MaterialTheme.typography.titleLarge)
                Text(match.clinician.specialty+" · "+match.clinician.languages.joinToString())
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){ match.reasons.forEach { AssistChip(onClick={},label={Text(tr("reason_"+it))}) } }
                TextButton(onClick={vm.focusClinician(match.clinician.id);open("doctor")}){Text(tr("open_profile"))}
            }
        }
    }
}
@Composable fun DoctorScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val doctor=s.clinicians.find{it.id==s.focusClinician} ?: return
    Page {
        if(doctor.demonstration) Text(tr("doctor_demo"),color=MaterialTheme.colorScheme.tertiary)
        Text(doctor.name,style=MaterialTheme.typography.headlineLarge)
        Text(doctor.title+" · "+doctor.specialty)
        Text(doctor.qualifications)
        Text(doctor.biography)
        Text(doctor.experienceNote,style=MaterialTheme.typography.bodySmall)
        Text(doctor.practiceAreas.joinToString())
        Text(doctor.consultOptions)
        if(doctor.consultPrice!=null) Text(tr("consult_price")+": "+doctor.consultPrice+" "+doctor.consultCurrency) else Text(tr("no_verified_price"))
        if(doctor.photoUrl.isBlank()) Text(tr("no_photo"))
        Action(tr("request_title")){ vm.focusHospital(doctor.hospitalId); open("request") }
        TextButton(onClick={vm.toggleDoctor(doctor.id)},enabled=!s.busy){Text(tr(if(s.patient?.savedDoctors?.contains(doctor.id)==true) "saved" else "favorite"))}
    }
}
@Composable fun CompareHospitalsScreen(s:AppState) {
    val rows=s.hospitals.filter{it.id in s.compareHospitals}
    Page {
        Text(tr("compare"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("match_note"),style=MaterialTheme.typography.bodySmall)
        rows.forEach { hospital ->
            Panel {
                Text(hospital.name,style=MaterialTheme.typography.titleLarge)
                Text(hospital.city); Text(hospital.services.joinToString()); Text(hospital.languages.joinToString())
                Text(if(hospital.indicativeMin==null) tr("no_verified_price") else hospital.indicativeMin.toString()+"–"+hospital.indicativeMax+" "+hospital.currency)
                Text(hospital.indicativeNote,style=MaterialTheme.typography.bodySmall)
                Text(if(hospital.internationalDesk) tr("international_desk") else tr("accreditation_unverified"))
            }
        }
    }
}
@Composable fun RequestScreen(vm:AppViewModel,s:AppState) {
    val p=s.patient ?: return
    val draft=p.careRequests.find{it.id==s.focusRequest && it.status==RequestStatus.DRAFT}
    var hospitalId by remember(s.focusHospital,draft?.id){mutableStateOf(draft?.hospitalId ?: s.focusHospital.orEmpty())}
    var doctorId by remember(s.focusClinician,draft?.id){mutableStateOf(draft?.doctorId ?: s.focusClinician.orEmpty())}
    var note by remember(draft?.id){mutableStateOf(draft?.note.orEmpty())}
    var docs by remember(draft?.id){mutableStateOf(draft?.documentIds ?: emptyList())}
    var summary by remember(draft?.id){mutableStateOf(draft?.includeSummary == true)}
    var authorized by remember(draft?.id){mutableStateOf(draft?.authorized == true)}
    Page {
        Text(tr("request_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("share_warning"))
        s.hospitals.filter{p.pathway.name in it.services}.forEach { hospital -> FilterChip(selected=hospitalId==hospital.id,onClick={hospitalId=hospital.id},label={Text(hospital.name)}) }
        s.clinicians.filter{it.hospitalId==hospitalId}.forEach { doctor -> FilterChip(selected=doctorId==doctor.id,onClick={doctorId=doctor.id},label={Text(doctor.name)}) }
        OutlinedTextField(value=note,onValueChange={note=it},label={Text(tr("additional_questions"))},modifier=Modifier.fillMaxWidth())
        p.attachments.forEach { item -> Row(verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=item.id in docs,onCheckedChange={ docs=if(it) docs+item.id else docs-item.id }); Text(item.name) } }
        Row(verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=summary,onCheckedChange={summary=it}); Text(tr("include_summary")) }
        Row(verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=authorized,onCheckedChange={authorized=it}); Text(tr("share_authorize")) }
        OutlinedButton(onClick={vm.saveDraft(hospitalId,doctorId,note,docs,summary,authorized)},enabled=!s.busy && hospitalId.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(tr("save"))}
        Action(tr("submit_request"),!s.busy && authorized && hospitalId.isNotBlank()){ vm.submitDraft(hospitalId,doctorId,note,docs,summary,authorized) }
        Text(tr("request_needs_server"),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun RequestsScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val requests=s.patient?.careRequests.orEmpty()
    Page {
        Text(tr("action_requests"),style=MaterialTheme.typography.headlineLarge)
        OutlinedButton(onClick=vm::refreshDirectory,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("refresh_directory"))}
        if(requests.isEmpty()) Text(tr("empty_requests"))
        requests.forEach { request ->
            val name=s.hospitals.find{it.id==request.hospitalId}?.name ?: request.hospitalId
            Panel {
                Text(name,style=MaterialTheme.typography.titleLarge)
                Text(request.pathway.name+" · "+tr("status_"+request.status.name.lowercase()))
                TextButton(onClick={vm.focusRequest(request.id); open(if(request.quotations.isNotEmpty()) "quotes" else "messages")}){Text(tr("open_profile"))}
            }
        }
        if(requests.flatMap{it.quotations}.size>1) Action(tr("quote_compare")){open("compareQuotes")}
    }
}
@Composable fun QuotesScreen(vm:AppViewModel,s:AppState) {
    val request=s.patient?.careRequests?.find{it.id==s.focusRequest}
    Page {
        Text(tr("quote_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("quote_warning"))
        request?.quotations?.forEach { quote -> QuoteBlock(quote) }
        if(request?.status==RequestStatus.QUOTATION_RECEIVED) {
            Action(tr("accept_estimate"),!s.busy){vm.careTransition("ACCEPT")}
            OutlinedButton(onClick={vm.careTransition("DECLINE")},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("decline_estimate"))}
        }
        Text(tr("plan_pending"),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun QuoteBlock(quote:CareQuote) {
    Panel {
        if(quote.demonstration) Text(tr("demo_banner"),color=MaterialTheme.colorScheme.tertiary)
        Text(quote.treatment,style=MaterialTheme.typography.titleLarge)
        quote.lines.forEach { line -> Text(line.description+" · "+line.amount+(if(line.included) "" else " · "+tr("optional_line"))) }
        Text(tr("total_estimate")+": "+quote.total+" "+quote.currency)
        Text(tr("quote_expires")+": "+quote.expiresOn)
        if(quote.exclusions.isNotEmpty()) Text(tr("exclusions")+": "+quote.exclusions.joinToString())
        if(quote.qualifications.isNotBlank()) Text(quote.qualifications)
        if(quote.paymentTerms.isNotBlank()) Text(quote.paymentTerms)
    }
}
@Composable fun CompareQuotesScreen(s:AppState) {
    val quotes=s.patient?.careRequests?.flatMap{it.quotations}.orEmpty()
    Page {
        Text(tr("quote_compare"),style=MaterialTheme.typography.headlineLarge)
        if(QuotationAnalysis.inclusionsDiffer(quotes)) Text(tr("inclusions_differ"),color=MaterialTheme.colorScheme.tertiary)
        quotes.forEach { QuoteBlock(it) }
    }
}
@Composable fun MessagesScreen(vm:AppViewModel,s:AppState) {
    val request=s.patient?.careRequests?.find{it.id==s.focusRequest}
    var text by remember{mutableStateOf("")}
    Page {
        Text(tr("messages_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("actor_note"),style=MaterialTheme.typography.bodySmall)
        request?.messages?.forEach { message ->
            Panel { Text(tr("actor_"+message.actor.name.lowercase()),color=MaterialTheme.colorScheme.primary); Text(message.sender,style=MaterialTheme.typography.labelLarge); Text(message.body) }
        }
        OutlinedTextField(value=text,onValueChange={text=it},label={Text(tr("message_hint"))},modifier=Modifier.fillMaxWidth())
        Action(tr("send"),!s.busy && text.isNotBlank()){ vm.careMessage(text); text="" }
    }
}
@Composable fun StaysScreen(vm:AppViewModel,s:AppState,open:(String)->Unit) {
    val p=s.patient
    var kitchen by remember{mutableStateOf(false)}
    val guests=1+(p?.journey?.companionCount ?: 0)
    val rows=ProviderDiscovery.stays(s.stays,s.focusHospital.orEmpty(),guests,if(kitchen) true else null)
    Page {
        Text(tr("stays_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("stay_distance_note"),style=MaterialTheme.typography.bodySmall)
        FilterChip(selected=kitchen,onClick={kitchen=!kitchen},label={Text(tr("kitchen"))})
        StayMap(rows)
        rows.forEach { stay ->
            Panel {
                if(stay.demonstration) Text(tr("demo_banner"),color=MaterialTheme.colorScheme.tertiary)
                Text(stay.name,style=MaterialTheme.typography.titleLarge)
                Text(tr("stay_"+stay.type.name.lowercase()))
                Text(stay.address)
                if(stay.nightly!=null) Text(stay.nightly.toString()+" "+stay.currency+" · "+tr("indicative_cost")) else Text(tr("no_verified_price"))
                Text(tr("stay_rooms")+": "+stay.rooms+" · "+tr("guests")+": "+stay.guests+" · "+tr("suggested_nights")+": "+stay.suggestedNights)
                if(stay.listedDistanceKm!=null) Text(stay.listedDistanceKm.toString()+" km · "+stay.distanceSource)
                TextButton(onClick={vm.focusStay(stay.id);open("stay")}){Text(tr("open_profile"))}
            }
        }
        if(rows.isEmpty()) Text(tr("empty_stays"))
    }
}
@Composable private fun StayMap(stays:List<Stay>) {
    val points=stays.filter{it.latitude!=null && it.longitude!=null}
    if(points.size<2) return
    val minLat=points.minOf{it.latitude!!}; val maxLat=points.maxOf{it.latitude!!}
    val minLng=points.minOf{it.longitude!!}; val maxLng=points.maxOf{it.longitude!!}
    Text(tr("schematic_map"),style=MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        points.forEach { stay ->
            val x=((stay.longitude!!-minLng)/((maxLng-minLng).takeIf{it!=0.0} ?: 1.0)).toFloat()*size.width
            val y=(1-((stay.latitude!!-minLat)/((maxLat-minLat).takeIf{it!=0.0} ?: 1.0))).toFloat()*size.height
            drawCircle(color=androidx.compose.ui.graphics.Color(0xFF205E58),radius=14f,center=Offset(x,y))
        }
    }
}
@Composable fun StayScreen(s:AppState) {
    val stay=s.stays.find{it.id==s.focusStay} ?: return
    val context=LocalContext.current
    Page {
        Text(stay.name,style=MaterialTheme.typography.headlineLarge)
        if(stay.photoUrls.isEmpty()) Text(tr("no_photo"))
        Text(stay.address); Text(stay.amenities.joinToString()); Text(stay.accessibility.ifBlank{tr("accreditation_unverified")})
        Text(tr("stay_rooms")+": "+stay.rooms+" · "+tr("guests")+": "+stay.guests+" · "+tr("suggested_nights")+": "+stay.suggestedNights)
        Text(stay.transport)
        if(stay.bookingUrl.isBlank()) Text(tr("no_booking")) else Text(stay.bookingUrl)
        if(stay.latitude!=null) OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${stay.latitude},${stay.longitude}")))},modifier=Modifier.fillMaxWidth()){Text(tr("open_map"))}
    }
}
@Composable fun TravelScreen(vm:AppViewModel,s:AppState) {
    val p=s.patient ?: return
    val trip=p.trip
    var arrival by remember(p.id,trip.arrivalDate){mutableStateOf(trip.arrivalDate)}
    var transfer by remember(p.id,trip.transfer){mutableStateOf(trip.transfer)}
    var nights by remember(p.id){mutableStateOf(if(trip.nights>0) trip.nights.toString() else s.stays.find{it.id==s.focusStay}?.suggestedNights?.takeIf{it>0}?.toString().orEmpty())}
    var contact by remember(p.id){mutableStateOf(trip.emergencyContact)}
    val checks=listOf("checklist_passport","checklist_visa_official","checklist_records","checklist_insurance","checklist_contact")
    var flags by remember(p.id){mutableStateOf(trip.checklist)}
    Page {
        Text(tr("travel_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("visa_warning")); Text(tr("schedule_warning"))
        Field(tr("arrival_date"),arrival,{arrival=it}); Field(tr("airport_transfer"),transfer,{transfer=it}); Field(tr("nights"),nights,{nights=it.filter{ch->ch.isDigit()}.take(3)}); Field(tr("emergency_contact"),contact,{contact=it})
        checks.forEach { key -> Row(verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=flags[key]==true,onCheckedChange={ flags=flags+mapOf(key to it) }); Text(tr(key)) } }
        Action(tr("save"),!s.busy){ vm.saveTrip(trip.copy(arrivalDate=arrival.trim(),transfer=transfer.trim(),nights=nights.toIntOrNull()?:0,emergencyContact=contact.trim(),checklist=flags)) }
    }
}
@Composable fun BudgetScreen(vm:AppViewModel,s:AppState) {
    val p=s.patient ?: return
    val quote=p.careRequests.flatMap{it.quotations}.lastOrNull()
    val stay=s.stays.find{it.id==s.focusStay}
    var transport by remember(p.id){mutableStateOf(p.trip.transportEstimate)}
    var other by remember(p.id){mutableStateOf(p.trip.otherExpense)}
    val display=p.journey.budgetCurrency.ifBlank{"USD"}
    val rows=Money.budget(quote,stay?.nightly,p.trip.nights,stay?.currency ?: display,transport.toDoubleOrNull(),other.toDoubleOrNull(),p.trip.otherCurrency,display,s.rates)
    val format=NumberFormat.getNumberInstance()
    Page {
        Text(tr("budget_title"),style=MaterialTheme.typography.headlineLarge)
        Text(s.rates.source,style=MaterialTheme.typography.bodySmall)
        Field(tr("transport_estimate"),transport,{transport=it}); Field(tr("other_expense"),other,{other=it})
        OutlinedButton(onClick={vm.saveTrip(p.trip.copy(transportEstimate=transport,otherExpense=other))},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("save"))}
        rows.forEach { row ->
            Panel {
                Text(tr(row.code),style=MaterialTheme.typography.titleMedium)
                Text(format.format(row.amount)+" "+row.currency+" · "+tr(if(row.origin=="provider_quote") "confirmed_cost" else "estimated_cost"))
                if(row.displayAmount!=null && row.currency!=row.displayCurrency) Text(tr("fx_approximate")+": "+format.format(row.displayAmount)+" "+row.displayCurrency)
            }
        }
    }
}
@Composable fun ConciergeScreen(vm:AppViewModel,s:AppState) {
    val p=s.patient
    var text by remember{mutableStateOf("")}
    val examples=listOf("concierge_ex_hospitals","concierge_ex_doctors","concierge_ex_docs","concierge_ex_compare","concierge_ex_stay","concierge_ex_next","concierge_ex_excluded","concierge_ex_ask")
    Page {
        Text(tr("concierge_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("concierge_ai_label"))
        examples.forEach { key -> val label=tr(key); AssistChip(onClick={vm.askConcierge(label)},label={Text(label)}) }
        p?.conciergeLog?.forEach { message ->
            if(message.role=="user") Text(message.text,style=MaterialTheme.typography.titleMedium)
            else {
                val code=message.text.lineSequence().first()
                val entries=message.text.lineSequence().drop(1).filter{it.isNotBlank()}.toList()
                Panel { Text(tr("actor_ai_assistant")); Text(tr("brief_"+code)); entries.forEach { Text(it) } }
            }
        }
        OutlinedTextField(value=text,onValueChange={text=it},label={Text(tr("concierge_hint"))},modifier=Modifier.fillMaxWidth())
        Action(tr("ask_concierge"),text.isNotBlank() && !s.busy){ vm.askConcierge(text); text="" }
    }
}
@Composable fun NoticesScreen(vm:AppViewModel,s:AppState) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    Page {
        Text(tr("notices_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("notice_privacy"))
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            OutlinedButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)},modifier=Modifier.fillMaxWidth()){Text(tr("enable_alerts"))}
        s.patient?.notices?.asReversed()?.forEach { notice -> Text(tr("notice_"+notice.code)+" · "+notice.createdAt.substringBefore('T')) }
        TextButton(onClick=vm::readNotices,enabled=!s.busy){Text(tr("mark_read"))}
    }
}
