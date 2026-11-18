package com.aminfathi.nava.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.aminfathi.nava.domain.*
import com.aminfathi.nava.ui.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportComposer {
    private val json=Json { prettyPrint=true; encodeDefaults=true }
    fun create(context:Context,patient:Patient,language:String):ReportSnapshot {
        val facts=patient.currentFacts()
        val at=now()
        val text=buildString {
            appendLine("NAVA  /  "+context.label("report"))
            appendLine(patient.nickname+" • "+patient.pathway.name)
            appendLine(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.forLanguageTag(language)).withZone(ZoneId.systemDefault()).format(Instant.parse(at)))
            appendLine(context.label("report_disclaimer"))
            appendLine(context.label(if(Coverage.complete(patient)) "complete" else "incomplete"))
            appendLine("\n"+context.label("stage")+": "+context.label(patient.stage))
            Specification.fields(patient).groupBy { it.domain }.forEach { (domain,fields) ->
                appendLine("\n"+context.label(domain))
                fields.forEach { field ->
                    val fact=facts[field.id]
                    val applicable=Coverage.applicable(field,facts)
                    appendLine(context.fieldLabel(field)+": "+when {
                        fact!=null-> listOf(fact.value,fact.unit,fact.date).filter { it.isNotBlank() }.joinToString(" ") + " ["+context.label(fact.status.name)+"; "+context.label(if(fact.reviewed) "reviewed" else "unreviewed")+"]"
                        !applicable && field.dependsOn != null && facts[field.dependsOn]?.status == Status.ANSWERED && facts[field.dependsOn]?.value != field.expected ->context.label("NOT_APPLICABLE")
                        else->context.label("not_provided")
                    })
                    if(fact?.status==Status.CONFLICTING) {
                        patient.facts.find { it.id==fact.supersedes }?.let { appendLine("↳ "+context.label("previous")+": "+it.value+" "+it.unit) }
                    }
                }
            }
        }
        val structured=buildJsonObject {
            put("schema_version",Specification.VERSION);put("generated_at",at);put("language",language)
            put("patient_id",patient.id);put("nickname",patient.nickname);put("pathway",patient.pathway.name)
            put("status",if(Coverage.complete(patient)) "required_topics_addressed" else "incomplete")
            put("clinical_review_required",true);put("facts",json.encodeToJsonElement(facts.values.toList()))
            put("revision_history",json.encodeToJsonElement(patient.facts))
            put("missing_or_uncertain",json.encodeToJsonElement(Coverage.unresolved(patient).map { it.id }))
        }
        return ReportSnapshot(createdAt=at,language=language,text=text,structuredJson=json.encodeToString(structured))
    }
    /** Streams straight to a user-selected SAF destination; no plaintext cache. */
    fun writePdf(report:ReportSnapshot,output:OutputStream) {
        val document=PdfDocument()
        try {
            val rtl=languages.find { it.tag==report.language }?.rtl==true
            val paint=TextPaint().apply { color=Color.rgb(31,64,60);textSize=11f;typeface=Typeface.create("sans-serif",Typeface.NORMAL) }
            val layout=StaticLayout.Builder.obtain(report.text,0,report.text.length,paint,499)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if(rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setLineSpacing(4f,1f).setIncludePad(true).build()
            var firstLine=0;var pageNumber=1
            while(firstLine<layout.lineCount) {
                val top=layout.getLineTop(firstLine);var endLine=firstLine+1
                while(endLine<layout.lineCount && layout.getLineBottom(endLine)-top<=720) endLine++
                val bottom=layout.getLineBottom(endLine-1)
                val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.save();page.canvas.translate(48f,52f-top)
                page.canvas.clipRect(0f,top.toFloat(),499f,bottom.toFloat());layout.draw(page.canvas);page.canvas.restore()
                page.canvas.drawText("NAVA  •  $pageNumber",48f,810f,paint)
                document.finishPage(page);firstLine=endLine;pageNumber++
            }
            document.writeTo(output)
        } finally {document.close()}
    }
}
