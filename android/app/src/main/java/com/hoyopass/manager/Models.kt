package com.hoyopass.manager

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 残り日数の切り替わり時刻（午前5時）。これより前は前日扱い。 */
const val DAY_ROLLOVER_HOUR = 5

/** 5時境界を適用した「論理的な今日」。 */
fun logicalToday(): LocalDate {
    val now = LocalDateTime.now()
    return if (now.hour < DAY_ROLLOVER_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
}

enum class PassType { MONTHLY, SEASON }

/** ゲーム定義（top-up URL は設定で上書き可能） */
data class GameDef(
    val id: String,
    val name: String,
    val sub: String,
    val colorHex: Long,
    /** 公式ストアのアプリアイコンURL（参照のみ・失敗時はローカル記号にフォールバック） */
    val iconUrl: String,
    val monthName: String,
    val seasonName: String,
    val seasonDays: Int,
    /** 既知の次回アップデート日（ISO）。初回起動時にシーズンパスの初期値として使用。 */
    val seasonAnchor: String,
    val monthPrice: Int,
    val seasonStdName: String,
    val seasonStdPrice: Int,
    val seasonPremiumName: String,
    val seasonPremiumPrice: Int,
    val defaultUrl: String,
)

enum class SeasonTier { STD, PREMIUM }

val GAMES = listOf(
    GameDef(
        id = "genshin", name = "原神", sub = "Genshin Impact", colorHex = 0xFF3B6FE0,
        iconUrl = "https://play-lh.googleusercontent.com/YQqyKaXX-63krqsfIzUEJWUWLINxcb5tbS6QVySdxbS7eZV7YB2dUjUvX27xA0TIGtfxQ5v-tQjwlT5tTB-O=s144",
        monthName = "空月の祝福", seasonName = "紀行", seasonDays = 42,
        seasonAnchor = "2026-07-01", monthPrice = 610,
        seasonStdName = "精緻な紀行", seasonStdPrice = 1220,
        seasonPremiumName = "華麗な紀行", seasonPremiumPrice = 2440,
        defaultUrl = "https://sdk.hoyoverse.com/payment/genshin/index.html?lang=ja-jp",
    ),
    GameDef(
        id = "hsr", name = "スターレイル", sub = "Honkai: Star Rail", colorHex = 0xFF7C4DFF,
        iconUrl = "https://play-lh.googleusercontent.com/aWrGocSA7hEuk1qAPe7L4T57LvLKrwwH26cK2_LOqxRQMQX7j3uHYojC-EKWgYEV2PdrmE0ahqvvhLhXrAGk6Q=s144",
        monthName = "列車補給標章", seasonName = "ナナシの勲功", seasonDays = 42,
        seasonAnchor = "2026-07-15", monthPrice = 610,
        seasonStdName = "ナナシビトの褒章", seasonStdPrice = 1220,
        seasonPremiumName = "ナナシビトの勲章", seasonPremiumPrice = 2480,
        defaultUrl = "https://sdk.hoyoverse.com/payment/hsr/index.html?lang=ja-jp",
    ),
    GameDef(
        id = "zzz", name = "ゼンレスゾーンゼロ", sub = "Zenless Zone Zero", colorHex = 0xFFFF7A3D,
        iconUrl = "https://play-lh.googleusercontent.com/-ZZaqZBQ7EBjH4j0hyHX-0Fu0jUtnoOc-LwydvgQmsXWBZLxyAhxPcmIakzZB7NFurlK4Mj0pbvYe0pHYSuv4p8=s144",
        monthName = "インターノット会員権", seasonName = "エリーファンド", seasonDays = 42,
        seasonAnchor = "2026-07-29", monthPrice = 610,
        seasonStdName = "成長プラン", seasonStdPrice = 1220,
        seasonPremiumName = "プレミアムプラン", seasonPremiumPrice = 2480,
        defaultUrl = "https://sdk.hoyoverse.com/payment/zenless/index.html?lang=ja-jp",
    ),
)

fun gameById(id: String): GameDef = GAMES.first { it.id == id }

/** 1件のパス登録。startDate は ISO (yyyy-MM-dd)。 */
@Serializable
data class PassEntry(
    val gameId: String,
    val type: PassType,
    val startDate: String,
    val days: Int,
)

@Serializable
data class AppData(
    val leadDays: Int = 3,
    val urls: Map<String, String> = emptyMap(),
    /** key = "<gameId>_<type>" */
    val passes: Map<String, PassEntry> = emptyMap(),
    /** key = gameId → シーズンパスの種類。未設定は STD */
    val seasonTiers: Map<String, SeasonTier> = emptyMap(),
    /** 初回シーズン初期値の投入済みフラグ */
    val seeded: Boolean = false,
)

fun AppData.tierOf(gameId: String): SeasonTier = seasonTiers[gameId] ?: SeasonTier.STD

fun AppData.priceOf(gameId: String, type: PassType): Int {
    val g = gameById(gameId)
    return when {
        type == PassType.MONTHLY -> g.monthPrice
        tierOf(gameId) == SeasonTier.PREMIUM -> g.seasonPremiumPrice
        else -> g.seasonStdPrice
    }
}

fun passKey(gameId: String, type: PassType) = "${gameId}_${type.name}"

data class Remaining(
    val expiry: LocalDate,
    val daysLeft: Long,
    val percentLeft: Float,
)

fun PassEntry.remaining(today: LocalDate = logicalToday()): Remaining {
    if (type == PassType.SEASON) {
        // 次回アップデート日までのカウントダウン。過ぎたら周期分だけ自動で次回へ繰り上げ。
        var next = LocalDate.parse(startDate)
        while (next.isBefore(today)) next = next.plusDays(days.toLong())
        val left = ChronoUnit.DAYS.between(today, next)
        val prev = next.minusDays(days.toLong())
        val used = ChronoUnit.DAYS.between(prev, today).coerceIn(0, days.toLong())
        val pct = if (days == 0) 0f else (1f - used.toFloat() / days).coerceIn(0f, 1f)
        return Remaining(next, left, pct)
    }
    // 月パス：購入日 → 期限までのカウントダウン
    val start = LocalDate.parse(startDate)
    val expiry = start.plusDays(days.toLong())
    val left = ChronoUnit.DAYS.between(today, expiry)
    val used = ChronoUnit.DAYS.between(start, today).coerceIn(0, days.toLong())
    val pct = if (days == 0) 0f else (1f - used.toFloat() / days).coerceIn(0f, 1f)
    return Remaining(expiry, left, pct)
}

fun AppData.urlFor(gameId: String): String =
    urls[gameId] ?: gameById(gameId).defaultUrl

/** カレンダー用の課金イベント。 */
data class PaymentEvent(
    val date: LocalDate,
    val gameId: String,
    val type: PassType,
    val name: String,
    val amount: Int,
)

/**
 * 今日〜monthsAhead先までに必要な課金イベントを生成。
 * 月パス＝期限(購入+日数)ごと、シーズン＝アップデート日(周期)ごと。
 */
fun AppData.paymentEvents(
    today: LocalDate = logicalToday(),
    monthsAhead: Long = 24,
): List<PaymentEvent> {
    val horizon = today.plusMonths(monthsAhead)
    val events = mutableListOf<PaymentEvent>()
    GAMES.forEach { g ->
        PassType.entries.forEach { type ->
            val p = passes[passKey(g.id, type)] ?: return@forEach
            val amount = priceOf(g.id, type)
            if (amount <= 0) return@forEach
            val name = if (type == PassType.MONTHLY) g.monthName else g.seasonName
            // 月パス＝現在の期限(購入+累積日数)が次の課金で以後30日ごと / シーズン＝周期(=days)ごと
            val step = if (type == PassType.MONTHLY) 30L else p.days.toLong()
            var d = if (type == PassType.MONTHLY)
                LocalDate.parse(p.startDate).plusDays(p.days.toLong())
            else LocalDate.parse(p.startDate)
            while (d.isBefore(today)) d = d.plusDays(step)
            while (!d.isAfter(horizon)) {
                events.add(PaymentEvent(d, g.id, type, name, amount))
                d = d.plusDays(step)
            }
        }
    }
    return events.sortedWith(compareBy({ it.date }, { it.gameId }))
}
