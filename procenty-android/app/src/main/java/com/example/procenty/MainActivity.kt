package com.example.procenty

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private var off = 0
    private var screen = 0
    private val ru = Locale("ru")

    override fun onCreate(b: Bundle?) { super.onCreate(b); render() }
    override fun onResume() { super.onResume(); render() }
    @Deprecated("back")
    override fun onBackPressed() { if (screen != 0) { screen = 0; render() } else super.onBackPressed() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun f(x: Double) = String.format(ru, "%,.2f", x)
    private fun d(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    private fun tv(t: String, sz: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; setPadding(0, dp(4), 0, dp(4)); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun numEt(v: Double) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(v.toString()); textSize = 14f
    }
    private fun page(): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(24)) }
        setContentView(ScrollView(this).apply { addView(r) })
        return r
    }
    private fun render() { if (screen == 1) settings() else main() }

    private fun main() {
        val r = page()
        val on = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        if (!on) {
            r.addView(tv("Доступ к уведомлениям выключен: продажи не записываются", 15f))
            r.addView(Button(this).apply { text = "Включить доступ"; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
        }
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, off) }
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
        val items = Store.load(this).filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.ts }
            c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
        }.sortedByDescending { it.ts }

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nav.addView(Button(this).apply { text = "‹"; setOnClickListener { off--; render() } })
        nav.addView(tv(SimpleDateFormat("LLLL yyyy", ru).format(cal.time), 18f, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply { text = "›"; setOnClickListener { if (off < 0) { off++; render() } } })
        r.addView(nav)

        val sdf = SimpleDateFormat("yyyyMMdd", ru)
        val todayStr = sdf.format(Date())
        val today = items.filter { sdf.format(Date(it.ts)) == todayStr }.sumOf { it.commission }
        r.addView(tv("Заработано за месяц", 14f))
        r.addView(tv(f(items.sumOf { it.commission }) + " руб.", 32f, true))
        r.addView(tv("Продаж: ${items.size}, оборот ${f(items.sumOf { it.net })}" + if (off == 0) "\nСегодня: ${f(today)}" else "", 14f))

        r.addView(Button(this).apply { text = "Проценты, группы и бонусы"; setOnClickListener { screen = 1; render() } })
        r.addView(Button(this).apply {
            text = "Пересчитать месяц по текущим правилам"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("Проценты и коэффициенты всех продаж этого месяца будут заменены на текущие из правил, ручные правки по строкам пропадут. Продолжить?")
                    .setPositiveButton("Да") { _, _ ->
                        items.forEach { s ->
                            Store.put(this@MainActivity, s.copy(lines = s.lines.map { l -> val (pc, k) = Store.match(this@MainActivity, l.name); l.copy(pct = pc, coef = k) }))
                        }
                        render()
                    }.setNegativeButton("Отмена", null).show()
            }
        })

        r.addView(tv("Продажи: нажми, чтобы изменить скидку и проценты; удержи, чтобы удалить", 13f).apply { setPadding(0, dp(12), 0, dp(4)) })
        if (items.isEmpty()) r.addView(tv("Пока пусто", 15f))
        val df = SimpleDateFormat("dd.MM HH:mm", ru)
        items.forEach { s ->
            val disc = if (s.discount > 0) " (скидка ${f(s.discount)})" else ""
            r.addView(tv("${df.format(Date(s.ts))} · ${s.title}\n${f(s.net)}$disc → +${f(s.commission)}", 15f).apply {
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { editSale(s) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setMessage("Удалить эту продажу?")
                        .setPositiveButton("Удалить") { _, _ -> Store.save(this@MainActivity, Store.load(this@MainActivity).filter { it.id != s.id }); render() }
                        .setNegativeButton("Отмена", null).show()
                    true
                }
            })
        }
    }

    private fun editSale(s: Sale) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0) }
        box.addView(tv("Скидка: сумма или процент (например 50 или 5%)", 13f))
        val disc = EditText(this).apply { setText(if (s.discount > 0) s.discount.toString() else ""); hint = "0"; textSize = 14f }
        box.addView(disc)
        val fields = s.lines.map { l ->
            box.addView(tv("${l.name} — ${f(l.amount)}", 14f, true).apply { setPadding(0, dp(12), 0, 0) })
            val row = LinearLayout(this)
            val p = numEt(l.pct); val k = numEt(l.coef)
            row.addView(tv("% ", 14f)); row.addView(p, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tv("  коэф. ", 14f)); row.addView(k, LinearLayout.LayoutParams(0, -2, 1f))
            box.addView(row)
            Pair(p, k)
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Сохранить") { _, _ ->
                val t = disc.text.toString().trim().replace(',', '.')
                val dv = if (t.endsWith("%")) (t.dropLast(1).toDoubleOrNull() ?: 0.0) * s.gross / 100 else (t.toDoubleOrNull() ?: 0.0)
                val nl = s.lines.mapIndexed { i, l -> l.copy(pct = d(fields[i].first.text.toString()) ?: l.pct, coef = d(fields[i].second.text.toString()) ?: l.coef) }
                Store.put(this, s.copy(lines = nl, discount = dv))
                render()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun settings() {
        val r = page()
        r.addView(tv("Проценты и бонусы", 22f, true))
        r.addView(tv("Основной процент (для всех товаров, которых нет в правилах), %", 14f))
        val defEt = numEt(Store.defPct(this)); r.addView(defEt)
        r.addView(tv("Правила для групп. В первом поле ключевые слова через запятую, они ищутся в названии товара (регистр не важен). Работает первое подходящее правило. Коэф. — бонусный множитель к твоему проценту (1 = без бонуса).", 13f).apply { setPadding(0, dp(12), 0, dp(4)) })
        r.addView(tv("слова            %            коэф.", 12f))
        val rows = mutableListOf<Triple<EditText, EditText, EditText>>()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun addRow(rule: Rule) {
            val row = LinearLayout(this)
            val k = EditText(this).apply { setText(rule.keys); hint = "слова"; textSize = 14f }
            val p = numEt(rule.pct); val c = numEt(rule.coef)
            val t = Triple(k, p, c)
            rows.add(t)
            row.addView(k, LinearLayout.LayoutParams(0, -2, 2f))
            row.addView(p, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply { text = "×"; setOnClickListener { box.removeView(row); rows.remove(t) } })
            box.addView(row)
        }
        Store.rules(this).forEach { addRow(it) }
        r.addView(box)
        r.addView(Button(this).apply { text = "+ Добавить правило"; setOnClickListener { addRow(Rule("", 1.0, 1.0)) } })
        r.addView(Button(this).apply {
            text = "Сохранить"
            setOnClickListener {
                Store.setDefPct(this@MainActivity, d(defEt.text.toString()) ?: 1.0)
                Store.saveRules(this@MainActivity, rows.filter { it.first.text.toString().isNotBlank() }
                    .map { Rule(it.first.text.toString().trim(), d(it.second.text.toString()) ?: 1.0, d(it.third.text.toString()) ?: 1.0) })
                Toast.makeText(this@MainActivity, "Сохранено. Действует на новые продажи", Toast.LENGTH_SHORT).show()
                screen = 0; render()
            }
        })
        r.addView(Button(this).apply { text = "Назад"; setOnClickListener { screen = 0; render() } })
    }
}
