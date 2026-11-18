@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.aminfathi.nava.ui

import android.content.Intent
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.aminfathi.nava.domain.*
import java.text.NumberFormat

@Composable private fun tr(key:String)=LocalLocalized.current.label(key)
@Composable fun NavaApp(vm:AppViewModel,locked:Boolean,unlock:()->Unit,setLock:(Boolean)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val base=LocalContext.current
    val context=remember(state.preferences.language) { base.localized(state.preferences.language) }
    val rtl=languages.find { it.tag==state.preferences.language }?.rtl==true
    CompositionLocalProvider(LocalLocalized provides context, LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        NavaTheme(state.preferences.theme) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                when {
                    !state.loaded->Box(Modifier.fillMaxSize().padding(28.dp),contentAlignment=Alignment.Center) {
                        if(state.error!=null) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(tr(state.error!!))
                            state.errorDetail?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else CircularProgressIndicator()
                    }
                    state.preferences.language.isEmpty()->LanguageScreen(vm::language)
                    locked->Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center) { Brand();Spacer(Modifier.height(32.dp));Action(tr("unlock"),onClick=unlock) }
                    !state.preferences.welcomed->Welcome(vm::welcome)
                    else->MainShell(vm,state,setLock)
                }
            }
        }
    }
}
@Composable private fun Brand() {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(38.dp)) {
            Icon(Icons.Outlined.Spa,null,Modifier.padding(8.dp),tint=MaterialTheme.colorScheme.onPrimary)
        }
        Text("NAVA",letterSpacing=4.sp,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
    }
}
@Composable private fun LanguageScreen(select:(String)->Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(30.dp));Brand();Spacer(Modifier.height(28.dp));Text(tr("choose_language"),style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(18.dp)) }
        items(languages) { language -> Surface(onClick={select(language.tag)},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(language.nativeName,style=MaterialTheme.typography.titleLarge);Icon(Icons.Outlined.ArrowForward,null) }
        } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
