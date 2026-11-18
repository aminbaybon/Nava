package com.aminfathi.nava.domain

/** Fixed file-packet slots. Quotes are copied from supplied text; nothing is interpreted. */
object PacketTemplate {
    fun slots(pathway: Pathway): List<Pair<String, List<String>>> = buildList {
        add("detail_amh" to listOf("amh", "antimullerian", "anti-mullerian", "آنتی مولر", "انتی مولر"))
        add("detail_fsh" to listOf("fsh"))
        add("detail_lh" to listOf("lh"))
        add("detail_estradiol" to listOf("estradiol", "oestradiol", "e2", "استرادیول"))
        add("detail_tsh" to listOf("tsh"))
        add("detail_prolactin" to listOf("prolactin", "پرولاکتین"))
        add("detail_ultrasound" to listOf("ultrasound", "sonography", "afc", "antral", "سونو"))
        add("detail_semen" to listOf("semen", "sperm", "morphology", "اسپرم"))
        if (pathway == Pathway.IVF) {
            add("detail_retrieval" to listOf("retrieval", "oocyte", "pickup", "تخمک"))
            add("detail_embryos" to listOf("embryo", "blastocyst", "جنین"))
        } else {
            add("detail_tubes" to listOf("hsg", "hycosy", "tubal", "لوله"))
            add("detail_insemination" to listOf("iui", "insemination", "تلقیح"))
        }
    }
    fun quotes(text: String, keys: List<String>): List<String> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            .filter { line -> keys.any { matches(line, it) } }
            .distinct().take(6).map { it.take(240) }
    fun fill(pathway: Pathway, texts: List<String>): List<Pair<String, List<String>>> =
        slots(pathway).map { (id, keys) -> id to texts.flatMap { quotes(it, keys) }.distinct().take(6) }
    private fun matches(line: String, key: String): Boolean {
        if (key.any { it.code > 127 }) return line.contains(key, ignoreCase = true)
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(key) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(line)
    }
}
