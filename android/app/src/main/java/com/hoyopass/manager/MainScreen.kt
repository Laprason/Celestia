package com.hoyopass.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val BG = Color(0xFF0F1226)
private val CARD = Color(0xFF1E2243)
private val LINE = Color(0xFF33396B)
private val TEXT = Color(0xFFEEF0FF)
private val SUB = Color(0xFF9AA0D4)
private val ACCENT = Color(0xFF6F7BFF)
private val OK = Color(0xFF36D399)
private val WARN = Color(0xFFFBBD23)
private val DANGER = Color(0xFFF87272)

private val dateFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repo: PassRepository, onOpenTopup: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsState(initial = AppData())

    var editing by remember { mutableStateOf<Pair<String, PassType>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🌙 Celestia", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("今日 ${LocalDate.now().format(dateFmt)}", color = SUB, fontSize = 11.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(
                            if (showCalendar) Icons.AutoMirrored.Filled.ViewList else Icons.Default.CalendarMonth,
                            contentDescription = if (showCalendar) "一覧" else "カレンダー", tint = TEXT,
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "設定", tint = TEXT)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BG),
            )
        },
    ) { pad ->
        if (showCalendar) {
            CalendarView(data, Modifier.padding(pad))
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(GAMES, key = { it.id }) { game ->
                    GameCard(
                        game = game,
                        data = data,
                        onEdit = { type -> editing = game.id to type },
                        onExtend = { scope.launch { repo.extendMonth(game.id) } },
                        onBuy = { onOpenTopup(data.urlFor(game.id)) },
                    )
                }
            }
        }
    }

    editing?.let { (gid, type) ->
        EditDialog(
            game = gameById(gid),
            type = type,
            existing = data.passes[passKey(gid, type)],
            onDismiss = { editing = null },
            onSave = { entry -> scope.launch { repo.savePass(entry) }; editing = null },
            onDelete = { scope.launch { repo.deletePass(gid, type) }; editing = null },
        )
    }

    if (showSettings) {
        SettingsDialog(
            data = data,
            onDismiss = { showSettings = false },
            onSaveLead = { scope.launch { repo.setLeadDays(it) } },
            onSaveUrl = { id, url -> scope.launch { repo.setUrl(id, url) } },
            onSaveTier = { id, tier -> scope.launch { repo.setSeasonTier(id, tier) } },
        )
    }
}

@Composable
private fun GameCard(
    game: GameDef,
    data: AppData,
    onEdit: (PassType) -> Unit,
    onExtend: () -> Unit,
    onBuy: () -> Unit,
) {
    Surface(color = CARD, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = game.iconUrl,
                    contentDescription = game.name,
                    placeholder = painterResource(gameIconRes(game.id)),
                    error = painterResource(gameIconRes(game.id)),
                    fallback = painterResource(gameIconRes(game.id)),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.name, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(game.sub, color = SUB, fontSize = 11.sp)
                }
                SmallButton("🛒 課金センター", true) { onBuy() }
            }
            Spacer(Modifier.height(4.dp))
            PassRow(game, data, PassType.MONTHLY, onEdit, onExtend)
            HorizontalDivider(color = LINE)
            PassRow(game, data, PassType.SEASON, onEdit, onExtend)
        }
    }
}

