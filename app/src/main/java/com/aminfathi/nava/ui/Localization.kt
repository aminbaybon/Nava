package com.aminfathi.nava.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import com.aminfathi.nava.domain.Field
import java.util.Locale

val LocalLocalized = staticCompositionLocalOf<Context> { error("Localized context is not available") }

data class Language(val tag:String,val nativeName:String,val rtl:Boolean)
val languages=listOf(Language("en","English",false),Language("fa","فارسی",true),Language("ar","العربية",true),Language("hy","Հայերեն",false),Language("az","Azərbaycan dili",false),Language("ur-PK","اردو",true),Language("ckb","کوردی • سۆرانی",true),Language("ku","Kurdî • Kurmancî",false))
fun Context.localized(tag:String):Context {
    val locale=Locale.forLanguageTag(tag.ifEmpty { "en" })
    val configuration=Configuration(resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
    return createConfigurationContext(configuration)
}
fun Context.label(key:String):String {
    val resource=resources.getIdentifier(key,"string",packageName)
    return if(resource!=0) getString(resource) else key.replace('_',' ')
}
fun Context.fieldLabel(field:Field):String {
    val suffix=field.id.substringAfterLast('.')
    val key="field_"+field.id.replace('.','_')
    val resource=resources.getIdentifier(key,"string",packageName)
    if(resource!=0) return getString(resource)
    val cycle=field.id.split('.').getOrNull(2)?.toIntOrNull()
    val locales=resources.configuration.locales
    val locale=if(locales.isEmpty) Locale.getDefault() else locales[0]
    return label(field.domain)+" · "+(cycle?.let { java.text.NumberFormat.getIntegerInstance(locale).format(it)+" · " } ?: "")+label("detail_$suffix")
}
