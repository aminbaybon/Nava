package com.aminfathi.nava.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ExportSpecification {
    @JvmStatic fun main(args:Array<String>) {
        val fixture=Patient(nickname="SPECIFICATION TEMPLATE",facts=listOf(
            Fact(fieldId="fertility.ivf_count",value="1",sourceMessageId="template",language="en"),
            Fact(fieldId="fertility.iui_count",value="1",sourceMessageId="template",language="en"),
            Fact(fieldId="obstetric.count",value="1",sourceMessageId="template",language="en")))
        val fields=(Specification.fields(fixture)+Specification.fields(fixture.copy(pathway=Pathway.IUI))).distinctBy {it.id}
        File(args[0]).writeText(Json {prettyPrint=true;encodeDefaults=true}.encodeToString(fields))
        println("Exported ${fields.size} base and repeatable-template fields")
    }
}
