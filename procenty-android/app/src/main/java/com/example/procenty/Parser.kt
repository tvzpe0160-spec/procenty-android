package com.example.procenty

data class OLine(val name: String, val amount: Double)
data class Order(val id: String, val lines: List<OLine>, val total: Double?)

object Parser {
    private const val NUM = """([\d\s\u00A0]+[.,]\d{2})"""
    private fun num(s: String) = s.replace(Regex("""[\s\u00A0]"""), "").replace(',', '.').toDoubleOrNull()

    fun parse(text: String): Order? {
        val id = Regex("""Заказ клиента\s*№\s*-?\s*(\d+)""").find(text)?.groupValues?.get(1) ?: return null
        val total = Regex("""Итого к оплате:\s*$NUM""").find(text)?.groupValues?.get(1)?.let { num(it) }
        val lines = mutableListOf<OLine>()
        val itemRe = Regex("""^\s*\d+\)\s*(.+?)\s*$""")
        val sumRe = Regex("""^\s*Сумма:\s*$NUM""")
        var cur: String? = null
        for (ln in text.lines()) {
            val m = itemRe.find(ln)
            if (m != null) { cur = m.groupValues[1].replace(Regex("""\s*\d+\s*шт.*$"""), "").trim(); continue }
            val s = sumRe.find(ln)
            val name = cur
            if (s != null && name != null) {
                num(s.groupValues[1])?.let { lines.add(OLine(name, it)) }
                cur = null
            }
        }
        if (lines.isEmpty() && total == null) return null
        return Order(id, lines, total)
    }
}
