package com.aminfathi.nava

import android.app.Application
import com.aminfathi.nava.data.*
import com.aminfathi.nava.files.AttachmentStore
import com.aminfathi.nava.security.Vault
import com.aminfathi.nava.network.OpenAIProvider
import com.aminfathi.nava.network.TourismClient
import com.aminfathi.nava.ai.InterviewOrchestrator

class NavaApplication: Application() {
    val vault by lazy { Vault(this) }
    val database by lazy { ClinicalDatabase.open(this,vault) }
    val patients by lazy { PatientRepository(database) }
    val files by lazy { AttachmentStore(this, vault) }
    val preferences by lazy { PreferenceRepository(this) }
    val provider by lazy { OpenAIProvider { vault.apiKey() } }
    val portal by lazy { TourismClient() }
    val interview by lazy { InterviewOrchestrator(patients,provider) }
}
