package com.aminfathi.nava.ai

import com.aminfathi.nava.data.PatientRepository
import com.aminfathi.nava.domain.*
import com.aminfathi.nava.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class InterviewOrchestrator(private val repository:PatientRepository,private val provider:OpenAIProvider) {
    private val mutex=Mutex()
    suspend fun turn(patientId:String,model:String,language:String,text:String?,urgentText:String,adviceText:String,onText:(String)->Unit):Boolean = mutex.withLock {
        var patient=repository.get(patientId)
        check(patient.consent!=null && !patient.session.finished)
        val pending=patient.session.messages.lastOrNull { it.role=="user" && it.state!=MessageState.COMMITTED }
        val message=pending ?: Message(role="user",text=text?.trim()?.take(12000) ?: error("Missing response"),state=MessageState.PENDING)
        require(message.text.isNotBlank())
        if(pending==null) patient=repository.mutate(patientId) { p ->
            check(p.consent!=null && !p.session.finished)
            p.withSession(p.session.copy(messages=p.session.messages+message))
        }
        val sessionId=patient.session.id
        val revision=patient.revision
        suspend fun authorize() {
            val current=repository.get(patientId)
            check(current.consent!=null && current.session.id==sessionId && current.revision==revision)
        }
        try {
            val emergency=SafetyGuard.needsAttention(message.text)
            val extraction=if(emergency) Extraction(emptyList(),true,false) else {
                val raw=provider.structured(model,Prompts.EXTRACTOR,Prompts.context(patient,language,message,extraction=true),Prompts.extractionSchema,::authorize)
                runCatching { Json.decodeFromString<Extraction>(raw) }.getOrElse { throw ProviderException("format") }
            }
            val proposed=MemoryReducer.apply(patient,extraction.facts,message,language)
            val urgent=emergency || extraction.urgent
            val askedAfterReply=if(urgent) proposed.session.asked else (proposed.session.asked+listOfNotNull(patient.session.currentField)).distinct()
            val cursor=proposed.withSession(proposed.session.copy(asked=askedAfterReply))
            val next=if(urgent) null else Coverage.next(cursor)
            val asking=!urgent && !extraction.adviceRequested
            val reply=if(urgent) urgentText else if(extraction.adviceRequested) adviceText else provider.stream(model,Prompts.INTERVIEWER,
                Prompts.context(cursor,language,message,next,extraction.adviceRequested),::authorize,onText)
            if(!urgent && !SafetyGuard.allowsReply(reply)) throw ProviderException("format")
            authorize()
            val asked=if(asking) (askedAfterReply+listOfNotNull(next?.id)).distinct() else askedAfterReply
            val field=if(asking) next?.id else patient.session.currentField
            repository.mutate(patientId) { current ->
                check(current.revision==revision && current.consent!=null)
                cursor.withSession(cursor.session.copy(currentField=field,asked=asked,
                    messages=cursor.session.messages.map { if(it.id==message.id) it.copy(state=MessageState.COMMITTED) else it } + Message(role="assistant",text=reply)))
            }
            urgent
        } catch(e:Exception) {
            withContext(NonCancellable) {
                runCatching { repository.mutate(patientId) { current ->
                    if(current.session.id!=sessionId) current else current.withSession(current.session.copy(messages=current.session.messages.map {
                        if(it.id==message.id && it.state==MessageState.PENDING) it.copy(state=MessageState.FAILED) else it
                    }))
                } }
            }
            throw e
        }
    }
}
