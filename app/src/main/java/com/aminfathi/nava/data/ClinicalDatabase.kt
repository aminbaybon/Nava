package com.aminfathi.nava.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.aminfathi.nava.security.Vault
import com.aminfathi.nava.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Encrypted SQLite aggregates. No destructive upgrade path; see schemas/1.sql. */
class ClinicalDatabase private constructor(private val helper:SupportSQLiteOpenHelper) {
    internal val mutex=Mutex()
    internal val changes=MutableStateFlow(0L)
    internal val sqlite get()=helper.writableDatabase
    fun close()=helper.close()
    companion object {
        fun open(context:Context,vault:Vault):ClinicalDatabase {
            System.loadLibrary("sqlcipher")
            val configuration=SupportSQLiteOpenHelper.Configuration.builder(context).name("clinical.db")
                .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db:SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE patients (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, revision INTEGER NOT NULL)")
                    }
                    override fun onOpen(db:SupportSQLiteDatabase) { db.query("PRAGMA secure_delete=ON").close() }
                    override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {
                        error("An explicit, tested migration is required")
                    }
                    override fun onDowngrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {
                        error("Downgrade is not supported")
                    }
                }).build()
            return ClinicalDatabase(SupportOpenHelperFactory(vault.databaseKey().copyOf()).create(configuration))
        }
    }
}
class PatientRepository(private val db:ClinicalDatabase) {
    private val json=Json { ignoreUnknownKeys=false;encodeDefaults=true }
    private fun decode(payload:String):Patient=json.decodeFromString<Patient>(payload).also {require(it.schemaVersion==1)}
    private fun read(id:String):Patient = db.sqlite.query("SELECT payload FROM patients WHERE id = ?",arrayOf(id)).use { cursor ->
        check(cursor.moveToFirst()) {"Patient unavailable"};decode(cursor.getString(0))
    }
    val patients:Flow<List<Patient>> = db.changes.map {
        db.mutex.withLock {
            db.sqlite.query("SELECT payload FROM patients ORDER BY rowid").use { cursor ->
                buildList { while(cursor.moveToNext()) add(decode(cursor.getString(0))) }
            }
        }
    }.flowOn(Dispatchers.IO)
    suspend fun get(id:String):Patient=withContext(Dispatchers.IO) {db.mutex.withLock {read(id)}}
    private suspend fun <T> transaction(block:()->T):T=withContext(Dispatchers.IO) {
        db.mutex.withLock {
            db.sqlite.beginTransaction()
            val result=try {block().also {db.sqlite.setTransactionSuccessful()}} finally {db.sqlite.endTransaction()}
            db.changes.value+=1
            result
        }
    }
    suspend fun create(name:String):String=transaction {
        val p=Patient(nickname=name.trim().take(80))
        db.sqlite.execSQL("INSERT INTO patients (id,payload,revision) VALUES (?,?,?)",arrayOf(p.id,json.encodeToString(p),0L))
        p.id
    }
    suspend fun mutate(id:String,transform:(Patient)->Patient):Patient=transaction {
        val old=read(id);val next=transform(old).copy(revision=old.revision+1)
        require(next.id==old.id)
        db.sqlite.compileStatement("UPDATE patients SET payload=?, revision=? WHERE id=? AND revision=?").use { statement ->
            statement.bindString(1,json.encodeToString(next));statement.bindLong(2,next.revision)
            statement.bindString(3,id);statement.bindLong(4,old.revision)
            check(statement.executeUpdateDelete()==1)
        }
        next
    }
    suspend fun delete(id:String)=transaction {db.sqlite.execSQL("DELETE FROM patients WHERE id=?",arrayOf(id))}
    suspend fun deleteAll()=transaction {db.sqlite.execSQL("DELETE FROM patients")}
}
