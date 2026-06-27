package com.hoyopass.manager

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "hoyopass")
private val DATA_KEY = stringPreferencesKey("appdata")
private val json = Json { ignoreUnknownKeys = true }

/** DataStore に AppData を JSON で永続化する単純なリポジトリ。 */
class PassRepository(private val context: Context) {

    val data: Flow<AppData> = context.dataStore.data.map { prefs ->
        prefs[DATA_KEY]?.let { runCatching { json.decodeFromString<AppData>(it) }.getOrNull() }
            ?: AppData()
    }

    suspend fun snapshot(): AppData = data.first()

    private suspend fun update(transform: (AppData) -> AppData) {
        context.dataStore.edit { prefs ->
            val current = prefs[DATA_KEY]?.let {
                runCatching { json.decodeFromString<AppData>(it) }.getOrNull()
            } ?: AppData()
            prefs[DATA_KEY] = json.encodeToString(transform(current))
        }
    }

    /**
     * 初回起動時の初期化：
     * シーズン＝既知の次回アップデート日、月パス＝毎月更新前提で今日を起点に投入。
     */
    suspend fun seedIfNeeded() = update { d ->
        if (d.seeded) return@update d
        val today = java.time.LocalDate.now().toString()
        val seeded = buildMap {
            GAMES.forEach { g ->
                put(passKey(g.id, PassType.SEASON), PassEntry(g.id, PassType.SEASON, g.seasonAnchor, g.seasonDays))
                put(passKey(g.id, PassType.MONTHLY), PassEntry(g.id, PassType.MONTHLY, today, 30))
            }
        }
        // 既存（ユーザー登録済み）を優先し、未登録ぶんだけ補完
        d.copy(passes = seeded + d.passes, seeded = true)
    }

    suspend fun savePass(entry: PassEntry) = update { d ->
        d.copy(passes = d.passes + (passKey(entry.gameId, entry.type) to entry))
    }

    suspend fun deletePass(gameId: String, type: PassType) = update { d ->
        d.copy(passes = d.passes - passKey(gameId, type))
    }

    /** 月パスを30日延長（現在の期限から積み増し。期限切れなら今日から30日で再開）。 */
    suspend fun extendMonth(gameId: String) = update { d ->
        val key = passKey(gameId, PassType.MONTHLY)
        val today = logicalToday()
        val cur = d.passes[key]
        val next = if (cur == null) {
            PassEntry(gameId, PassType.MONTHLY, today.toString(), 30)
        } else {
            val expiry = java.time.LocalDate.parse(cur.startDate).plusDays(cur.days.toLong())
            if (!expiry.isBefore(today)) cur.copy(days = cur.days + 30)
            else PassEntry(gameId, PassType.MONTHLY, today.toString(), 30)
        }
        d.copy(passes = d.passes + (key to next))
    }

    suspend fun setLeadDays(days: Int) = update { it.copy(leadDays = days.coerceIn(0, 30)) }

    suspend fun setUrl(gameId: String, url: String) = update { d ->
        d.copy(urls = d.urls + (gameId to url))
    }

    suspend fun setSeasonTier(gameId: String, tier: SeasonTier) = update { d ->
        d.copy(seasonTiers = d.seasonTiers + (gameId to tier))
    }
}
