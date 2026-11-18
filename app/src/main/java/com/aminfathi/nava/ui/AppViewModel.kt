package com.aminfathi.nava.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.provider.OpenableColumns
import com.aminfathi.nava.NavaApplication
import com.aminfathi.nava.ai.Prompts
import com.aminfathi.nava.data.Preferences
import com.aminfathi.nava.domain.*
import com.aminfathi.nava.files.PageImages
import com.aminfathi.nava.files.RejectedFile
import com.aminfathi.nava.network.PortalException
import com.aminfathi.nava.network.ProviderException
import com.aminfathi.nava.reports.PacketComposer
import com.aminfathi.nava.reports.ReportComposer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AppState(val loaded:Boolean=false,val preferences:Preferences=Preferences(),val patients:List<Patient> = emptyList(),val activeId:String?=null,
    val busy:Boolean=false,val live:String="",val error:String?=null,val errorDetail:String?=null,val models:List<String> = emptyList(),val hasKey:Boolean=false,val urgent:Boolean=false,val exportedUri:Uri?=null,val exportedMime:String="application/pdf",val exportedReportId:String?=null,
    val hospitals:List<Hospital> = emptyList(),val clinicians:List<Clinician> = emptyList(),val stays:List<Stay> = emptyList(),val rates:FxTable = FxTable(),val directoryLive:Boolean=false,
    val focusHospital:String?=null,val focusClinician:String?=null,val focusStay:String?=null,val focusRequest:String?=null,val compareHospitals:List<String> = emptyList(),val browseCity:String="",val portalRole:String="") {
    val patient get()=patients.find { it.id==activeId } ?: patients.firstOrNull()
}
class AppViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as NavaApplication
    private val mutable=MutableStateFlow(AppState())
    val state=mutable.asStateFlow()
    private var operation:Job?=null
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val catalog=runCatching { Json { ignoreUnknownKeys=true }.decodeFromString(CatalogFile.serializer(), application.assets.open("demo-catalog.json").bufferedReader().readText()) }.getOrNull()
            if(catalog!=null) mutable.update { it.copy(hospitals=catalog.hospitals,clinicians=catalog.clinicians,stays=catalog.stays,rates=catalog.rates) }
        }
        viewModelScope.launch {
            try {
                combine(app.preferences.flow,app.patients.patients) { preferences,patients -> preferences to patients }
                    .collect { (preferences,patients) -> mutable.update { it.copy(loaded=true,preferences=preferences,patients=patients,hasKey=app.vault.apiKey().isNotBlank()) } }
            } catch(e:CancellationException) { throw e }
            catch(e:Throwable) { Log.e("Nava","startup",e); mutable.update { it.copy(error="error_generic",errorDetail=e.describe()) } }
        }
    }
    private fun local(block:suspend ()->Unit) { viewModelScope.launch { try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { Log.e("Nava","action",e); mutable.update { it.copy(error="error_generic",errorDetail=e.describe()) } } } }
    private fun error(key:String?)=mutable.update { it.copy(error=key,errorDetail=if(key==null) null else it.errorDetail) }
    private fun Throwable.describe():String {
        val cause=cause
        return javaClass.simpleName+": "+(message ?: "")+(if(cause!=null) "\n"+cause.javaClass.simpleName+": "+(cause.message ?: "") else "")
    }
    fun clearError()=error(null)
    fun language(value:String) { cancel();local { app.preferences.language(value) } }
    fun welcome()=local { app.preferences.welcome() }
    fun theme(value:String)=local { app.preferences.theme(value) }
    fun lock(value:Boolean)=local { app.preferences.lock(value) }
    fun selectPatient(id:String) { if(!state.value.busy) mutable.update { it.copy(activeId=id,urgent=false,error=null,live="",exportedUri=null,exportedReportId=null) } }
    fun createPatient(name:String)=local { selectPatient(app.patients.create(name)) }
    fun consent(value:Boolean) {
        cancel(); val id=state.value.patient?.id ?: return
        local { app.patients.mutate(id) { it.copy(consent=if(value) Consent() else null) } }
    }
    fun pathway(value:Pathway)=edit { it.copy(pathway=value).withSession(it.session.copy(currentField=null)) }
    fun stage(value:String)=edit { it.copy(stage=value) }
    private fun edit(change:(Patient)->Patient) {
        if(state.value.busy) return
        val id=state.value.patient?.id ?: return
        local { app.patients.mutate(id,change) }
    }
    fun key(value:String) { cancel(); local { withContext(Dispatchers.IO) { app.vault.saveApiKey(value) };app.preferences.model(state.value.preferences.model,false);mutable.update { it.copy(hasKey=true) } } }
    fun removeKey() { cancel();local { app.vault.remove("api");app.preferences.model("",false);mutable.update { it.copy(hasKey=false,models=emptyList()) } } }
    fun model(value:String) { cancel();local { app.preferences.model(value,false) } }
    private fun network(block:suspend ()->Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true,error=null,live="") }
        operation=viewModelScope.launch {
            try { block() }
            catch(e:CancellationException) { throw e }
            catch(e:ProviderException) { error("error_"+if(e.category=="key") "auth" else e.category) }
            catch(e:PortalException) { error(e.key) }
            catch(_:Exception) { error("error_generic") }
            finally { mutable.update { it.copy(busy=false,live="") } }
        }
    }
    fun models()=network { val list=app.provider.models();mutable.update { it.copy(models=list) } }
    fun validate()=network { val model=state.value.preferences.model;app.provider.validateModel(model);app.preferences.model(model,true) }
    fun cancel() { operation?.cancel();app.provider.cancel();mutable.update { it.copy(live="") } }
    fun send(text:String?) {
        val snapshot=state.value;val p=snapshot.patient ?: return
        if(p.consent==null || !snapshot.preferences.modelValidated) { error("setup_required");return }
        network {
            val ctx=app.localized(snapshot.preferences.language)
            val urgent=app.interview.turn(p.id,snapshot.preferences.model,snapshot.preferences.language,text,ctx.label("urgent_body"),ctx.label("advice_boundary")) { live ->
                mutable.update { it.copy(live=live) }
            }
            mutable.update { it.copy(urgent=urgent) }
        }
    }
    fun showUrgent(value:Boolean)=mutable.update { it.copy(urgent=value) }
    fun correct(field:Field,value:String,unit:String,status:Status) = edit { MemoryReducer.correct(it,field,value,unit,status,state.value.preferences.language) }
    fun confirm(fact:Fact)=edit { it.copy(facts=it.facts.map { f -> if(f.id==fact.id && f.status!=Status.CONFLICTING) f.copy(reviewed=true) else f }) }
    fun finish()=edit { it.withSession(it.session.copy(finished=true)) }
    fun newSession()=edit { it.copy(sessions=it.sessions+Session()) }
    fun deletePatient() { cancel(); val id=state.value.patient?.id ?: return; local { app.patients.delete(id); withContext(Dispatchers.IO) { app.files.deletePatient(id) } } }
    fun deleteAll() { cancel();local { app.patients.deleteAll(); withContext(Dispatchers.IO) { app.files.deleteAll() } } }
    fun deleteSession(sessionId:String)=edit { p ->
        val messageIds=p.sessions.find { it.id==sessionId }?.messages?.map { it.id }?.toSet().orEmpty()
        val removed=p.facts.filter { it.sourceMessageId in messageIds }.map { it.id }.toMutableSet()
        var changed=true
        while(changed) { val before=removed.size;p.facts.filter { it.supersedes in removed }.forEach { removed+=it.id };changed=before!=removed.size }
        p.copy(sessions=p.sessions.filterNot { it.id==sessionId }.ifEmpty { listOf(Session()) },facts=p.facts.filterNot { it.id in removed },reports=emptyList())
    }
    fun report()=edit { p -> p.copy(reports=p.reports+ReportComposer.create(app.localized(state.value.preferences.language),p,state.value.preferences.language)) }
    fun addAttachment(uri:Uri, kind:AttachmentKind) {
        val patientId=state.value.patient?.id ?: return
        if(state.value.busy) return
        local {
            var savedId:String?=null
            try {
                val (attachment,bytes)=withContext(Dispatchers.IO) {
                    runCatching { app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    try { incoming(uri,kind) } finally { runCatching { app.contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                }
                savedId=attachment.id
                withContext(Dispatchers.IO) { app.files.save(patientId,attachment.id,bytes) }
                app.patients.mutate(patientId) { it.copy(attachments=it.attachments+attachment) }
            } catch(e:RejectedFile) { savedId?.let { id -> withContext(Dispatchers.IO) { app.files.delete(patientId,id) } }; error(e.key) }
            catch(e:Exception) { savedId?.let { id -> withContext(Dispatchers.IO) { app.files.delete(patientId,id) } }; throw e }
        }
    }
    private fun incoming(uri:Uri, kind:AttachmentKind):Pair<Attachment,ByteArray> {
        val resolver=app.contentResolver
        var name="file"
        resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor -> if(cursor.moveToFirst() && !cursor.isNull(0)) name=cursor.getString(0) }
        val mime=resolver.getType(uri) ?: "application/octet-stream"
        val bytes=resolver.openInputStream(uri)?.use { it.readNBytes(PageImages.MAX_BYTES+1) } ?: throw RejectedFile("file_unsupported")
        if(bytes.size>PageImages.MAX_BYTES) throw RejectedFile("file_too_large")
        if(bytes.isEmpty()) throw RejectedFile("file_unsupported")
        val type=mime.lowercase()
        if(type!="application/pdf" && !type.startsWith("image/")) throw RejectedFile("file_unsupported")
        return Attachment(kind=kind,name=name.replace('\n',' ').take(80).ifBlank { "file" },mime=mime) to bytes
    }
    fun readAttachment(attachmentId:String) {
        val snapshot=state.value; val patient=snapshot.patient ?: return
        if(patient.attachments.none { it.id==attachmentId }) return
        if(patient.consent==null || !snapshot.preferences.modelValidated) { error("setup_required"); return }
        network {
            try {
                val attachment=patient.attachments.first { it.id==attachmentId }
                val bytes=withContext(Dispatchers.IO) { app.files.load(patient.id,attachment.id) }
                val prepared=withContext(Dispatchers.IO) { PageImages.prepare(app,bytes,attachment.mime) }
                val raw=app.provider.readImages(snapshot.preferences.model,Prompts.DOCUMENT,"description_language: ${snapshot.preferences.language}",prepared.pages,Prompts.documentSchema)
                val parsed=Json { ignoreUnknownKeys=false }.parseToJsonElement(raw).jsonObject
                val ocr=parsed["ocr"]!!.jsonPrimitive.content.take(12000)
                val description=parsed["description"]!!.jsonPrimitive.content.take(600)
                app.patients.mutate(patient.id) { current -> current.copy(attachments=current.attachments.map { item ->
                    if(item.id==attachmentId) item.copy(ocr=ocr,description=description,pageCount=prepared.pageCount,pagesRead=prepared.pages.size) else item
                }) }
            } catch(e:RejectedFile) { error(e.key) }
        }
    }
    fun removeAttachment(attachmentId:String) {
        val patientId=state.value.patient?.id ?: return
        if(state.value.busy) return
        local {
            app.patients.mutate(patientId) { it.copy(attachments=it.attachments.filterNot { item -> item.id==attachmentId }) }
            withContext(Dispatchers.IO) { app.files.delete(patientId,attachmentId) }
        }
    }
    fun packet() {
        val snapshot=state.value; val patient=snapshot.patient ?: return
        if(patient.attachments.none { it.ocr.isNotBlank() }) { error("packet_empty"); return }
        edit { current -> current.copy(packets=current.packets+PacketComposer.create(app.localized(snapshot.preferences.language),current,snapshot.preferences.language)) }
    }
    fun export(uri:Uri,report:ReportSnapshot,pdf:Boolean)=local {
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(uri,"wt")!!.use { stream ->
                if(pdf) ReportComposer.writePdf(report,stream) else stream.write(report.structuredJson.toByteArray(Charsets.UTF_8))
            }
        }
        mutable.update { it.copy(error="export_success",exportedUri=uri,exportedReportId=report.id,exportedMime=if(pdf) "application/pdf" else "application/json") }
    }
    fun focusHospital(id:String)=mutable.update { it.copy(focusHospital=id) }
    fun browseCity(city:String)=mutable.update { it.copy(browseCity=city) }
    fun focusClinician(id:String)=mutable.update { it.copy(focusClinician=id) }
    fun focusStay(id:String)=mutable.update { it.copy(focusStay=id) }
    fun focusRequest(id:String)=mutable.update { it.copy(focusRequest=id) }
    fun toggleCompare(id:String)=mutable.update { it.copy(compareHospitals=(if(id in it.compareHospitals) it.compareHospitals-id else it.compareHospitals+id).takeLast(3)) }
    fun saveJourney(profile:JourneyProfile)=edit { it.copy(journey=profile.copy(updatedAt=now())) }
    fun saveTrip(plan:TripPlan)=edit { it.copy(trip=plan) }
    fun toggleHospital(id:String)=edit { it.copy(savedHospitals=if(id in it.savedHospitals) it.savedHospitals-id else it.savedHospitals+id) }
    fun toggleDoctor(id:String)=edit { it.copy(savedDoctors=if(id in it.savedDoctors) it.savedDoctors-id else it.savedDoctors+id) }
    fun saveServer(url:String,email:String) { val base=app.portal.base(url); if(url.isNotBlank() && base==null) { error("portal_url"); return }; local { app.preferences.server(base ?: "", email) } }
    fun portalLogin(email:String,password:String,register:Boolean)=network {
        val base=app.portal.base(state.value.preferences.serverUrl) ?: throw PortalException("portal_url")
        val session=withContext(Dispatchers.IO) { if(register) app.portal.register(base,email,password,state.value.patient?.nickname ?: email) else app.portal.login(base,email,password) }
        app.vault.savePortalToken(session.token); app.preferences.server(base,email); mutable.update { it.copy(portalRole=session.role) }
        pullDirectory(base,session.token)
    }
    fun portalLogout()=local { app.vault.clearPortalToken(); mutable.update { it.copy(portalRole="",directoryLive=false) } }
    fun refreshDirectory()=network {
        val base=app.portal.base(state.value.preferences.serverUrl) ?: throw PortalException("portal_url")
        val token=app.vault.portalToken().ifBlank { throw PortalException("portal_sign_in") }
        pullDirectory(base,token)
    }
    private suspend fun pullDirectory(base:String,token:String) {
        val hospitals=withContext(Dispatchers.IO) { app.portal.hospitals(base,token) }
        val clinicians=withContext(Dispatchers.IO) { app.portal.clinicians(base,token) }
        val stays=withContext(Dispatchers.IO) { app.portal.stays(base,token) }
        val rates=withContext(Dispatchers.IO) { app.portal.rates(base,token) }
        val remote=withContext(Dispatchers.IO) { app.portal.requests(base,token) }
        val patientId=state.value.patient?.id
        if(patientId!=null) {
            val previous=state.value.patient?.careRequests.orEmpty()
            val codes=RequestFlow.noticeCodes(previous,remote)
            app.patients.mutate(patientId) { current -> current.copy(careRequests=RequestFlow.merge(current.careRequests,remote), notices=current.notices+codes.map { CareNotice(code=it) }) }
            if(codes.isNotEmpty()) notifyUpdate()
        }
        mutable.update { it.copy(hospitals=hospitals,clinicians=clinicians,stays=stays,rates=rates,directoryLive=true) }
    }
    fun extractFindings(attachmentId:String) {
        val snapshot=state.value; val patient=snapshot.patient ?: return
        val attachment=patient.attachments.find { it.id==attachmentId } ?: return
        if(attachment.ocr.isBlank()) { error("read_first"); return }
        if(patient.consent==null || !snapshot.preferences.modelValidated) { error("setup_required"); return }
        network {
            val raw=app.provider.structured(snapshot.preferences.model,Prompts.FINDINGS,"description_language: ${snapshot.preferences.language}\n\nocr:\n${attachment.ocr}",Prompts.findingsSchema)
            val obj=Json { ignoreUnknownKeys=false }.parseToJsonElement(raw).jsonObject
            val language=obj["language"]?.jsonPrimitive?.content.orEmpty()
            val drafts=obj["findings"]!!.jsonArray.map { item ->
                val row=item.jsonObject
                FindingDraft(row["label"]!!.jsonPrimitive.content,row["value"]!!.jsonPrimitive.content,row["unit"]!!.jsonPrimitive.content,row["evidence"]!!.jsonPrimitive.content,language,row["confidence"]!!.jsonPrimitive.content)
            }
            val accepted=DocumentGate.conflicts(DocumentGate.accept(attachment.ocr,attachmentId,drafts,language),patient.currentFacts())
            app.patients.mutate(patient.id) { current -> current.copy(findings=current.findings.filterNot { it.attachmentId==attachmentId && it.verification==FindingCheck.UNVERIFIED }+accepted) }
        }
    }
    fun setFinding(id:String,check:FindingCheck)=edit { patient ->
        val findings=patient.findings.map { if(it.id==id) it.copy(verification=check) else it }
        patient.copy(findings=findings,careSummary=SummaryAssembler.build(patient.copy(findings=findings)))
    }
    fun buildSummary()=edit { it.copy(careSummary=SummaryAssembler.build(it)) }
    fun translateSummary() {
        val snapshot=state.value; val patient=snapshot.patient ?: return
        if(patient.consent==null || !snapshot.preferences.modelValidated) { error("setup_required"); return }
        val summary=patient.careSummary ?: SummaryAssembler.build(patient)
        network {
            val raw=app.provider.structured(snapshot.preferences.model,Prompts.TRANSLATE,summaryLines(summary),Prompts.translationSchema)
            val english=Json { ignoreUnknownKeys=false }.parseToJsonElement(raw).jsonObject["english"]!!.jsonPrimitive.content.take(8000)
            app.patients.mutate(patient.id) { it.copy(careSummary=(it.careSummary ?: summary).copy(englishNote=english,englishIsMachine=true)) }
        }
    }
    private fun summaryLines(summary:CareSummary)=buildString {
        appendLine(summary.concern)
        (summary.history+summary.treatments+summary.results+summary.conditions).forEach { appendLine(it.fieldId+": "+it.value) }
    }
    fun saveDraft(hospitalId:String,doctorId:String,note:String,documentIds:List<String>,includeSummary:Boolean,authorized:Boolean) {
        val existing=state.value.patient?.careRequests?.find { it.id==state.value.focusRequest && it.status==RequestStatus.DRAFT }
        val draftId=existing?.id ?: id()
        mutable.update { it.copy(focusRequest=draftId) }
        edit { patient ->
            val draft=CareRequest(id=draftId,pathway=patient.pathway,hospitalId=hospitalId,doctorId=doctorId,note=note,documentIds=documentIds,includeSummary=includeSummary,authorized=authorized,status=RequestStatus.DRAFT,createdAt=existing?.createdAt ?: now())
            patient.copy(careRequests=patient.careRequests.filterNot { it.id==draftId }+draft)
        }
    }
    fun authorizeDraft(value:Boolean)=edit { patient -> patient.copy(careRequests=patient.careRequests.map { if(it.id==state.value.focusRequest && it.status==RequestStatus.DRAFT) it.copy(authorized=value) else it }) }
    fun submitDraft(hospitalId:String,doctorId:String,note:String,documentIds:List<String>,includeSummary:Boolean,authorized:Boolean) {
        val snapshot=state.value; val patient=snapshot.patient ?: return
        if(!SharePolicy.submissionAllowed(authorized,hospitalId)) { error("share_authorize_required"); return }
        val base=app.portal.base(snapshot.preferences.serverUrl) ?: run { error("portal_url"); return }
        val token=app.vault.portalToken().ifBlank { error("portal_sign_in"); return }
        val existing=patient.careRequests.find { it.id==snapshot.focusRequest && it.status==RequestStatus.DRAFT }
        val draft=CareRequest(id=existing?.id ?: id(),pathway=patient.pathway,hospitalId=hospitalId,doctorId=doctorId,note=note,documentIds=documentIds,includeSummary=includeSummary,authorized=true,status=RequestStatus.DRAFT,createdAt=existing?.createdAt ?: now())
        network {
            val summary=if(includeSummary) SummaryAssembler.build(patient) else null
            val remote=withContext(Dispatchers.IO) { app.portal.submit(base,token,draft,summary) }
            documentIds.forEach { documentId ->
                val attachment=patient.attachments.find { it.id==documentId } ?: return@forEach
                val bytes=withContext(Dispatchers.IO) { app.files.load(patient.id,attachment.id) }
                withContext(Dispatchers.IO) { app.portal.upload(base,token,remote.id,documentId,attachment.name,attachment.mime.ifBlank { "application/octet-stream" },bytes) }
            }
            app.patients.mutate(patient.id) { current -> current.copy(careRequests=current.careRequests.filterNot { it.id==draft.id }+remote,careSummary=summary ?: current.careSummary,notices=current.notices+CareNotice(code="request_submitted")) }
            mutable.update { it.copy(focusRequest=remote.id) }
            notifyUpdate()
        }
    }
    fun careMessage(text:String) {
        val snapshot=state.value; val id=snapshot.focusRequest ?: return
        val base=app.portal.base(snapshot.preferences.serverUrl) ?: run { error("portal_url"); return }
        val token=app.vault.portalToken().ifBlank { error("portal_sign_in"); return }
        network {
            val remote=withContext(Dispatchers.IO) { app.portal.message(base,token,id,text) }
            val patientId=snapshot.patient?.id ?: return@network
            app.patients.mutate(patientId) { current -> current.copy(careRequests=current.careRequests.map { if(it.id==id) remote else it }) }
        }
    }
    fun careTransition(action:String) {
        val snapshot=state.value; val id=snapshot.focusRequest ?: return
        val base=app.portal.base(snapshot.preferences.serverUrl) ?: run { error("portal_url"); return }
        val token=app.vault.portalToken().ifBlank { error("portal_sign_in"); return }
        network {
            val remote=withContext(Dispatchers.IO) { app.portal.transition(base,token,id,action) }
            val patientId=snapshot.patient?.id ?: return@network
            app.patients.mutate(patientId) { current -> current.copy(careRequests=current.careRequests.map { if(it.id==id) remote else it },notices=current.notices+CareNotice(code="provider_response")) }
            notifyUpdate()
        }
    }
    fun askConcierge(question:String) {
        val snapshot=state.value; val patient=snapshot.patient ?: return
        if(question.isBlank()) return
        val prior=(patient.currentFacts()["fertility.ivf_count"]?.value?.toIntOrNull() ?: 0) > 0
        val brief=TourismOrchestrator.route(question,snapshot.hospitals,snapshot.clinicians,snapshot.stays,patient.careRequests,patient.pathway,patient.journey.destinationCity,snapshot.preferences.language.substringBefore('-'),snapshot.focusHospital ?: patient.savedHospitals.firstOrNull().orEmpty(),prior)
        val stored=brief.code+"\n"+brief.entries.joinToString("\n")
        edit { it.copy(conciergeLog=it.conciergeLog+Message(role="user",text=question.take(2000))+Message(role="assistant",text=stored)) }
    }
    fun readNotices()=edit { it.copy(notices=it.notices.map { notice -> notice.copy(read=true) }) }
    private fun notifyUpdate() {
        val context=getApplication<Application>()
        if(android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val manager=context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("nava.updates","Nava",android.app.NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility=android.app.Notification.VISIBILITY_SECRET })
        val text=context.localized(state.value.preferences.language).label("notice_generic")
        manager.notify(41,android.app.Notification.Builder(context,"nava.updates").setSmallIcon(com.aminfathi.nava.R.drawable.ic_nava).setContentTitle("Nava").setContentText(text).setVisibility(android.app.Notification.VISIBILITY_SECRET).build())
    }
}