@Composable private fun Welcome(continueAction:()->Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Brand();Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth().height(175.dp).clip(RoundedCornerShape(80.dp,24.dp,80.dp,24.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.colorScheme.primaryContainer))),contentAlignment=Alignment.Center) {
            Icon(Icons.Outlined.Spa,null,Modifier.size(98.dp),tint=MaterialTheme.colorScheme.primary)
        }
        Text(tr("welcome_title"),style=MaterialTheme.typography.headlineLarge)
        Text(tr("welcome_body"),style=MaterialTheme.typography.bodyLarge)
        Text(tr("medical_limit"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Action(tr("continue_action"),onClick=continueAction)
    }
}
private data class Tab(val key:String,val icon:ImageVector)
@Composable private fun MainShell(vm:AppViewModel,s:AppState,setLock:(Boolean)->Unit) {
    val nav=rememberNavController()
    val tabs=listOf(Tab("home",Icons.Outlined.Home),Tab("interview",Icons.Outlined.Forum),Tab("record",Icons.Outlined.Description),Tab("settings",Icons.Outlined.Tune))
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "home"
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        topBar={TopAppBar(title={Brand()},actions={IconButton(onClick={vm.showUrgent(true)}){Icon(Icons.Outlined.HealthAndSafety,tr("symptom_help"))};Icon(Icons.Outlined.Lock,tr("privacy_local"),Modifier.padding(14.dp).size(19.dp),tint=MaterialTheme.colorScheme.primary)},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))},
        bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface) { tabs.forEach { tab ->
            NavigationBarItem(selected=route==tab.key,onClick={nav.navigate(tab.key) { popUpTo("home") { saveState=true };launchSingleTop=true;restoreState=true }},icon={Icon(tab.icon,tr(tab.key))},label={Text(tr(tab.key),maxLines=2)})
        } }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            s.error?.let { key -> Surface(color=MaterialTheme.colorScheme.errorContainer,onClick=vm::clearError,modifier=Modifier.fillMaxWidth()) { Text(tr(key),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer) } }
            NavHost(navController=nav,startDestination="home",modifier=Modifier.weight(1f)) {
                composable("home") { Overview(vm,s) { nav.navigate(it) } }
                composable("interview") { Interview(vm,s) { nav.navigate(it) } }
                composable("record") { Record(vm,s) }
                composable("settings") { Settings(vm,s,setLock) }
            }
        }
    }
    if(s.urgent) AlertDialog(onDismissRequest={vm.showUrgent(false)},icon={Icon(Icons.Outlined.HealthAndSafety,null)},title={Text(tr("urgent_title"))},text={Text(tr("urgent_body"))},confirmButton={TextButton(onClick={vm.showUrgent(false)}){Text(tr("continue_action"))}})
}
@Composable private fun Page(content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
@Composable private fun Overview(vm:AppViewModel,s:AppState,navigate:(String)->Unit) {
    var add by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Page {
        Text(tr("welcome_title"),style=MaterialTheme.typography.headlineLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            s.patients.forEach { patient -> FilterChip(selected=patient.id==s.patient?.id,onClick={vm.selectPatient(patient.id)},enabled=!s.busy,label={Text(patient.nickname)}) }
            AssistChip(onClick={add=true},enabled=!s.busy,label={Text(tr("new_profile"))},leadingIcon={Icon(Icons.Outlined.Add,null,Modifier.size(18.dp))})
        }
        val p=s.patient
        if(p!=null) {
            ProgressCard(p)
            Panel {
                Text(tr("pathway"),style=MaterialTheme.typography.titleLarge)
                Pathway.entries.forEach { path ->
                    Surface(onClick={vm.pathway(path)},enabled=!s.busy,shape=MaterialTheme.shapes.medium,color=if(p.pathway==path) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Icon(if(path==Pathway.IVF) Icons.Outlined.Spa else Icons.Outlined.FavoriteBorder,null)
                            Column(Modifier.weight(1f)) { Text(path.name,style=MaterialTheme.typography.titleLarge);Text(tr(if(path==Pathway.IVF) "ivf_description" else "iui_description"),style=MaterialTheme.typography.bodyMedium) }
                            if(p.pathway==path) Icon(Icons.Outlined.CheckCircle,null)
                        }
                    }
                }
                Text(tr("stage"),style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("preparing","undergoing","previous","consultation").forEach { stage -> FilterChip(selected=p.stage==stage,onClick={vm.stage(stage)},enabled=!s.busy,label={Text(tr(stage))}) } }
            }
            Action(tr(if(p.session.finished) "new_session" else "interview"),!s.busy) { if(p.session.finished) vm.newSession();navigate("interview") }
            OutlinedButton(onClick={navigate("record")},modifier=Modifier.fillMaxWidth()) { Text(tr("record")) }
        } else Panel { Text(tr("empty_record"),style=MaterialTheme.typography.titleLarge);Action(tr("new_profile")) { add=true } }
        Text(tr("privacy_local"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(add) AlertDialog(onDismissRequest={add=false},title={Text(tr("new_profile"))},text={OutlinedTextField(value=name,onValueChange={name=it},label={Text(tr("nickname"))},singleLine=true)},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={vm.createPatient(name);name="";add=false}){Text(tr("create"))}},dismissButton={TextButton(onClick={add=false}){Text(tr("cancel"))}})
}
@Composable private fun ProgressCard(p:Patient) {
    val (count,total)=Coverage.addressed(p)
    val fraction=if(total==0) 0f else count.toFloat()/total
    val locales=LocalLocalized.current.resources.configuration.locales
    val format=NumberFormat.getIntegerInstance(if(locales.isEmpty) java.util.Locale.getDefault() else locales[0])
    Surface(color=MaterialTheme.colorScheme.primary,shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(p.nickname,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onPrimary)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(tr("progress"),color=MaterialTheme.colorScheme.onPrimary);Text(format.format(count)+" / "+format.format(total),color=MaterialTheme.colorScheme.onPrimary) }
            LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(5.dp),color=MaterialTheme.colorScheme.secondaryContainer,trackColor=MaterialTheme.colorScheme.onPrimary.copy(alpha=.2f))
        }
    }
}
@Composable private fun ConsentCard(vm:AppViewModel) {
    Panel { Icon(Icons.Outlined.VerifiedUser,null,tint=MaterialTheme.colorScheme.primary);Text(tr("consent_title"),style=MaterialTheme.typography.headlineMedium);Text(tr("consent_body"));Action(tr("consent_accept")){vm.consent(true)} }
}
@Composable private fun Interview(vm:AppViewModel,s:AppState,navigate:(String)->Unit) {
    val p=s.patient
    if(p==null) { Page { Text(tr("empty_record"));Action(tr("new_profile")){navigate("home")} };return }
    if(p.consent==null) { Page { ConsentCard(vm) };return }
    if(!s.preferences.modelValidated) { Page { Text(tr("setup_required"));Action(tr("settings")){navigate("settings")} };return }
    var input by remember(p.id,p.session.id) { mutableStateOf("") }
    val list=rememberLazyListState()
    val pending=p.session.messages.any { it.role=="user" && it.state!=MessageState.COMMITTED }
    val unknown=tr("unknown");val skip=tr("skip")
    val correction=tr("correction")
    var editing by remember(p.id) { mutableStateOf<String?>(null) }
    val initialIds=remember(p.session.id) { p.session.messages.map { it.id }.toSet() }
    val shrunk=remember(p.session.id) { mutableStateListOf<String>() }
    val locales=LocalLocalized.current.resources.configuration.locales
    val numbers=NumberFormat.getIntegerInstance(if(locales.isEmpty) java.util.Locale.getDefault() else locales[0])
    val left=Coverage.remaining(p)
    LaunchedEffect(p.session.messages.size,s.live) { if(list.layoutInfo.totalItemsCount>0) list.animateScrollToItem(list.layoutInfo.totalItemsCount-1) }
    if(editing!=null) AlertDialog(onDismissRequest={editing=null},title={Text(tr("edit_answer"))},text={OutlinedTextField(value=editing.orEmpty(),onValueChange={editing=it},maxLines=8)},confirmButton={TextButton(onClick={vm.send(correction+"\n"+editing.orEmpty());editing=null},enabled=!editing.isNullOrBlank()){Text(tr("save"))}},dismissButton={TextButton(onClick={editing=null}){Text(tr("cancel"))}})
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nickname+" · "+p.pathway.name,style=MaterialTheme.typography.titleMedium)
                Text(tr("remaining_questions")+" · "+numbers.format(left),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick={vm.showUrgent(true)}) { Icon(Icons.Outlined.HealthAndSafety,tr("symptom_help")) }
        }
        LazyColumn(state=list,modifier=Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Bubble(tr("intro_chat"),false) }
            items(p.session.messages,key={it.id}) { message ->
                val fresh=message.role=="user" && message.id !in initialIds && message.id !in shrunk
                if(fresh) LaunchedEffect(message.id) { delay(2500); if(message.id !in shrunk) shrunk.add(message.id) }
                Column {
                    Bubble(message.text,message.role=="user",message.state!=MessageState.COMMITTED && !fresh,fresh,if(message.role=="user") messageClock(message.createdAt) else "")
                    if(message.role=="user" && message.state==MessageState.COMMITTED && !p.session.finished && !pending)
                        TextButton(onClick={editing=message.text},enabled=!s.busy,modifier=Modifier.align(Alignment.End)){Text(tr("edit_answer"),style=MaterialTheme.typography.labelSmall)}
                }
            }
            if(s.busy) item { if(s.live.isNotBlank()) Column { Bubble(s.live,false);Text(tr("api_stream_notice"),style=MaterialTheme.typography.bodySmall) } else Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp);Text(tr("thinking"),style=MaterialTheme.typography.bodySmall) } }
        }
        if(s.busy) TextButton(onClick=vm::cancel,modifier=Modifier.align(Alignment.CenterHorizontally)) { Text(tr("cancel")) }
        else if(pending) Column(Modifier.padding(horizontal=20.dp)) { Action(tr("retry")) { vm.send(null) } }
        else if(p.session.finished) Column(Modifier.padding(20.dp)) { Action(tr("new_session")) { vm.newSession() } }
        else {
            FlowRow(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick={vm.send(unknown)},label={Text(unknown)})
                AssistChip(onClick={vm.send(skip)},label={Text(skip)})
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=input,onValueChange={if(it.length<=12000) input=it},placeholder={Text(tr("message_hint"))},modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp),maxLines=5)
                FilledIconButton(onClick={vm.send(input);input=""},enabled=input.isNotBlank(),modifier=Modifier.size(52.dp)) { Icon(Icons.Outlined.ArrowUpward,tr("send")) }
            }
        }
    }
}
private fun messageClock(iso:String)=runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("")
@Composable private fun Bubble(text:String,user:Boolean,pending:Boolean=false,large:Boolean=false,time:String="") {
    val wide=large && user
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user) Arrangement.End else Arrangement.Start) {
        Surface(shape=RoundedCornerShape(if(wide) 28.dp else 22.dp,if(wide) 28.dp else 22.dp,if(user) 6.dp else 22.dp,if(user) 22.dp else 6.dp),
            color=if(user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            modifier=if(wide) Modifier.fillMaxWidth(0.92f) else Modifier.widthIn(max=if(user) 240.dp else 330.dp)) {
            Column(Modifier.padding(if(wide) 22.dp else if(user) 12.dp else 17.dp)) {
                Text(text,color=if(user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,style=if(wide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                if(wide) Row(Modifier.padding(top=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Schedule,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onPrimary.copy(alpha=.75f))
                    Text(time,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.75f),style=MaterialTheme.typography.labelLarge)
                } else if(pending) Icon(Icons.Outlined.Schedule,tr("retry"),Modifier.padding(top=6.dp).size(14.dp),tint=MaterialTheme.colorScheme.onPrimary.copy(alpha=.6f))
            }
        }
    }
}
@Composable private fun Record(vm:AppViewModel,s:AppState) {
    val p=s.patient ?: run { Page { Text(tr("empty_record")) };return }
    var edit by remember(p.id) { mutableStateOf<Field?>(null) }
    var expanded by remember(p.id) { mutableStateOf<String?>(null) }
    var viewSession by remember(p.id) { mutableStateOf<Session?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var exportFormat by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<ReportSnapshot?>(null) }
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if(uri!=null) exporting?.let { vm.export(uri,it,true) };exporting=null }
    val json=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) exporting?.let { vm.export(uri,it,false) };exporting=null }
    val activity=LocalContext.current
    Page {
        Text(tr("record"),style=MaterialTheme.typography.headlineLarge);ProgressCard(p)
        Text(tr("review"),style=MaterialTheme.typography.titleLarge)
        Specification.fields(p).groupBy { it.domain }.forEach { (domain,fields) ->
            Panel {
                TextButton(onClick={expanded=if(expanded==domain) null else domain}) { Text(tr(domain),style=MaterialTheme.typography.titleLarge) }
                AnimatedVisibility(expanded==domain) {
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        fields.forEach { field ->
                            val fact=p.currentFacts()[field.id]
                            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled=!s.busy) { edit=field }.padding(8.dp)) {
                                Text(LocalLocalized.current.fieldLabel(field),fontWeight=FontWeight.SemiBold)
                                Text(fact?.value?.ifBlank { tr(fact.status.name) } ?: tr("not_provided"),style=MaterialTheme.typography.bodyMedium)
                                if(fact!=null) {
                                    Text(tr(fact.status.name)+" · "+tr(if(fact.reviewed) "reviewed" else "unreviewed"),color=if(fact.status==Status.CONFLICTING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                                    if(!fact.reviewed && fact.status!=Status.CONFLICTING) TextButton(onClick={vm.confirm(fact)},enabled=!s.busy) { Text(tr("confirm_fact")) }
                                }
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        Panel {
            Text(tr("documents_title"),style=MaterialTheme.typography.headlineMedium)
            Text(tr("documents_notice"),style=MaterialTheme.typography.bodyMedium)
            val pickDocument=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.addAttachment(uri,AttachmentKind.DOCUMENT) }
            val pickLab=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.addAttachment(uri,AttachmentKind.LAB) }
            OutlinedButton(onClick={pickDocument.launch(arrayOf("image/*","application/pdf"))},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) { Text(tr("upload_document")) }
            OutlinedButton(onClick={pickLab.launch(arrayOf("image/*","application/pdf"))},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) { Text(tr("upload_lab")) }
            p.attachments.forEach { item ->
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(item.name,fontWeight=FontWeight.SemiBold)
                    Text(tr(if(item.kind==AttachmentKind.DOCUMENT) "attachment_document" else "attachment_lab"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    if(item.description.isNotBlank()) Text(item.description,style=MaterialTheme.typography.bodyMedium)
                    if(item.ocr.isNotBlank()) Text(item.ocr,style=MaterialTheme.typography.bodySmall,maxLines=8)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={vm.readAttachment(item.id)},enabled=!s.busy) { Text(tr(if(item.ocr.isBlank()) "read_file" else "read_again")) }
                        TextButton(onClick={vm.removeAttachment(item.id)},enabled=!s.busy) { Text(tr("remove_file")) }
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            Action(tr("make_packet"),!s.busy){vm.packet()}
            p.packets.lastOrNull { it.pathway==p.pathway.name }?.let { packet ->
                Text(packet.text,style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={exporting=packet;exportFormat="pdf"},modifier=Modifier.fillMaxWidth()) { Text(tr("export_pdf")) }
                OutlinedButton(onClick={exporting=packet;exportFormat="json"},modifier=Modifier.fillMaxWidth()) { Text(tr("export_json")) }
            }
        }
        Panel {
            Text(tr("report"),style=MaterialTheme.typography.headlineMedium)
            Text(tr("report_disclaimer"),style=MaterialTheme.typography.bodyMedium)
            Action(tr("make_report"),!s.busy){vm.report()}
            p.reports.lastOrNull()?.let { report ->
                Text(report.text,style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={exporting=report;exportFormat="pdf"},modifier=Modifier.fillMaxWidth()) { Text(tr("export_pdf")) }
                OutlinedButton(onClick={exporting=report;exportFormat="json"},modifier=Modifier.fillMaxWidth()) { Text(tr("export_json")) }
            }
            s.exportedUri?.takeIf { (p.reports+p.packets).any { report -> report.id==s.exportedReportId } }?.let { uri ->
                OutlinedButton(onClick={
                    val send=Intent(Intent.ACTION_SEND).apply { type=s.exportedMime;putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("Nava intake",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    activity.startActivity(Intent.createChooser(send,activity.localized(s.preferences.language).label("share")))
                },modifier=Modifier.fillMaxWidth()) {Text(tr("share"))}
            }
            OutlinedButton(onClick=vm::finish,enabled=!s.busy && p.sessions.lastOrNull()?.finished!=true,modifier=Modifier.fillMaxWidth()) { Text(tr("finish")) }
        }
        Panel {
            Text(tr("history"),style=MaterialTheme.typography.titleLarge)
            p.sessions.reversed().forEach { session ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={viewSession=session},modifier=Modifier.weight(1f)) { Text(session.createdAt.substringBefore('T')) }
                    IconButton(onClick={deleting=session.id},enabled=!s.busy) { Icon(Icons.Outlined.DeleteOutline,tr("delete_conversation")) }
                }
            }
        }
    }
    edit?.let { field -> EditFactDialog(p,field,{edit=null}) { value,unit,status -> vm.correct(field,value,unit,status);edit=null } }
    if(viewSession!=null) AlertDialog(onDismissRequest={viewSession=null},title={Text(tr("history"))},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){viewSession!!.messages.forEach { Bubble(it.text,it.role=="user") }}},confirmButton={TextButton(onClick={viewSession=null}) {Text(tr("continue_action"))}})
    if(deleting!=null) DeleteDialog({deleting=null}) {vm.deleteSession(deleting!!);deleting=null}
    if(exportFormat!=null) AlertDialog(onDismissRequest={exportFormat=null;exporting=null},title={Text(tr("report"))},text={Text(tr("export_notice"))},confirmButton={TextButton(onClick={val f=exportFormat;exportFormat=null;if(f=="pdf") pdf.launch("Nava-intake.pdf") else json.launch("Nava-intake.json")}){Text(tr("continue_action"))}},dismissButton={TextButton(onClick={exportFormat=null;exporting=null}){Text(tr("cancel"))}})
}
@Composable private fun EditFactDialog(p:Patient,field:Field,close:()->Unit,save:(String,String,Status)->Unit) {
    val fact=p.currentFacts()[field.id]
    var value by remember { mutableStateOf(fact?.value.orEmpty()) }
    var unit by remember { mutableStateOf(fact?.unit.orEmpty()) }
    var status by remember { mutableStateOf(if(fact?.status==Status.CONFLICTING) Status.ANSWERED else fact?.status ?: Status.ANSWERED) }
    AlertDialog(onDismissRequest=close,title={Text(LocalLocalized.current.fieldLabel(field))},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(fact?.status==Status.CONFLICTING) { Text(tr("CONFLICTING"));p.facts.find { it.id==fact.supersedes }?.let { Text(tr("previous")+": "+it.value+" "+it.unit) } }
        OutlinedTextField(value=value,onValueChange={value=it},label={Text(tr("value"))})
        if(field.units.isNotEmpty()) OutlinedTextField(value=unit,onValueChange={unit=it},label={Text(tr("unit"))},supportingText={Text(field.units.joinToString(" · "))})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(Status.ANSWERED,Status.UNKNOWN,Status.SKIPPED,Status.NOT_APPLICABLE).forEach { option -> FilterChip(selected=status==option,onClick={status=option},label={Text(tr(option.name))}) } }
    }},confirmButton={TextButton(onClick={save(value,unit,status)}){Text(tr("save"))}},dismissButton={TextButton(onClick=close){Text(tr("cancel"))}})
}
@Composable private fun Settings(vm:AppViewModel,s:AppState,setLock:(Boolean)->Unit) {
    var key by remember { mutableStateOf("") }
    var deletion by remember { mutableStateOf<String?>(null) }
    var modelMenu by remember { mutableStateOf(false) }
    Page {
        Text(tr("settings"),style=MaterialTheme.typography.headlineLarge)
        Panel {
            Text("OpenAI",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(value=key,onValueChange={key=it},label={Text(tr("api_key"))},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Action(tr("save_key"),key.length>=10 && !s.busy){vm.key(key);key=""}
            if(s.hasKey) TextButton(onClick=vm::removeKey,enabled=!s.busy){Text(tr("remove_key"))}
            OutlinedButton(onClick=vm::models,enabled=s.hasKey && !s.busy,modifier=Modifier.fillMaxWidth()){Text(tr("load_models"))}
            OutlinedTextField(value=s.preferences.model,onValueChange=vm::model,label={Text(tr("model"))},singleLine=true,enabled=!s.busy,modifier=Modifier.fillMaxWidth())
            if(s.models.isNotEmpty()) {
                TextButton(onClick={modelMenu=true},enabled=!s.busy) { Text(tr("model")) }
                if(modelMenu) AlertDialog(onDismissRequest={modelMenu=false},title={Text(tr("model"))},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())){s.models.forEach { model -> TextButton(onClick={vm.model(model);modelMenu=false}){Text(model)} }}},confirmButton={TextButton(onClick={modelMenu=false}){Text(tr("cancel"))}})
            }
            Action(tr(if(s.preferences.modelValidated) "connected" else "validate"),s.hasKey && s.preferences.model.isNotBlank() && !s.busy){vm.validate()}
            if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(tr("api_cost"),style=MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text(tr("language"),style=MaterialTheme.typography.titleLarge)
            languages.forEach { language ->
                val selected=language.tag==s.preferences.language
                Surface(onClick={if(!selected && !s.busy) vm.language(language.tag)},enabled=!s.busy,shape=MaterialTheme.shapes.medium,color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(language.nativeName,style=MaterialTheme.typography.titleMedium)
                        if(selected) Icon(Icons.Outlined.CheckCircle,null)
                    }
                }
            }
            Text(tr("theme"),style=MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("system","light","dark").forEach { theme -> FilterChip(selected=s.preferences.theme==theme,onClick={vm.theme(theme)},label={Text(tr(theme))}) }}
            Row(verticalAlignment=Alignment.CenterVertically){Text(tr("app_lock"),Modifier.weight(1f));Switch(checked=s.preferences.lock,onCheckedChange=setLock)}
        }
        Panel {
            Text(tr("consent_title"),style=MaterialTheme.typography.titleLarge)
            Text(tr("consent_body"),style=MaterialTheme.typography.bodyMedium)
            if(s.patient?.consent!=null) OutlinedButton(onClick={vm.consent(false)},modifier=Modifier.fillMaxWidth()){Text(tr("revoke"))}
            TextButton(onClick={deletion="patient"},enabled=s.patient!=null && !s.busy){Text(tr("delete_patient"),color=MaterialTheme.colorScheme.error)}
            TextButton(onClick={deletion="all"},enabled=s.patients.isNotEmpty() && !s.busy){Text(tr("delete_all"),color=MaterialTheme.colorScheme.error)}
        }
        Text(tr("medical_limit"),style=MaterialTheme.typography.bodySmall)
        Text("Amin Fathi · Nava 0.1.0",style=MaterialTheme.typography.bodySmall)
    }
    if(deletion!=null) DeleteDialog({deletion=null}) {if(deletion=="all") vm.deleteAll() else vm.deletePatient();deletion=null}
}
@Composable private fun DeleteDialog(close:()->Unit,confirm:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(tr("delete_confirm"))},text={Text(tr("delete_warning"))},confirmButton={TextButton(onClick=confirm){Text(tr("delete_confirm"),color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=close){Text(tr("cancel"))}})
}
