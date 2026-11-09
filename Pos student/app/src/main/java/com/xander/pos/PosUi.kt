package com.xander.pos

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.xander.pos.palm.PalmEngine
import com.xander.pos.palm.PalmRecord
import com.xander.pos.palm.palmSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Luxury = Color(0xFFC9961B)
private val LuxurySoft = Color(0xFFE0B44A)
private val Cream = Color(0xFFFBF7F0)
private val Ink = Color(0xFF1C2430)
private val Muted = Color(0xFF6A6E77)
private val Soft = Color(0xFF8A847C)
private val Line = Color(0xFFEFE7DA)
private val FieldBg = Color(0xFFF7F5F2)
private val Chip = Color(0xFFF8E7C3)
private val Wash = Color(0xFFFDF6EB)
private val Ok = Color(0xFF0F7A45)
private val OkBg = Color(0xFFE8F7EF)
private val Danger = Color(0xFFB42318)
private val DangerBg = Color(0xFFFDECEA)
private val CardShape = RoundedCornerShape(24.dp)
private val Pill = RoundedCornerShape(28.dp)

private val imageClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
private val imageCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

@Composable
fun PosApp(model: PosModel, palm: PalmEngine) {
    val entered by model.entered.collectAsState()
    val pending by model.pending.collectAsState()
    val busy by model.busy.collectAsState()
    val overlay by model.overlay.collectAsState()
    Box(Modifier.fillMaxSize().background(Cream)) {
        SystemBars(Cream, Cream, darkIcons = true, darkNavIcons = true)
        when {
            overlay == DeskOverlay.Record -> RecordScreen(model, palm)
            overlay == DeskOverlay.Settings -> SettingsScreen(model)
            overlay == DeskOverlay.Verify -> VerifyScreen(model, palm)
            overlay == DeskOverlay.Today -> TodayScreen(model)
            pending != null -> ApproveScreen(model, palm, pending!!)
            !entered -> GateScreen(model)
            else -> SellScreen(model)
        }
        if (busy && overlay != DeskOverlay.Record && (pending != null || !entered)) {
            Box(Modifier.fillMaxSize().background(Color(0xCC1C2430)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    CircularProgressIndicator(color = LuxurySoft, modifier = Modifier.size(54.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when {
                            pending != null -> "Saving the sale…"
                            !entered -> "Finding the school…"
                            else -> "Reading the card…"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun GateScreen(model: PosModel) {
    val picking by model.pickingSchool.collectAsState()
    val brand by model.brand.collectAsState()
    val banner by model.banner.collectAsState()
    val bannerOk by model.bannerOk.collectAsState()
    var acronym by remember { mutableStateOf("") }
    if (picking || brand.id <= 0 || brand.acronym.isBlank()) {
        GoldStage {
            UnlockableLogo(model, Modifier.padding(top = 18.dp).height(64.dp).fillMaxWidth())
            Spacer(Modifier.height(22.dp))
            Text("Welcome to", color = Soft, fontSize = 15.sp)
            Text("Xander", color = Ink, fontWeight = FontWeight.Black, fontSize = 40.sp)
            Text("Canteen", color = Luxury, fontWeight = FontWeight.Black, fontSize = 32.sp)
            Box(Modifier.padding(top = 8.dp).width(56.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Luxury))
            Text(
                "Enter the acronym for this school. The till loads only that school.",
                color = Muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (banner.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Banner(banner, bannerOk)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp).clip(CardShape).background(Color.White).padding(16.dp),
            ) {
                Text("School acronym", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                CreamField(
                    value = acronym,
                    onValueChange = { acronym = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                    placeholder = "Acronym",
                    mark = "Aa",
                    capitalization = KeyboardCapitalization.Characters,
                    ime = ImeAction.Done,
                    onDone = { model.submitAcronym(acronym) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                ReaderStatus(model)
                GoldButton("Load school  →", acronym.isNotBlank(), Modifier.padding(top = 8.dp)) { model.submitAcronym(acronym) }
            }
            if (brand.id > 0 && brand.acronym.isNotBlank()) {
                Text(
                    "Back",
                    color = Color(0xFFC4922A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp).clickable { model.pickSchool(false) },
                )
            }
        }
        return
    }
    GoldStage {
        UnlockableLogo(model, Modifier.padding(top = 8.dp).height(52.dp).fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text("This till is for", color = Soft, fontSize = 15.sp)
        Text(brand.name.ifBlank { "School" }, color = Luxury, fontWeight = FontWeight.Black, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Box(Modifier.padding(top = 8.dp).width(56.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Luxury).align(Alignment.CenterHorizontally))
        if (brand.logoUrl.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            RemoteImage(brand.logoUrl, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).border(2.dp, Color(0xFFE4C56A), RoundedCornerShape(24.dp)).align(Alignment.CenterHorizontally))
        }
        if (brand.slogan.isNotBlank()) Text(brand.slogan, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        if (brand.address.isNotBlank()) Text(brand.address, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        val place = listOf(brand.pobox, brand.phone, brand.email).filter { it.isNotBlank() }.joinToString("  ·  ")
        if (place.isNotBlank()) Text(place, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        if (banner.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Banner(banner, bannerOk)
        }
        Spacer(Modifier.height(8.dp))
        ReaderStatus(model)
        GoldButton("Open the till  →", true, Modifier.padding(top = 12.dp)) { model.openTill() }
        Text(
            "Change school",
            color = Color(0xFFC4922A),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 16.dp).clickable { model.pickSchool(true) },
        )
    }
}

private fun menuNameIsLong(name: String) = name.trim().length > 18

@Composable
private fun MenuSearch(query: String, onQuery: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE6EAF0), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Find", color = Luxury, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Luxury),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search the menu", color = Color(0xFFA8ADB4), fontSize = 15.sp)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Text(
                "Clear",
                color = Luxury,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    onQuery("")
                    keyboard?.hide()
                }.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SellScreen(model: PosModel) {
    val items by model.items.collectAsState()
    val qty by model.qty.collectAsState()
    val brand by model.brand.collectAsState()
    val banner by model.banner.collectAsState()
    val bannerOk by model.bannerOk.collectAsState()
    val sold by model.sold.collectAsState()
    val short by model.shortfall.collectAsState()
    val nfc by model.nfcStatus.collectAsState()
    val palm by model.palmStatus.collectAsState()
    val total = items.sumOf { it.price * (qty[it.id] ?: 0) }
    val count = qty.values.sum()
    var query by remember { mutableStateOf("") }
    val shown = remember(items, query) {
        val needle = query.trim()
        if (needle.isEmpty()) items
        else items.filter { item ->
            item.name.contains(needle, ignoreCase = true) || item.unit.contains(needle, ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F6F8)).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                UnlockableLogo(model, Modifier.height(28.dp).width(120.dp))
                Spacer(Modifier.height(6.dp))
                Text(brand.name.ifBlank { "School" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2, lineHeight = 20.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("TODAY", color = LuxurySoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(money(sold), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
            }
        }
        Text(
            "$nfc   ·   $palm",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (banner.isNotBlank()) Banner(banner, bannerOk, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val owed = short
        if (owed != null) {
            val gap = owed.amount - owed.balance
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF1F0)).border(1.dp, Danger, RoundedCornerShape(16.dp)).padding(14.dp),
            ) {
                Text("Not enough money", color = Danger, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("${owed.name} has ${money(owed.balance)}.", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text("This order is ${money(owed.amount)}${if (owed.lines.isNotBlank()) " · ${owed.lines}" else ""}.", color = Ink, fontSize = 14.sp)
                Text("Short by ${money(gap)}. The sale was not started.", color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                Text("OK", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).clickable { model.dismissShortfall() })
            }
        }
        if (items.isEmpty()) {
            Text("The menu is empty. Add items under Canteen on the school website.", color = Muted, modifier = Modifier.weight(1f).padding(16.dp))
        } else {
            MenuSearch(query) { query = it }
            if (shown.isEmpty()) {
                Text(
                    "No item matches that search.",
                    color = Muted,
                    modifier = Modifier.weight(1f).padding(16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        shown,
                        key = { it.id },
                        span = { item -> GridItemSpan(if (menuNameIsLong(item.name)) 2 else 1) },
                    ) { item ->
                        val have = qty[item.id] ?: 0
                        val left = item.stock - have
                        val picked = have > 0
                        val wide = menuNameIsLong(item.name)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (picked) Wash else Color.White)
                                .border(if (picked) 2.dp else 1.dp, if (picked) Luxury else Color(0xFFE6EAF0), RoundedCornerShape(20.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                item.name,
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (wide) 18.sp else 16.sp,
                                lineHeight = if (wide) 22.sp else 20.sp,
                            )
                            if (item.unit.isNotBlank()) {
                                Text(
                                    item.unit,
                                    color = Luxury,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Chip).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Text(money(item.price), color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
                            Text(
                                when {
                                    item.stock <= 0 -> "Set stock on the website"
                                    left <= 0 -> "Sold out"
                                    item.unit.isBlank() -> "$left left"
                                    else -> "$left ${item.unit} left"
                                },
                                color = if (left <= 0) Danger else Muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                QtyButton("−", have > 0) { model.remove(item.id) }
                                Text(
                                    have.toString(),
                                    color = if (picked) Luxury else Ink,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(36.dp),
                                )
                                QtyButton("+", true) { model.add(item.id) }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(if (count > 0) "$count on the ticket" else "Ticket empty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "The card is read on the computer. Palm or signature opens here.",
                    color = Color(0xFFD6DBE3),
                    fontSize = 12.sp,
                )
            }
            Text(money(total), color = LuxurySoft, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        TillNav(model, "sell")
    }
}

@Composable
private fun TodayScreen(model: PosModel) {
    val report by model.report.collectAsState()
    val busy by model.reportBusy.collectAsState()
    val context = LocalContext.current
    var mode by remember { mutableStateOf("all") }
    var pickedClass by remember { mutableStateOf("") }
    var studentQuery by remember { mutableStateOf("") }
    val classes = remember(report.recent) {
        report.recent.map { it.className.ifBlank { "No class" } }.distinct().sorted()
    }
    val shown = remember(report.recent, mode, pickedClass, studentQuery) {
        when (mode) {
            "class" -> report.recent.filter { it.className.ifBlank { "No class" } == pickedClass }
            "student" -> {
                val q = studentQuery.trim()
                if (q.length < 2) emptyList() else report.recent.filter { it.name.contains(q, ignoreCase = true) }
            }
            else -> report.recent
        }
    }
    val grouped = remember(shown) { shown.groupBy { it.className.ifBlank { "No class" } }.toSortedMap() }
    val filteredSales = shown.sumOf { it.amount }
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F6F8))) {
        Row(
            Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("TODAY", color = LuxurySoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(report.label.ifBlank { "Canteen" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
            }
            Text(
                "Export",
                color = LuxurySoft,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { shareDailyReport(context, report.label, shown) }.padding(end = 14.dp),
            )
            Text(
                if (busy) "Loading…" else "Refresh",
                color = LuxurySoft,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !busy) { model.refreshToday() },
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (report.error.isNotBlank()) {
                Text(report.error, color = Danger, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayChoice("All", mode == "all", Modifier.weight(1f)) { mode = "all" }
                PayChoice("Class", mode == "class", Modifier.weight(1f)) {
                    mode = "class"
                    if (pickedClass.isBlank()) pickedClass = classes.firstOrNull().orEmpty()
                }
                PayChoice("Student", mode == "student", Modifier.weight(1f)) { mode = "student" }
            }
            if (mode == "class") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    classes.forEach { name ->
                        PayChoice(name, pickedClass == name) { pickedClass = name }
                    }
                }
            }
            if (mode == "student") {
                CreamField(
                    value = studentQuery,
                    onValueChange = { studentQuery = it.take(40) },
                    placeholder = "Student name",
                    mark = "Aa",
                    capitalization = KeyboardCapitalization.Words,
                    ime = ImeAction.Search,
                    onDone = {},
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportTile("Sales", money(if (mode == "all") report.sales else filteredSales), Modifier.weight(1f))
                ReportTile("Tickets", (if (mode == "all") report.invoices else shown.size).toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportTile("Students", (if (mode == "all") report.students else shown.map { it.name }.distinct().size).toString(), Modifier.weight(1f))
                ReportTile("Portions", report.portions.toString(), Modifier.weight(1f))
            }
            if (mode == "all") {
                Text("Sold", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                if (report.items.isEmpty()) {
                    Text("Nothing sold yet today.", color = Muted)
                }
                report.items.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, color = Ink, fontWeight = FontWeight.Bold)
                            if (line.unit.isNotBlank()) Text(line.unit, color = Luxury, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${line.qty}", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(money(line.amount), color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            Text("By class", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            if (shown.isEmpty()) {
                Text(
                    if (mode == "student" && studentQuery.trim().length < 2) "Type a student name." else "No tickets for this filter.",
                    color = Muted,
                )
            }
            grouped.forEach { (className, tickets) ->
                Text(
                    "$className  ·  ${money(tickets.sumOf { it.amount })}",
                    color = Luxury,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
                tickets.forEach { ticket ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ticket.name, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(money(ticket.amount), color = Luxury, fontWeight = FontWeight.Black)
                        }
                        Text(
                            listOf(ticket.at, ticket.items).filter { it.isNotBlank() }.joinToString("  ·  "),
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        TillNav(model, "today")
    }
}

private fun shareDailyReport(context: android.content.Context, label: String, tickets: List<TillTicket>) {
    val title = label.ifBlank { "Canteen" }
    val body = buildString {
        append("Daily sales, ")
        append(title)
        append("\nClass,Student,Time,Items,Amount\n")
        tickets.forEach { ticket ->
            append(csv(ticket.className.ifBlank { "No class" }))
            append(',')
            append(csv(ticket.name))
            append(',')
            append(csv(ticket.at))
            append(',')
            append(csv(ticket.items))
            append(',')
            append(ticket.amount)
            append('\n')
        }
        append("Total,,,,")
        append(tickets.sumOf { it.amount })
        append('\n')
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Daily sales $title")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(send, "Export daily sales"))
}

private fun csv(value: String): String {
    val clean = value.replace("\"", "\"\"")
    return "\"$clean\""
}

@Composable
private fun ReportTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun VerifyScreen(model: PosModel, palm: PalmEngine) {
    val palms by model.palms.collectAsState()
    val verified by model.verified.collectAsState()
    val note by model.verifyNote.collectAsState()
    LaunchedEffect(palms.isNotEmpty()) {
        if (palms.isNotEmpty()) palm.ensureRunning()
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F6F8))) {
        Row(
            Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("VERIFY", color = LuxurySoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Verify palm", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Text(
                "Record",
                color = LuxurySoft,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { model.openRecord() },
            )
        }
        Text(
            note,
            color = if (verified != null) Ok else Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 6.dp),
        )
        verified?.let { face ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(2.dp, Luxury, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FacePhoto(face.photoUrl, face.name, 52.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(face.name, color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2)
                    if (face.className.isNotBlank()) Text(face.className, color = Muted, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        if (palms.isEmpty()) {
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text("No palms recorded yet.", color = Muted)
                Text(
                    "Record a palm",
                    color = Luxury,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable { model.openRecord() },
                )
            }
        } else {
            PalmPreview(
                palm,
                Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Luxury, RoundedCornerShape(16.dp)),
            )
        }
        TillNav(model, "verify")
    }
}

@Composable
private fun FacePhoto(url: String, name: String, size: androidx.compose.ui.unit.Dp) {
    if (url.isNotBlank()) {
        RemoteImage(url, Modifier.size(size).clip(CircleShape).border(2.dp, Luxury, CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Luxury), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TillNav(model: PosModel, current: String) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavChip("Register", current == "sell") { model.closeSettings() }
        NavChip("Today", current == "today") { model.openToday() }
        NavChip("Verify palm", current == "verify") { model.openVerify() }
    }
}

@Composable
private fun NavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.White else Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink else Color(0xFFF4F6F8))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsScreen(model: PosModel) {
    val palms by model.palms.collectAsState()
    val faces by model.faces.collectAsState()
    val nfc by model.nfcStatus.collectAsState()
    val palm by model.palmStatus.collectAsState()
    var confirmUid by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var byCard by remember { mutableStateOf(true) }
    val photos = remember(faces) { faces.associateBy { it.uid.uppercase() } }
    val shown = remember(palms, query, byCard) { palms.filter { palmMatches(it, query, byCard) } }
    Column(Modifier.fillMaxSize().background(Cream).imePadding()) {
        ScreenHead("Palms", "Till") { model.closeSettings() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recorded palms", color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text(
                    palms.size.toString(),
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Chip).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                if (byCard) "Card search is on. Untick it to search by name." else "Name search is on. Tick Card to search by card.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            GoldButton("Record a palm  →", true) { model.openRecord() }
            PalmSearch(query, byCard, { query = it.take(40) }) { byCard = it }
            Text(
                listOf(nfc, palm).filter { it.isNotBlank() }.joinToString("   ·   "),
                color = Soft,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Change school",
                color = Color(0xFFC4922A),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp).clickable { model.pickSchool(true) },
            )
        }
        when {
            palms.isEmpty() -> Text("No palms recorded yet.", color = Muted, modifier = Modifier.padding(horizontal = 16.dp).weight(1f))
            shown.isEmpty() -> Text("No palm matches “${query.trim()}”.", color = Muted, modifier = Modifier.padding(horizontal = 16.dp).weight(1f))
            else -> LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isNotBlank()) {
                    item {
                        Text(
                            "${shown.size} of ${palms.size}",
                            color = Soft,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                items(shown, key = { it.uid }) { record ->
                    PalmRow(
                        record,
                        photos[record.uid.uppercase()]?.photoUrl.orEmpty(),
                        confirmUid.equals(record.uid, ignoreCase = true),
                        { confirmUid = record.uid },
                        {
                            model.deletePalm(record.uid)
                            confirmUid = ""
                        },
                        { confirmUid = "" },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
        TillNav(model, "verify")
    }
}

@Composable
private fun PalmSearch(query: String, byCard: Boolean, onQuery: (String) -> Unit, onByCard: (Boolean) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE6EAF0), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Find", color = Luxury, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(Luxury),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (byCard) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                if (byCard) "Card number" else "Student name",
                                color = Color(0xFFA8ADB4),
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Text(
                    "Clear",
                    color = Luxury,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        onQuery("")
                        keyboard?.hide()
                    }.padding(start = 8.dp),
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { onByCard(!byCard) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (byCard) Luxury else Color.White).border(1.5.dp, if (byCard) Luxury else Line, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (byCard) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                "Card",
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                if (byCard) "Searching by card" else "Searching by name",
                color = Soft,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun palmMatches(record: PalmRecord, query: String, byCard: Boolean): Boolean {
    val raw = query.trim()
    if (raw.isEmpty()) return true
    val tokens = raw.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (byCard) {
        val hex = record.uid.uppercase().filter { it.isDigit() || it in 'A'..'F' }.lowercase()
        val reversed = if (hex.length >= 4 && hex.length % 2 == 0) hex.chunked(2).reversed().joinToString("") else ""
        return tokens.all { token ->
            val compact = token.filter { it.isDigit() || it in 'a'..'f' }
            compact.length >= 2 && (hex.contains(compact) || reversed.contains(compact))
        }
    }
    val name = record.name.lowercase()
    return tokens.all { name.contains(it) }
}

@Composable
private fun PalmRow(
    record: PalmRecord,
    photoUrl: String,
    confirming: Boolean,
    onAsk: () -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val synced = record.syncedAt >= record.savedAt && record.savedAt > 0L
    val samples = if (palmSamples(record.feature).size >= 3) "3 samples" else "On file"
    Column(
        Modifier.fillMaxWidth().shadow(4.dp, CardShape, ambientColor = Color(0x14C9961B), spotColor = Color(0x14C9961B)).clip(CardShape).background(Color.White).border(1.dp, Line, CardShape).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FacePhoto(photoUrl, record.name, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.name.ifBlank { "Student" }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val place = listOf(record.className, record.uid).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (place.isNotBlank()) Text(place, color = Muted, fontSize = 13.sp)
                Text("${palmWhen(record.savedAt)}  ·  $samples", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (synced) "Backed up" else "Waiting to back up",
                color = if (synced) Ok else Luxury,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (synced) OkBg else Wash).padding(horizontal = 10.dp, vertical = 5.dp),
            )
            if (confirming) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Danger).clickable(onClick = onDelete).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        "Keep",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).clickable(onClick = onKeep).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Text(
                    "Delete",
                    color = Danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onAsk).padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StudentSearch(model: PosModel, note: String, modifier: Modifier = Modifier) {
    val hits by model.studentHits.collectAsState()
    val searched by model.searched.collectAsState()
    val searching by model.searching.collectAsState()
    var query by remember { mutableStateOf("") }
    var byCard by remember { mutableStateOf(true) }
    LaunchedEffect(query, byCard) { model.searchStudents(query, byCard) }
    Column(modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Find the student", color = Ink, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text(
            if (byCard) "Card search is on. Untick it to search by name, or tap the card." else "Name search is on. Tick Card to search by card, or tap the card.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        CreamField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = if (byCard) "Card number" else "Student name",
            mark = if (byCard) "#" else "Aa",
            capitalization = if (byCard) KeyboardCapitalization.Characters else KeyboardCapitalization.Words,
            ime = ImeAction.Search,
            onDone = { model.searchStudents(query, byCard) },
            modifier = Modifier.padding(top = 14.dp),
        )
        Row(
            Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable { byCard = !byCard }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (byCard) Luxury else Color.White).border(1.5.dp, if (byCard) Luxury else Line, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (byCard) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text("Card", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            Text(
                if (byCard) "Searching by card" else "Searching by name",
                color = Soft,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (searching) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Luxury, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Searching…", color = Ink, fontWeight = FontWeight.Bold)
            }
        } else if (query.trim().length >= 2 && query.trim() == searched && hits.isEmpty()) {
            Text("No student matches that search.", color = Muted, modifier = Modifier.padding(top = 12.dp))
        }
        if (note.isNotBlank()) {
            Text(
                note,
                color = when {
                    note.startsWith("Palm saved") -> Ok
                    note.startsWith("No card") || note.contains("not found") || note.startsWith("Could not") || note.startsWith("Cannot reach") || note.startsWith("Choose the school") -> Danger
                    else -> Muted
                },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hits, key = { it.id }) { hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, CardShape, ambientColor = Color(0x14C9961B), spotColor = Color(0x14C9961B))
                        .clip(CardShape)
                        .background(Color.White)
                        .border(1.dp, Line, CardShape)
                        .clickable { model.chooseStudent(hit) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hit.photoUrl.isNotBlank()) {
                        RemoteImage(hit.photoUrl, Modifier.size(52.dp).clip(CircleShape).border(2.dp, Color(0xFFE4C56A), CircleShape))
                    } else {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Luxury), contentAlignment = Alignment.Center) {
                            Text(hit.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hit.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        val detail = listOf(hit.regno, hit.className).filter { it.isNotBlank() }.joinToString(" · ")
                        if (detail.isNotBlank()) Text(detail, color = Muted, fontSize = 13.sp)
                        Text(
                            if (hit.cardUid.isBlank()) "No card saved" else "Card ${hit.cardUid}",
                            color = if (hit.cardUid.isBlank()) Danger else Ok,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "Record",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.background(Brush.horizontalGradient(listOf(LuxurySoft, Luxury)), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordScreen(model: PosModel, palm: PalmEngine) {
    val capture by model.capture.collectAsState()
    val palmReady by model.palmReady.collectAsState()
    val busy by model.busy.collectAsState()
    LaunchedEffect(capture.phase, capture.uid) {
        if (capture.phase == RecordPhase.Capturing && capture.uid.length >= 4) palm.ensureRunning()
    }
    Column(Modifier.fillMaxSize().background(Cream)) {
        ScreenHead("Record palm vein", "Back", enabled = !busy) { model.closeRecord() }
        if (busy) {
            Row(
                Modifier.fillMaxWidth().background(Wash).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Luxury, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Finding the student…", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        if (capture.phase == RecordPhase.WaitCard) {
            StudentSearch(model, capture.note, Modifier.weight(1f).fillMaxWidth())
        } else if (capture.phase == RecordPhase.ConfirmChange) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FacePhoto(capture.photoUrl, capture.name, 84.dp)
                Text(
                    capture.name.ifBlank { "Student" },
                    color = Ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
                val place = listOf(capture.className, if (capture.uid.isNotBlank()) "Card ${capture.uid}" else "").filter { it.isNotBlank() }
                if (place.isNotEmpty()) {
                    Text(place.joinToString("  ·  "), color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                Text(
                    "A palm is already saved for this student.",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Change replaces the palm on this till. Keep leaves it as it is.",
                    color = Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))
                GoldButton("Change palm  →", !busy) { model.confirmChangePalm() }
                Text(
                    "Keep this palm",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !busy) { model.keepExistingPalm() }.padding(16.dp),
                )
            }
        } else {
            val scanning = capture.uid.length >= 4 && capture.phase != RecordPhase.Saved
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (capture.photoUrl.isNotBlank()) {
                        RemoteImage(capture.photoUrl, Modifier.size(36.dp).clip(CircleShape).border(2.dp, Luxury, CircleShape))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(capture.name.ifBlank { "Student" }, color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
                        val meta = listOf(capture.className, if (capture.uid.isNotBlank()) "Card ${capture.uid}" else "").filter { it.isNotBlank() }
                        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (capture.uid.length >= 4) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { index ->
                            val done = capture.taken > index || capture.phase == RecordPhase.Saved
                            val active = !done && capture.taken == index && capture.phase == RecordPhase.Capturing
                            SampleStep(index + 1, active, done, Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    capture.note,
                    color = if (capture.phase == RecordPhase.Failed) Danger else if (scanning && palmReady) Ok else Ink,
                    fontWeight = if (scanning) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                if (capture.phase == RecordPhase.Failed) {
                    GoldButton("Retry", true, Modifier.fillMaxWidth().padding(bottom = 6.dp)) { model.retryRecord() }
                }
                if (scanning) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PalmPreview(
                            palm,
                            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).border(2.dp, Luxury, RoundedCornerShape(16.dp)),
                        )
                        if (capture.phase == RecordPhase.Capturing) {
                            Row(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.94f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(color = Luxury, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Reading sample ${capture.taken + 1} of 3",
                                    color = Ink,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
                if (capture.phase == RecordPhase.Capturing || capture.phase == RecordPhase.Failed) {
                    Text(
                        "Change student",
                        color = Color(0xFFC4922A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !busy) { model.searchAgain() }.padding(top = 8.dp),
                    )
                }
                if (capture.phase == RecordPhase.Saved) {
                    Spacer(Modifier.height(12.dp))
                    GoldButton("Record another  →", true) { model.recordAnother() }
                    Text("Done", color = Soft, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { model.closeRecord() }.padding(14.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        TillNav(model, "record")
    }
}

@Composable
private fun SampleStep(number: Int, active: Boolean, done: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Wash else Color.White)
            .border(if (active || done) 2.dp else 1.dp, if (done) Ok else if (active) Luxury else Line, RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) Ok else if (active) Luxury else FieldBg),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (done) "✓" else number.toString(),
                    color = if (done) Color.White else Muted,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
            }
        }
        Text("Sample $number", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Text(
            when {
                done -> "Saved"
                active -> "Reading"
                else -> "Next"
            },
            color = if (done) Ok else if (active) Luxury else Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ApproveScreen(model: PosModel, palm: PalmEngine, sale: PendingSale) {
    val brand by model.brand.collectAsState()
    val note by model.palmNote.collectAsState()
    val palms by model.palms.collectAsState()
    val method by model.confirm.collectAsState()
    val busy by model.busy.collectAsState()
    val recorded = remember(palms, sale.uid) { model.hasPalm(sale.uid) }
    val leftAfter = sale.balance - sale.amount
    var drawn by remember(sale.uid) { mutableStateOf(false) }
    var board by remember(sale.uid) { mutableStateOf<SignBoard?>(null) }
    LaunchedEffect(sale.uid, recorded, method) {
        if (method == ConfirmMethod.Palm && recorded) palm.ensureRunning()
    }
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XanderLogo(Modifier.height(22.dp).width(96.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(brand.name.ifBlank { "School" }, color = Luxury, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 2)
            }
            Text(
                "Verify",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clip(Pill).background(Ink).clickable { model.openVerify() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(Color.White).border(1.dp, Line, CardShape).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(sale.photoUrl, Modifier.size(42.dp).clip(CircleShape).border(2.dp, Luxury, CircleShape))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(sale.name, color = Ink, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                    if (sale.className.isNotBlank()) Text(sale.className, color = Muted, fontSize = 11.sp, maxLines = 1)
                    Text(sale.lines, color = Muted, fontSize = 11.sp, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(sale.amount), color = Luxury, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        if (sale.walletReady) "Wallet ${money(sale.balance)}" else "Wallet …",
                        color = Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (sale.walletReady) "Left ${money(leftAfter)}" else "",
                        color = Ok,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                note.ifBlank { if (method == ConfirmMethod.Signature) "Draw the signature." else "Place the recorded palm." },
                color = Ink,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                maxLines = 3,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            if (method == ConfirmMethod.Signature) {
                Text("Sign inside the box", color = Muted, fontSize = 12.sp)
                AndroidView(
                    factory = { ctx ->
                        SignBoard(ctx).also { created ->
                            board = created
                            created.onInk = { drawn = it }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Luxury, RoundedCornerShape(16.dp)),
                    update = { view ->
                        board = view
                        view.onInk = { drawn = it }
                    },
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Clear",
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(Pill).border(1.dp, Line, Pill).clickable {
                            board?.clear()
                            drawn = false
                        }.padding(vertical = 12.dp),
                    )
                    Box(Modifier.weight(1.4f)) {
                        GoldButton("Confirm", drawn && !busy) {
                            model.chargeWithSignature(board?.png().orEmpty())
                        }
                    }
                }
            } else if (recorded) {
                Text(
                    "Change palm",
                    color = Luxury,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { model.recordThisStudent() }.padding(bottom = 6.dp),
                )
                PalmPreview(
                    palm,
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(2.dp, Luxury, RoundedCornerShape(16.dp)),
                )
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No palm is saved for ${sale.name}.", color = Danger, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Text("Choose Signature, or record a palm first.", color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayChoice("Palm", method == ConfirmMethod.Palm, Modifier.weight(1f)) { model.chooseConfirm(sale.uid, ConfirmMethod.Palm) }
            PayChoice("Signature", method == ConfirmMethod.Signature, Modifier.weight(1f)) { model.chooseConfirm(sale.uid, ConfirmMethod.Signature) }
        }
        Text(
            "Cancel",
            color = Ink,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(Pill).border(1.dp, Color(0xFFE4C56A), Pill).clickable { model.cancelSale() }.padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun PayChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.White else Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(Pill)
            .background(if (selected) Ink else Color.White)
            .border(1.dp, if (selected) Ink else Line, Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ScreenHead(title: String, action: String, enabled: Boolean = true, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XanderLogo(Modifier.height(28.dp).width(120.dp))
            Text(title, color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            action,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(Pill).background(Brush.horizontalGradient(listOf(LuxurySoft, Luxury))).clickable(enabled = enabled, onClick = onAction).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GoldStage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Cream)) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(-12f, pivot = Offset(size.width * 0.2f, 30f)) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFF3D98A), Luxury)),
                    topLeft = Offset(-90f, -30f),
                    size = Size(size.width * 0.7f, 150f),
                    cornerRadius = CornerRadius(80f, 80f),
                )
            }
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFE8C56A))),
                topLeft = Offset(size.width * 0.5f, -16f),
                size = Size(size.width * 0.62f, 120f),
                cornerRadius = CornerRadius(70f, 70f),
            )
            rotate(-3f, pivot = Offset(size.width / 2f, size.height)) {
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFE7C56B), Luxury, Color(0xFFF0D48A))),
                    topLeft = Offset(-30f, size.height - 130f),
                    size = Size(size.width + 60f, 190f),
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

@Composable
private fun UnlockableLogo(model: PosModel, modifier: Modifier = Modifier) {
    var taps by remember { mutableIntStateOf(0) }
    var started by remember { mutableLongStateOf(0L) }
    var ask by remember { mutableStateOf(false) }
    XanderLogo(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            val now = System.currentTimeMillis()
            if (started == 0L || now - started > 1800L) {
                started = now
                taps = 0
            }
            taps += 1
            if (taps >= 3) {
                taps = 0
                started = 0L
                ask = true
            }
        },
    )
    if (ask) {
        SettingsPasswordDialog(
            onDismiss = { ask = false },
            onGranted = {
                ask = false
                model.openSettings()
            },
        )
    }
}

@Composable
private fun SettingsPasswordDialog(onDismiss: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var pass by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    fun submit() {
        if (pass == "HFSecurity") onGranted() else {
            pass = ""
            Toast.makeText(context, "Wrong password", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter password to continue", color = Muted)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    placeholder = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (visible) KeyboardType.Text else KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { visible = !visible },
                            contentAlignment = Alignment.Center,
                        ) {
                            PasswordEye(visible)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Open", color = Luxury, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}

@Composable
private fun PasswordEye(open: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val color = Color(0xFF5C6B80)
        drawOval(color = color, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = color, radius = size.minDimension * 0.16f, center = center)
        if (!open) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.12f, size.height * 0.82f),
                end = Offset(size.width * 0.88f, size.height * 0.18f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun QtyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (label == "+" && enabled) Luxury else Color.White)
            .border(1.5.dp, if (enabled) Luxury else Line, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> Soft
                label == "+" -> Color.White
                else -> Luxury
            },
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun XanderLogo(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        Image(
            painterResource(R.drawable.xander_logo_gold),
            contentDescription = "Xander Tech",
            modifier = Modifier.fillMaxWidth().height(maxHeight),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
        )
    }
}

@Composable
private fun CreamField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mark: String,
    capitalization: KeyboardCapitalization,
    ime: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(FieldBg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Chip), contentAlignment = Alignment.Center) {
            Text(mark, color = Luxury, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Luxury),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ime),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }),
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFFA8ADB4), fontSize = 16.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun GoldButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(Pill).background(Brush.horizontalGradient(listOf(LuxurySoft, Luxury))).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.65f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun PalmPreview(palm: PalmEngine, modifier: Modifier) {
    val vein by palm.frame.collectAsState()
    Box(modifier.clipToBounds().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    palm.attachHost(this)
                }
            },
            update = { palm.attachHost(it) },
            onRelease = { palm.detachHost(it) },
        )
        if (vein != null) {
            Image(
                vein!!.asImageBitmap(),
                contentDescription = "Infrared palm",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun RemoteImage(url: String, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf(imageCache[url]) }
    LaunchedEffect(url) {
        if (url.isBlank() || bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            imageCache[url] ?: try {
                val response = imageClient.newCall(Request.Builder().url(url).build()).execute()
                response.use { res ->
                    if (!res.isSuccessful) return@use null
                    val decoded = BitmapFactory.decodeStream(res.body?.byteStream()) ?: return@use null
                    imageCache[url] = decoded
                    decoded
                }
            } catch (error: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(FieldBg), contentAlignment = Alignment.Center) {
            Text(if (url.isBlank()) "—" else "…", color = Muted)
        }
    }
}

@Composable
private fun ReaderStatus(model: PosModel) {
    val nfc by model.nfcStatus.collectAsState()
    Text(
        "$nfc. This POS reads the student card on the contactless pad. It has no phone NFC switch in Android settings.",
        color = Muted,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun Banner(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        color = if (ok) Ok else Danger,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ok) OkBg else DangerBg).padding(12.dp),
    )
}

@Composable
private fun SystemBars(status: Color, navigation: Color = status, darkIcons: Boolean = false, darkNavIcons: Boolean = darkIcons) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = status.toArgb()
        window.navigationBarColor = navigation.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkNavIcons
    }
}

private fun palmWhen(millis: Long): String {
    if (millis <= 0L) return "Recorded on this till"
    return "Recorded " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date(millis))
}
