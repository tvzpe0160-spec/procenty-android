package com.example.procenty

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Line(val name: String, val amount: Double, val pct: Double, val coef: Double)
data class Rule(val keys: String, val pct: Double, val coef: Double)

data class Sale(val id: String, val ts: Long, val lines: List<Line>, val discount: Double) {
    val gross: Double get() = lines.sumOf { it.amount }
    val net: Double get() = gross - discount
    val commission: Double get() {
        val g = gross
        if (g <= 0) return 0.0
        val k = (g - discount) / g
        return lines.sumOf { it.amount * k * it.pct / 100 * it.coef }
    }
    val title: String get() = lines.joinToString("; ") { it.name }.take(80)
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    fun defPct(c: Context): Double = sp(c).getString("defpct", "1")?.toDoubleOrNull() ?: 1.0
    fun setDefPct(c: Context, v: Double) { sp(c).edit().putString("defpct", v.toString()).apply() }

    fun rules(c: Context): List<Rule> {
        val s = sp(c).getString("rules", null) ?: return listOf(Rule("телевизор", 0.7, 1.0))
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { val o = a.getJSONObject(it); Rule(o.getString("k"), o.getDouble("p"), o.getDouble("c")) }
        } catch (e: Exception) { emptyList() }
    }
    fun saveRules(c: Context, l: List<Rule>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("k", it.keys).put("p", it.pct).put("c", it.coef)) }
        sp(c).edit().putString("rules", a.toString()).apply()
    }

    // Первое подходящее правило; иначе основной процент и коэф. 1
    fun match(c: Context, name: String): Pair<Double, Double> {
        val low = name.lowercase()
        for (r in rules(c)) {
            val ks = r.keys.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (ks.any { low.contains(it) }) return Pair(r.pct, r.coef)
        }
        return Pair(defPct(c), 1.0)
    }

    @Synchronized fun load(c: Context): MutableList<Sale> {
        val out = mutableListOf<Sale>()
        try {
            val a = JSONArray(sp(c).getString("sales2", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val la = o.getJSONArray("lines")
                val ls = (0 until la.length()).map { val x = la.getJSONObject(it); Line(x.getString("n"), x.getDouble("a"), x.getDouble("p"), x.getDouble("c")) }
                out.add(Sale(o.getString("id"), o.getLong("ts"), ls, o.getDouble("d")))
            }
        } catch (e: Exception) { }
        return out
    }

    @Synchronized fun save(c: Context, l: List<Sale>) {
        val a = JSONArray()
        l.forEach { s ->
            val la = JSONArray()
            s.lines.forEach { la.put(JSONObject().put("n", it.name).put("a", it.amount).put("p", it.pct).put("c", it.coef)) }
            a.put(JSONObject().put("id", s.id).put("ts", s.ts).put("d", s.discount).put("lines", la))
        }
        sp(c).edit().putString("sales2", a.toString()).apply()
    }

    @Synchronized fun put(c: Context, s: Sale) {
        val l = load(c); val i = l.indexOfFirst { it.id == s.id }
        if (i >= 0) l[i] = s else l.add(s)
        save(c, l)
    }

    // Повторное уведомление по тому же заказу не дублирует; берём только если оно полнее
    @Synchronized fun upsert(c: Context, s: Sale) {
        val old = load(c).firstOrNull { it.id == s.id }
        if (old == null) put(c, s)
        else if (s.lines.size > old.lines.size) put(c, s.copy(ts = old.ts, discount = old.discount))
    }
}
