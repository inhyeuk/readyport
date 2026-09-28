package com.readyport.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.readyport.MainActivity
import com.readyport.R

/**
 * 홈 화면 위젯 (PRD 5.4): 누르면 '입국 때 보여 주기'를 연다.
 * QR 그림이나 이름은 위젯에 그리지 않는다 — 잠금 없이 보이는 홈 화면에 개인정보를 두지 않기 위해.
 */
class EntryQrWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PRESENT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_entry_qr).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            manager.updateAppWidget(id, views)
        }
    }
}
