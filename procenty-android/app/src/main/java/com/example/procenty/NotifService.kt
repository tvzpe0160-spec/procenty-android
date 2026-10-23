package com.example.procenty

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val p = sbn.packageName
        if (!(p.startsWith("org.telegram") || p == "org.thunderdog.challegram")) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val e = n.extras
        val c = mutableListOf<String>()
        e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { c.add(it.toString()) }
        e.getCharSequence(Notification.EXTRA_TEXT)?.let { c.add(it.toString()) }
        @Suppress("DEPRECATION")
        e.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { m ->
            (m as? Bundle)?.getCharSequence("text")?.let { c.add(it.toString()) }
        }
        val o = c.sortedByDescending { it.length }.firstNotNullOfOrNull { Parser.parse(it) } ?: return
        val lines: List<Line>
        var disc = 0.0
        if (o.lines.isNotEmpty()) {
            lines = o.lines.map { val (pc, k) = Store.match(this, it.name); Line(it.name, it.amount, pc, k) }
            val t = o.total
            if (t != null) disc = maxOf(0.0, lines.sumOf { it.amount } - t)
        } else {
            val t = o.total ?: return
            lines = listOf(Line("Заказ " + o.id, t, Store.defPct(this), 1.0))
        }
        Store.upsert(this, Sale(o.id, sbn.postTime, lines, disc))
    }
}