@Composable
private fun PassRow(
    game: GameDef,
    data: AppData,
    type: PassType,
    onEdit: (PassType) -> Unit,
    onExtend: () -> Unit,
) {
    val entry = data.passes[passKey(game.id, type)]
    val passName = if (type == PassType.MONTHLY) game.monthName else game.seasonName
    val typeLabel = if (type == PassType.MONTHLY) "月パス" else "シーズン/BP"
    val rem = entry?.remaining()

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(passName, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                PillLabel(typeLabel, type)
            }
            Text(
                when {
                    entry == null && type == PassType.SEASON -> "タップして次回アップデート日を登録"
                    entry == null -> "タップして購入日を登録"
                    type == PassType.SEASON ->
                        "次回アップデート予定 ${rem!!.expiry.format(dateFmt)}（周期${entry.days}日）"
                    else ->
                        "購入 ${LocalDate.parse(entry.startDate).format(dateFmt)} → 期限 ${rem!!.expiry.format(dateFmt)}"
                },
                color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp),
            )
            if (rem != null) {
                val col = statusColor(rem.daysLeft, data.leadDays)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { rem.percentLeft },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = col, trackColor = Color(0xFF262B54),
                    gapSize = 0.dp,            // 塗りと残りトラックの隙間をなくす
                    drawStopIndicator = {},    // 端の丸い点を消す
                )
            }
            if (type == PassType.MONTHLY) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("延長", true) { onExtend() }
                    SmallButton("✎ 編集", false) { onEdit(type) }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 78.dp)) {
            if (rem == null) {
                Text("未登録", color = SUB, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                val col = statusColor(rem.daysLeft, data.leadDays)
                if (rem.daysLeft < 0) {
                    Text("期限切れ", color = col, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Text("${rem.daysLeft}", color = col, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                    Text("残り日", color = SUB, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun statusColor(daysLeft: Long, lead: Int): Color =
    when {
        daysLeft < 0 -> DANGER
        daysLeft <= lead -> WARN
        else -> OK
    }

@Composable
private fun PillLabel(text: String, type: PassType) {
    val bg = if (type == PassType.MONTHLY) Color(0x2E6F7BFF) else Color(0x2936D399)
    val fg = if (type == PassType.MONTHLY) Color(0xFFAEB6FF) else Color(0xFF7DF0C4)
    Box(Modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (primary) ACCENT else Color(0xFF262B54),
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
    ) {
        Text(text, color = TEXT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun EditDialog(
    game: GameDef,
    type: PassType,
    existing: PassEntry?,
    onDismiss: () -> Unit,
    onSave: (PassEntry) -> Unit,
    onDelete: () -> Unit,
) {
    val passName = if (type == PassType.MONTHLY) game.monthName else game.seasonName
    var dateText by remember {
        mutableStateOf(existing?.startDate ?: LocalDate.now().toString())
    }
    var daysText by remember {
        mutableStateOf((existing?.days ?: if (type == PassType.MONTHLY) 30 else game.seasonDays).toString())
    }
    val season = type == PassType.SEASON
    val expiry = runCatching {
        LocalDate.parse(dateText).plusDays(daysText.toLong()).format(dateFmt)
    }.getOrNull()

    AlertDialog(
        containerColor = Color(0xFF171A35),
        onDismissRequest = onDismiss,
        title = { Text("${game.name} ・ $passName", color = TEXT, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = dateText, onValueChange = { dateText = it },
                    label = { Text(if (season) "次回アップデート日 例 2026-06-21" else "購入日（開始日） 例 2026-06-21") },
                    singleLine = true, colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = daysText, onValueChange = { daysText = it.filter(Char::isDigit) },
                    label = { Text(if (season) "更新周期（日数）" else "有効期間（日数）") },
                    singleLine = true, colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        expiry == null -> "日付の形式が不正です（yyyy-MM-dd）"
                        season -> "次回更新日（自動計算）: $expiry"
                        else -> "期限日（自動計算）: $expiry"
                    },
                    color = if (expiry != null) SUB else DANGER, fontSize = 12.sp,
                )
                Text(
                    if (season) "次のアップデート（新バージョン）日を入力すると、その日が近づくと通知します。周期は約42日。アップデート日が過ぎると自動で次回へ繰り上がります。"
                    else "月パスは通常30日間です。",
                    color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = daysText.toIntOrNull() ?: return@TextButton
                    if (runCatching { LocalDate.parse(dateText) }.isSuccess && d > 0) {
                        onSave(PassEntry(game.id, type, dateText, d))
                    }
                }
            ) { Text("保存", color = ACCENT, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onDelete) { Text("削除", color = DANGER) }
                TextButton(onClick = onDismiss) { Text("閉じる", color = SUB) }
            }
        },
    )
}

@Composable
private fun SettingsDialog(
    data: AppData,
    onDismiss: () -> Unit,
    onSaveLead: (Int) -> Unit,
    onSaveUrl: (String, String) -> Unit,
    onSaveTier: (String, SeasonTier) -> Unit,
) {
    var lead by remember { mutableStateOf(data.leadDays.toString()) }
    val urlState = remember {
        mutableStateMapOf<String, String>().apply { GAMES.forEach { put(it.id, data.urlFor(it.id)) } }
    }
    val tierState = remember {
        mutableStateMapOf<String, SeasonTier>().apply { GAMES.forEach { put(it.id, data.tierOf(it.id)) } }
    }
    AlertDialog(
        containerColor = Color(0xFF171A35),
        onDismissRequest = onDismiss,
        title = { Text("⚙️ 設定", color = TEXT) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = lead, onValueChange = { lead = it.filter(Char::isDigit) },
                    label = { Text("通知タイミング（期限の何日前から）") },
                    singleLine = true, colors = fieldColors(),
                )
                Text("シーズンパスの種類（金額に反映）", color = TEXT, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                GAMES.forEach { g ->
                    Spacer(Modifier.height(8.dp))
                    Text("${g.name}・${g.seasonName}", color = SUB, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TierChip(
                            Modifier.weight(1f), "標準 ${g.seasonStdName}", yen(g.seasonStdPrice),
                            tierState[g.id] == SeasonTier.STD,
                        ) { tierState[g.id] = SeasonTier.STD }
                        TierChip(
                            Modifier.weight(1f), "上位 ${g.seasonPremiumName}", yen(g.seasonPremiumPrice),
                            tierState[g.id] == SeasonTier.PREMIUM,
                        ) { tierState[g.id] = SeasonTier.PREMIUM }
                    }
                }
                Text("月パスは毎月更新前提で各¥610固定。購入日（更新日）は一覧の各月パスから調整できます。",
                    color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                Text("課金センターURL", color = TEXT, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                GAMES.forEach { g ->
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = urlState[g.id] ?: "",
                        onValueChange = { urlState[g.id] = it },
                        label = { Text(g.name) },
                        singleLine = true, colors = fieldColors(),
                    )
                }
                Text(
                    "通知は約12時間ごとにバックグラウンドで判定されます。",
                    color = SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                lead.toIntOrNull()?.let(onSaveLead)
                urlState.forEach { (id, url) -> onSaveUrl(id, url.trim()) }
                GAMES.forEach { g -> onSaveTier(g.id, tierState[g.id] ?: SeasonTier.STD) }
                onDismiss()
            }) { Text("保存", color = ACCENT, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = SUB) } },
    )
}

private fun yen(n: Int) = "¥" + "%,d".format(n)

private fun gameIconRes(id: String): Int = when (id) {
    "genshin" -> R.drawable.ic_game_genshin
    "hsr" -> R.drawable.ic_game_hsr
    else -> R.drawable.ic_game_zzz
}

@Composable
private fun TierChip(modifier: Modifier, name: String, price: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        color = if (selected) ACCENT else Color(0xFF262B54),
        shape = RoundedCornerShape(10.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, LINE),
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(name, color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(price, color = if (selected) TEXT else SUB, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CalendarView(data: AppData, modifier: Modifier) {
    val today = logicalToday()
    val events = remember(data) { data.paymentEvents(today) }
    val byDay = remember(events) { events.groupBy { it.date } }
    var ym by remember { mutableStateOf(YearMonth.from(today)) }

    val total6 = remember(events) {
        val h = today.plusMonths(6)
        events.filter { !it.date.isAfter(h) }.sumOf { it.amount }
    }
    val monthEvents = events.filter { YearMonth.from(it.date) == ym }
    val monthTotal = monthEvents.sumOf { it.amount }

    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Surface(color = CARD, shape = RoundedCornerShape(12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今後6ヶ月で必要な課金の合計", color = SUB, fontSize = 13.sp)
                Text(yen(total6), color = ACCENT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { ym = ym.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, "前の月", tint = TEXT)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${ym.year}年 ${ym.monthValue}月", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("この月の合計 ${yen(monthTotal)}", color = SUB, fontSize = 12.sp)
            }
            IconButton(onClick = { ym = ym.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, "次の月", tint = TEXT)
            }
        }
        Spacer(Modifier.height(8.dp))
        CalendarGrid(ym, today, byDay)
        Spacer(Modifier.height(16.dp))
        if (monthEvents.isEmpty()) {
            Text(
                "この月に必要な課金はありません", color = SUB, fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            )
        } else {
            monthEvents.forEach { e ->
                val g = gameById(e.gameId)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(Color(g.colorHex), RoundedCornerShape(99.dp)))
                    Spacer(Modifier.width(9.dp))
                    Text("${e.date.monthValue}/${e.date.dayOfMonth}", color = SUB, fontSize = 12.sp, modifier = Modifier.width(46.dp))
                    Text(
                        "${g.name}・${e.name}（${if (e.type == PassType.MONTHLY) "月パス" else "シーズン"}）",
                        color = TEXT, fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    Text(yen(e.amount), color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = LINE)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${ym.monthValue}月 合計", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(yen(monthTotal), color = TEXT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CalendarGrid(ym: YearMonth, today: LocalDate, byDay: Map<LocalDate, List<PaymentEvent>>) {
    val dows = listOf("日", "月", "火", "水", "木", "金", "土")
    val firstDow = ym.atDay(1).dayOfWeek.value % 7   // 日=0, 月=1, ... 土=6
    val dim = ym.lengthOfMonth()
    Column {
        Row(Modifier.fillMaxWidth()) {
            dows.forEachIndexed { i, w ->
                Text(
                    w,
                    color = when (i) { 0 -> DANGER; 6 -> Color(0xFF7DB4FF); else -> SUB },
                    fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                )
            }
        }
        var day = 1
        val rows = (firstDow + dim + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c
                    if (idx < firstDow || day > dim) {
                        Box(Modifier.weight(1f).height(52.dp))
                    } else {
                        val d = ym.atDay(day)
                        val evs = byDay[d].orEmpty()
                        DayCell(Modifier.weight(1f), day, d == today, evs.map { gameById(it.gameId).colorHex }, evs.sumOf { it.amount })
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(modifier: Modifier, day: Int, isToday: Boolean, colors: List<Long>, sum: Int) {
    val has = colors.isNotEmpty()
    Column(
        modifier.height(52.dp)
            .background(if (has) Color(0xFF262B54) else CARD, RoundedCornerShape(8.dp))
            .border(1.dp, if (has) ACCENT else LINE, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Text(
            "$day", color = if (isToday) ACCENT else SUB, fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
        )
        if (has) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                colors.take(4).forEach { Box(Modifier.size(7.dp).background(Color(it), RoundedCornerShape(99.dp))) }
            }
            Spacer(Modifier.weight(1f))
            Text(yen(sum), color = TEXT, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TEXT, unfocusedTextColor = TEXT,
    focusedBorderColor = ACCENT, unfocusedBorderColor = LINE,
    focusedLabelColor = SUB, unfocusedLabelColor = SUB,
    cursorColor = ACCENT,
)
