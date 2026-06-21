package com.hoyopass.manager

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val BG = Color(0xFF171A35)
private val TEXT = Color(0xFFEEF0FF)
private val SUB = Color(0xFF9AA0D4)
private val ACCENT = Color(0xFF6F7BFF)
private val mdFmt = DateTimeFormatter.ofPattern("M/d")
private fun yenW(n: Int) = "¥" + "%,d".format(n)

/** 「近い課金リスト」ウィジェット。 */
class PaymentWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = PassRepository(context).snapshot()
        provideContent { WidgetContent(data) }
    }
}

class PaymentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaymentWidget()
}

@Composable
private fun WidgetContent(data: AppData) {
    val today = LocalDate.now()
    val events = data.paymentEvents(today, 6)
    val total6 = events.sumOf { it.amount }
    val shown = events.take(5)

    Column(
        GlanceModifier.fillMaxSize()
            .background(BG)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "🌙 課金予定",
                style = TextStyle(color = ColorProvider(TEXT), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "6ヶ月 ${yenW(total6)}",
                style = TextStyle(color = ColorProvider(ACCENT), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        if (shown.isEmpty()) {
            Text("予定なし", style = TextStyle(color = ColorProvider(SUB), fontSize = 12.sp))
        } else {
            shown.forEach { e ->
                val g = gameById(e.gameId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(Color(g.colorHex))) {}
                    Spacer(GlanceModifier.width(7.dp))
                    Text(
                        e.date.format(mdFmt),
                        style = TextStyle(color = ColorProvider(SUB), fontSize = 12.sp),
                        modifier = GlanceModifier.width(38.dp),
                    )
                    Text(
                        "${g.name}・${e.name}",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(TEXT), fontSize = 12.sp),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        yenW(e.amount),
                        style = TextStyle(color = ColorProvider(TEXT), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
