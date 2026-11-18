package com.aminfathi.nava

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aminfathi.nava.data.*
import com.aminfathi.nava.domain.*
import com.aminfathi.nava.security.Vault
import com.aminfathi.nava.reports.ReportComposer
import com.aminfathi.nava.ui.localized
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class) class SecurityAndReportTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun encryptedDatabaseDoesNotExposePatientContent()=runBlocking {
        val vault=Vault(context);val db=ClinicalDatabase.open(context,vault);val repo=PatientRepository(db)
        val id=repo.create("SYNTHETIC_SECRET_MARKER")
        assertEquals("SYNTHETIC_SECRET_MARKER",repo.get(id).nickname)
        db.close()
        val bytes=context.getDatabasePath("clinical.db").readBytes()
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("SYNTHETIC_SECRET_MARKER"))
        assertFalse(bytes.take(16).toByteArray().toString(Charsets.US_ASCII).startsWith("SQLite format 3"))
    }
    @Test fun keyIsSealedAndCanBeRemoved() {
        val vault=Vault(context);vault.put("test", "SYNTHETIC_TEST_KEY".toByteArray())
        assertEquals("SYNTHETIC_TEST_KEY",vault.get("test")!!.toString(Charsets.UTF_8))
        val file=java.io.File(context.applicationInfo.dataDir,"shared_prefs/sealed.xml")
        assertFalse(file.readText().contains("SYNTHETIC_TEST_KEY"));vault.remove("test");assertNull(vault.get("test"))
    }
    @Test fun persianReportBuildsPdfWithoutInventingAMH() {
        val report=ReportComposer.create(context.localized("fa"),Patient(nickname="بیمار ساختگی"),"fa")
        assertTrue(report.text.contains("ارائه نشده"));assertFalse(report.text.contains("normal"))
        val bytes=ByteArrayOutputStream();ReportComposer.writePdf(report,bytes)
        assertTrue(bytes.toByteArray().toString(Charsets.ISO_8859_1).startsWith("%PDF"))
    }
}
