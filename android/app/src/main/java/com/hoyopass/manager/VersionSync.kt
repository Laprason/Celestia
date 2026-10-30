package com.hoyopass.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 公式のお知らせAPIから各ゲームの現行バージョンと次回アップデート日（JST）を取得する。
 * 最新バージョンのお知らせのうち最も遅い終了時刻＝次回アップデート（メンテ開始）とみなす。
 */
object VersionSync {

    // 原神は region によって timezone フィールドと実際の時刻がずれるため、整合する os_usa を使う
    private val SOURCES = mapOf(
        "genshin" to "https://sg-hk4e-api.hoyoverse.com/common/hk4e_global/announcement/api/getAnnList" +
            "?game=hk4e&game_biz=hk4e_global&lang=ja-jp&bundle_id=hk4e_global&platform=pc" +
            "&region=os_usa&level=55&uid=600000000",
        "hsr" to "https://sg-hkrpg-api.hoyoverse.com/common/hkrpg_global/announcement/api/getAnnList" +
            "?game=hkrpg&game_biz=hkrpg_global&lang=ja-jp&bundle_id=hkrpg_global&platform=pc" +
            "&region=prod_official_asia&level=70&uid=800000000&channel_id=1",
        "zzz" to "https://sg-announcement-static.hoyoverse.com/common/nap_global/announcement/api/getAnnList" +
            "?game=nap&game_biz=nap_global&lang=ja-jp&bundle_id=nap_global&platform=pc" +
            "&region=prod_gf_jp&level=60&uid=1300000000&channel_id=1",
    )

    private val VERSION_RE = Regex("""Ver\.?\s*(\d+)\.(\d+)""")
    private val TAG_RE = Regex("<[^>]+>")
    private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val JST = ZoneOffset.ofHours(9)
    /** これより先に終わるお知らせ（常設FAQ等）は無視 */
    private const val MAX_AHEAD_DAYS = 100L

    /** 取得してリポジトリへ反映。失敗したゲームは前回値のまま。 */
    suspend fun sync(repo: PassRepository) {
        val found = fetchAll()
        if (found.isNotEmpty()) repo.applyVersions(found)
    }

    private suspend fun fetchAll(): Map<String, VersionInfo> = withContext(Dispatchers.IO) {
        SOURCES.mapNotNull { (gameId, url) ->
            runCatching { extract(get(url)) }.getOrNull()?.let { gameId to it }
        }.toMap()
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun extract(body: String, now: Instant = Instant.now()): VersionInfo? {
        val root = Json.parseToJsonElement(body).jsonObject
        if (root["retcode"]?.jsonPrimitive?.intOrNull != 0) return null
        val data = root["data"]?.jsonObject ?: return null
        val tz = ZoneOffset.ofHours(data["timezone"]?.jsonPrimitive?.intOrNull ?: 8)
        val limit = now.plus(MAX_AHEAD_DAYS, ChronoUnit.DAYS)

        // (major*1000+minor, 終了時刻)
        val found = data["list"]?.jsonArray.orEmpty()
            .flatMap { it.jsonObject["list"]?.jsonArray.orEmpty() }
            .mapNotNull { el ->
                val ann = el.jsonObject
                val title = ann["title"]?.jsonPrimitive?.content?.replace(TAG_RE, "") ?: return@mapNotNull null
                val m = VERSION_RE.find(title) ?: return@mapNotNull null
                val endText = ann["end_time"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val end = LocalDateTime.parse(endText, TIME_FMT).atOffset(tz).toInstant()
                if (end.isAfter(now) && end.isBefore(limit)) {
                    m.groupValues[1].toInt() * 1000 + m.groupValues[2].toInt() to end
                } else null
            }
        if (found.isEmpty()) return null
        val latest = found.maxOf { it.first }
        val end = found.filter { it.first == latest }.maxOf { it.second }
        return VersionInfo(
            version = "${latest / 1000}.${latest % 1000}",
            nextUpdate = end.atOffset(JST).toLocalDate().toString(),
        )
    }
}
