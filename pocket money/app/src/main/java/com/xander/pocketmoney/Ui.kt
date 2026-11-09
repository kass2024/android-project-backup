package com.xander.pocketmoney

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.Calendar

private val Blue = Color(0xFF1565D8)
private val BlueDeep = Color(0xFF0B49B0)
private val BlueDark = Color(0xFF121417)
private val Orange = Color(0xFFFF6A1A)
private val OrangeSoft = Color(0xFFFFF1E8)
private val Navy = Blue
private val Navy2 = BlueDeep
private val Gold = Orange
private val Ink = Color(0xFF142033)
private val Muted = Color(0xFF5C6B80)
private val Line = Color(0xFFD7E0EC)
private val Paper = Color(0xFFF4F7FB)
private val Card = Color.White
private val Ok = Color(0xFF0F7A45)
private val OkBg = Color(0xFFE8F7EF)
private val Danger = Color(0xFFB42318)
private val DangerBg = Color(0xFFFDECEA)
private val Shape = RoundedCornerShape(20.dp)
private val Pill = RoundedCornerShape(16.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketApp() }
    }
}

@Composable
fun PocketApp(model: PocketModel = viewModel()) {
    val destination by model.destination.collectAsState()
    val parent by model.parent.collectAsState()
    val students by model.students.collectAsState()
    val ledger by model.ledger.collectAsState()
    val busy by model.busy.collectAsState()
    val error by model.error.collectAsState()
    val notice by model.notice.collectAsState()
    val found by model.found.collectAsState()
    val matches by model.matches.collectAsState()
    val searching by model.searching.collectAsState()
    val lang by model.lang.collectAsState()
    val payMessage by model.payMessage.collectAsState()
    val copy = appCopy(lang)
    val sending by model.sending.collectAsState()
    val family by model.family.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && model.parent.value != null) {
                model.refresh()
                model.startLive()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalCopy provides copy) {
    Box(Modifier.fillMaxSize().background(Paper)) {
        when (val screen = destination) {
            Destination.Auth -> AuthScreen(busy, error, notice, lang, model)
            Destination.Home -> HomeScreen(parent, students, found, matches, searching, busy, error, notice, model)
            is Destination.Pay -> {
                val student = students.firstOrNull { it.id == screen.studentId }
                if (student == null) model.open(Destination.Home) else PayScreen(student, parent?.phone.orEmpty(), busy, error, payMessage, model)
            }
            is Destination.Activity -> {
                val student = students.firstOrNull { it.id == screen.studentId }
                if (student == null) model.open(Destination.Home) else ActivityScreen(student, ledger, busy, error, notice, model)
            }
        }
        val welcome = family
        if (welcome != null && destination is Destination.Home) {
            FamilyWelcomeScreen(welcome) { model.dismissFamily() }
        }
        if (sending) {
            Box(Modifier.fillMaxSize().background(BlueDark.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    CircularProgressIndicator(color = Orange, modifier = Modifier.size(54.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(if (payMessage.isBlank()) copy.sending else payMessage, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
    }
}

@Composable
private fun AuthScreen(busy: Boolean, error: String, notice: String, lang: String, model: PocketModel) {
    val chosen by model.langChosen.collectAsState()
    var stage by remember { mutableStateOf("welcome") }
    var mode by remember { mutableStateOf("signin") }
    var resetStep by remember { mutableStateOf(1) }
    var createStep by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    BackHandler(enabled = stage == "form" || (stage == "language" && chosen)) {
        if (stage == "language") {
            stage = "welcome"
        } else if (mode == "forgot" && resetStep == 2) {
            resetStep = 1
            code = ""
            pin = ""
        } else if (mode == "create" && createStep == 2) {
            createStep = 1
            code = ""
            model.clearMessages()
        } else {
            stage = "welcome"
            mode = "signin"
            resetStep = 1
            createStep = 1
            model.clearMessages()
        }
    }
    when (stage) {
        "language" -> LanguageScreen(
            lang = lang,
            showBack = chosen,
            onBack = { stage = "welcome" },
            onPick = { model.setLang(it) },
            onContinue = {
                model.confirmLang(lang)
                stage = "welcome"
            },
        )
        "welcome" -> WelcomeScreen(
            lang = lang,
            onPick = { model.setLang(it) },
            onContinue = {
                model.confirmLang(lang)
                mode = "signin"
                resetStep = 1
                createStep = 1
                model.clearMessages()
                stage = "form"
            },
        )
        else -> AuthForm(
            busy = busy,
            error = error,
            notice = notice,
            mode = mode,
            resetStep = resetStep,
            createStep = createStep,
            name = name,
            phone = phone,
            pin = pin,
            code = code,
            onName = { name = it },
            onPhone = { phone = it },
            onPin = { pin = it },
            onCode = { code = it },
            onResetStep = { resetStep = it },
            onCreateStep = { createStep = it },
            onMode = { mode = it },
            onBack = {
                if (mode == "forgot" && resetStep == 2) {
                    resetStep = 1
                    code = ""
                    pin = ""
                } else if (mode == "create" && createStep == 2) {
                    createStep = 1
                    code = ""
                    model.clearMessages()
                } else {
                    stage = "welcome"
                    mode = "signin"
                    resetStep = 1
                    model.clearMessages()
                }
            },
            model = model,
        )
    }
}

@Composable
private fun LanguageScreen(
    lang: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val copy = LocalCopy.current
    Column(Modifier.fillMaxSize().background(BlueDark)) {
        SystemBars(BlueDark)
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (showBack) {
                Text(
                    "‹",
                    color = Color.White,
                    fontSize = 32.sp,
                    modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(horizontal = 16.dp),
                )
            }
            Text(
                copy.welcomeTo + " " + copy.appName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 52.dp),
            )
        }
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 18.dp)) {
            Text(copy.chooseLanguage, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "English · Kiswahili · Français",
                color = Color(0xFFAEB6C2),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
        }
        listOf("en" to "English", "sw" to "Kiswahili", "fr" to "Français").forEach { (code, label) ->
            LanguageOption(label, lang == code) { onPick(code) }
        }
        Spacer(Modifier.weight(1f))
        OrangeButton(copy.continueAction, true, Modifier.padding(20.dp), onContinue)
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(26.dp).border(2.dp, Orange, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) {
                    Box(Modifier.matchParentSize().clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
        Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
    }
}

private val WelcomePaper = Color(0xFFF6F1E6)

@Composable
private fun WelcomeScreen(lang: String, onPick: (String) -> Unit, onContinue: () -> Unit) {
    val copy = LocalCopy.current
    var menu by remember { mutableStateOf(false) }
    GoldStage {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LangPill(lang, menu, { menu = !menu }, { onPick(it); menu = false })
        }
        GoldWordmark(compact = false)
        Spacer(Modifier.height(28.dp))
        Text(copy.welcomeTo, color = Color(0xFF8A847C), fontSize = 15.sp)
        Text("Xander", color = Color(0xFF1C2430), fontWeight = FontWeight.Black, fontSize = 40.sp)
        Text("Pocket Money", color = Luxury, fontWeight = FontWeight.Black, fontSize = 32.sp)
        Box(Modifier.padding(top = 8.dp).width(56.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Luxury))
        Text(copy.welcomeBody, color = Color(0xFF6A6E77), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LangChoice("English", lang == "en", { onPick("en") }, { UkFlag(Modifier.size(40.dp)) }, Modifier.weight(1f))
            LangChoice("Kiswahili", lang == "sw", { onPick("sw") }, { SwFlag(Modifier.size(40.dp)) }, Modifier.weight(1f))
            LangChoice("Français", lang == "fr", { onPick("fr") }, { FrFlag(Modifier.size(40.dp)) }, Modifier.weight(1f))
        }
        GoldPill(copy.continueAction + "  →", true, Modifier.padding(top = 20.dp), onContinue)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignInArtwork(
    phone: String,
    pin: String,
    busy: Boolean,
    error: String,
    onPhone: (String) -> Unit,
    onPin: (String) -> Unit,
    onBack: () -> Unit,
    onForgot: () -> Unit,
    onCreate: () -> Unit,
    onLang: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val copy = LocalCopy.current
    var menu by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf("") }
    val pinFocus = remember { FocusRequester() }
    var pinFocused by remember { mutableStateOf(false) }
    val phoneBring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val ready = !busy && phone.filter { it.isDigit() }.length >= 10 && pin.length == 4
    GoldStage {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = Color(0xFF1C1C1C), fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            LangPill(copy.code, menu, { menu = !menu }, { onLang(it); menu = false })
        }
        GoldWordmark(compact = true)
        Spacer(Modifier.height(22.dp))
        if (copy.code != "en") {
            Text(copy.signIn, color = Color(0xFF1C2430), fontWeight = FontWeight.Black, fontSize = 34.sp)
        } else {
            Row {
                Text("Sign ", color = Color(0xFF1C2430), fontWeight = FontWeight.Black, fontSize = 40.sp)
                Text("in", color = Luxury, fontWeight = FontWeight.Black, fontSize = 40.sp)
            }
        }
        Text(copy.signInLead, color = Color(0xFF6A707C), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
        if (error.isNotBlank()) {
            Text(error, color = Danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(16.dp),
        ) {
            Text(copy.phone, color = Color(0xFF12151C), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF7F5F2)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8E7C3)), contentAlignment = Alignment.Center) {
                    Text("☎", color = Luxury, fontSize = 14.sp)
                }
                BasicTextField(
                    value = phone,
                    onValueChange = { onPhone(it.filter { ch -> ch.isDigit() }.take(12)) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF1C1C1C), fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color(0xFF1C1C1C)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.padding(start = 10.dp).weight(1f).bringIntoViewRequester(phoneBring).onFocusEvent { if (it.isFocused) scope.launch { phoneBring.bringIntoView() } },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (phone.isEmpty()) Text("07xxxxxxxx", color = Color(0xFFA8ADB4), fontSize = 16.sp)
                            inner()
                        }
                    },
                )
            }
            Text("PIN  ${copy.pinHelp}", color = Color(0xFF12151C), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            BasicTextField(
                value = pin,
                onValueChange = { onPin(it.filter { ch -> ch.isDigit() }.take(4)) },
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.size(1.dp).focusRequester(pinFocus).onFocusChanged { pinFocused = it.isFocused },
            )
            val pinSlot = if (pinFocused) pin.length.coerceAtMost(3) else -1
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { index ->
                    val active = index == pinSlot
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) Color(0xFFFFF4D4) else Color.White)
                            .border(if (active) 2.5.dp else 1.dp, if (active) Color(0xFFC9961B) else Color(0xFFE4C56A), RoundedCornerShape(16.dp))
                            .clickable { pinFocus.requestFocus() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index < pin.length) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFC79A27)))
                        } else if (active) {
                            PinCaret()
                        } else {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD5D8DE)))
                        }
                    }
                }
            }
            GoldPill(if (busy) copy.pleaseWait else copy.signIn + "  →", ready, Modifier.padding(top = 16.dp), onSubmit)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE6E1D8)))
                Text(copy.orWord, color = Color(0xFF8A909C), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE6E1D8)))
            }
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFFDF6EB)).clickable { finger = copy.fingerprintLater },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◉", color = Luxury, fontSize = 18.sp)
                Text(copy.useFingerprint, color = Color(0xFF141C28), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                Text("›", color = Luxury, fontSize = 18.sp)
            }
            if (finger.isNotBlank()) {
                Text(finger, color = Color(0xFF8A8175), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(copy.forgotPin, color = Color(0xFFC4922A), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onForgot))
            Text(copy.switchToCreate, color = Color(0xFFC4922A), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f).clickable(onClick = onCreate))
        }
    }
}

private val Luxury = Color(0xFFC9961B)
private val LuxurySoft = Color(0xFFE0B44A)

@Composable
private fun GoldStage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFFBF7F0))) {
        SystemBars(Color(0xFFFBF7F0), Color(0xFFFBF7F0), darkIcons = true, darkNavIcons = true)
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 18.dp).padding(bottom = 80.dp),
            content = { content() },
        )
    }
}

@Composable
private fun GoldWordmark(compact: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(if (compact) 56.dp else 78.dp, if (compact) 40.dp else 56.dp)) {
            val stroke = Stroke(width = if (compact) 7f else 9f, cap = StrokeCap.Round)
            drawLine(Luxury, Offset(size.width * 0.12f, size.height * 0.12f), Offset(size.width * 0.5f, size.height * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(Luxury, Offset(size.width * 0.5f, size.height * 0.5f), Offset(size.width * 0.12f, size.height * 0.88f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(LuxurySoft, Offset(size.width * 0.88f, size.height * 0.12f), Offset(size.width * 0.5f, size.height * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(LuxurySoft, Offset(size.width * 0.5f, size.height * 0.5f), Offset(size.width * 0.88f, size.height * 0.88f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        Text("ANDER", color = Luxury, fontWeight = FontWeight.Black, fontSize = if (compact) 20.sp else 28.sp, letterSpacing = 3.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.width(22.dp).height(1.dp).background(Luxury))
            Text(if (compact) "POCKET MONEY" else "TECH LLC", color = Luxury, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Box(Modifier.width(22.dp).height(1.dp).background(Luxury))
        }
    }
}

@Composable
private fun LangPill(lang: String, open: Boolean, onToggle: () -> Unit, onPick: (String) -> Unit) {
    val short = when (lang) {
        "sw", "rw" -> "  SW"
        "fr" -> "  FR"
        else -> "  EN"
    }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.86f)).border(1.dp, Color.White, RoundedCornerShape(20.dp)).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◉", fontSize = 12.sp, color = Color(0xFF1C1C1C))
            Text(short, fontWeight = FontWeight.Bold, color = Color(0xFF1C1C1C))
            Text("  ▾", fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = onToggle) {
            DropdownMenuItem(text = { Text("English") }, onClick = { onPick("en") })
            DropdownMenuItem(text = { Text("Kiswahili") }, onClick = { onPick("sw") })
            DropdownMenuItem(text = { Text("Français") }, onClick = { onPick("fr") })
        }
    }
}

@Composable
private fun LangChoice(label: String, selected: Boolean, onClick: () -> Unit, flag: @Composable () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(128.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).border(if (selected) 2.dp else 1.dp, if (selected) Luxury else Color(0xFFEFE7DA), RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape).background(Luxury), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            flag()
            Text(label, color = Color(0xFF1C2430), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
        }
    }
}

@Composable
private fun GoldPill(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Brush.horizontalGradient(listOf(LuxurySoft, Luxury))).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.7f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun UkFlag(modifier: Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        drawRect(Color(0xFF012169))
        drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 10f)
        drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 10f)
        drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 5f)
        drawLine(Color(0xFFC8102E), Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 5f)
        drawLine(Color.White, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 16f)
        drawLine(Color.White, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 16f)
        drawLine(Color(0xFFC8102E), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 8f)
        drawLine(Color(0xFFC8102E), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 8f)
    }
}

@Composable
private fun FrFlag(modifier: Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        val band = size.width / 3f
        drawRect(Color(0xFF0055A4), size = Size(band, size.height))
        drawRect(Color.White, topLeft = Offset(band, 0f), size = Size(band, size.height))
        drawRect(Color(0xFFEF4135), topLeft = Offset(band * 2f, 0f), size = Size(band, size.height))
    }
}

@Composable
private fun SwFlag(modifier: Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        drawRect(Color(0xFF1EB53A))
        val blue = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width, size.height)
            lineTo(size.width, 0f)
            close()
        }
        drawPath(blue, Color(0xFF00A3DD))
        drawLine(
            Color(0xFFFCD116),
            Offset(0f, size.height),
            Offset(size.width, 0f),
            strokeWidth = size.minDimension * 0.28f,
            cap = StrokeCap.Square,
        )
        drawLine(
            Color.Black,
            Offset(0f, size.height),
            Offset(size.width, 0f),
            strokeWidth = size.minDimension * 0.16f,
            cap = StrokeCap.Square,
        )
    }
}

@Composable
private fun AuthForm(
    busy: Boolean,
    error: String,
    notice: String,
    mode: String,
    resetStep: Int,
    createStep: Int,
    name: String,
    phone: String,
    pin: String,
    code: String,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onPin: (String) -> Unit,
    onCode: (String) -> Unit,
    onResetStep: (Int) -> Unit,
    onCreateStep: (Int) -> Unit,
    onMode: (String) -> Unit,
    onBack: () -> Unit,
    model: PocketModel,
) {
    val copy = LocalCopy.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val create = mode == "create"
    val forgot = mode == "forgot"
    val signupCount by model.signupCount.collectAsState()
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (create && createStep == 1 && phone.isBlank()) {
            val line = SmsOtp.simPhone(context)
            if (line.isNotBlank()) onPhone(line)
        }
    }
    LaunchedEffect(mode, createStep, resetStep) {
        if (create && createStep == 1 && phone.isBlank()) {
            askSms.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS))
        }
    }
    LaunchedEffect(mode, createStep) {
        if (create && createStep == 1 && phone.isBlank()) {
            val line = SmsOtp.simPhone(context)
            if (line.isNotBlank()) onPhone(line)
        }
    }
    val phoneOk = phone.filter { it.isDigit() }.length >= 10
    val pinOk = pin.length == 4
    LaunchedEffect(notice, mode) {
        if (mode == "forgot" && notice.isNotBlank()) onResetStep(2)
    }
    val title = when {
        forgot -> copy.resetTitle
        create -> copy.createAccount
        else -> copy.signIn
    }
    val help = when {
        forgot && resetStep == 2 -> copy.resetCodeHelp
        forgot -> copy.resetHelp
        create && createStep == 2 -> copy.createCodeHelp
        create -> copy.createHelp
        else -> copy.signInHelp
    }
    val ready = !busy && when {
        forgot && resetStep == 1 -> phoneOk
        forgot -> phoneOk && pinOk && code.length == 6
        create && createStep == 1 -> phoneOk && pinOk && name.isNotBlank()
        create -> code.length == 6
        else -> phoneOk && pinOk
    }
    if (!create && !forgot) {
        SignInArtwork(
            phone = phone,
            pin = pin,
            busy = busy,
            error = error,
            onPhone = onPhone,
            onPin = onPin,
            onBack = onBack,
            onForgot = {
                onMode("forgot")
                onResetStep(1)
                onPin("")
                onCode("")
                model.clearMessages()
            },
            onCreate = {
                onMode("create")
                onCreateStep(1)
                onPin("")
                onCode("")
                model.clearMessages()
            },
            onLang = { model.setLang(it) },
            onSubmit = { model.login(phone, pin) },
        )
        return
    }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxHeight < 640.dp || keyboardOpen
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Blue, BlueDeep))).imePadding()) {
        SystemBars(Blue, Color.White, darkNavIcons = true)
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                color = Color.White,
                fontSize = 34.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (!compact) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.logo_xander),
                    contentDescription = "Xander Tech",
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            title,
            color = Color.White,
            fontSize = if (compact) 24.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        Text(
            help,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = if (compact) 12.dp else 18.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            if (forgot) StepDots(resetStep)
            Banner(error, false)
            Banner(notice, true)
            if (create && createStep == 2) {
                Text(copy.codeSent, color = Ok, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(phone, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (signupCount > 0) copy.familyCount(signupCount) else copy.openNumberHint,
                    color = Ink,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(OrangeSoft).padding(12.dp),
                )
                Text(copy.codeOnSim, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                DigitBoxes(copy.smsCode, code, 6, false, onCode)
            } else if (forgot && resetStep == 2) {
                Text(copy.codeSent, color = Ok, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(phone, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(copy.codeOnSim, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                DigitBoxes(copy.smsCode, code, 6, false, onCode)
                DigitBoxes(copy.newPin, pin, 4, true, onPin)
                Text(copy.pinHelp, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            } else {
                if (create) Field(copy.yourName, name, onName, "")
                Field(copy.phone, phone, { onPhone(it.filter { ch -> ch.isDigit() }.take(12)) }, "07xxxxxxxx", KeyboardType.Phone)
                if (!forgot) {
                    DigitBoxes(copy.pin, pin, 4, true, onPin)
                    Text(copy.pinHelp, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            AuthPrimaryButton(
                when {
                    busy -> copy.pleaseWait
                    forgot && resetStep == 1 -> copy.sendCode
                    forgot -> copy.savePin
                    create && createStep == 1 -> copy.createAccount
                    create -> copy.verifyCreate
                    else -> copy.signIn
                },
                ready,
            ) {
                when {
                    forgot && resetStep == 1 -> model.sendReset(phone)
                    forgot -> model.confirmReset(phone, code, pin)
                    create && createStep == 1 -> model.startRegister(name, phone, pin)
                    create -> model.confirmRegister(phone, code)
                    else -> model.login(phone, pin)
                }
            }
            if ((forgot && resetStep == 2) || (create && createStep == 2)) {
                Text(
                    copy.resend,
                    color = Blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp).clickable(enabled = !busy) {
                        onCode("")
                        if (forgot) {
                            onPin("")
                            model.sendReset(phone)
                        } else {
                            model.startRegister(name, phone, pin)
                        }
                    },
                )
            }
            if (!create && !forgot) {
                Text(
                    copy.forgotPin,
                    color = Blue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp).clickable {
                        onMode("forgot")
                        onResetStep(1)
                        onPin("")
                        onCode("")
                        model.clearMessages()
                    },
                )
            }
            Text(
                when {
                    forgot -> copy.backToSignIn
                    create -> copy.switchToSignIn
                    else -> copy.switchToCreate
                },
                color = Blue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp, bottom = 28.dp).clickable {
                    onMode(if (create || forgot) "signin" else "create")
                    onResetStep(1)
                    onCreateStep(1)
                    onPin("")
                    onCode("")
                    model.clearMessages()
                },
            )
        }
    }
    }
}

@Composable
private fun AuthPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) Color.White else Orange,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Pill)
            .background(if (enabled) Orange else OrangeSoft)
            .border(if (enabled) 0.dp else 1.5.dp, Orange, Pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun WelcomeButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Blue,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().shadow(10.dp, Pill).clip(Pill).background(Color.White).clickable(onClick = onClick).padding(vertical = 16.dp),
    )
}

@Composable
private fun OrangeButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().clip(Pill).background(if (enabled) Orange else Orange.copy(alpha = 0.45f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp),
    )
}

@Composable
private fun DiamondWash(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tint = Color.White.copy(alpha = 0.08f)
        val spots = listOf(
            Offset(size.width * 0.78f, size.height * 0.14f) to 170.dp.toPx(),
            Offset(size.width * 1.05f, size.height * 0.36f) to 250.dp.toPx(),
            Offset(size.width * -0.12f, size.height * 0.62f) to 220.dp.toPx(),
            Offset(size.width * 0.62f, size.height * 0.98f) to 200.dp.toPx(),
            Offset(size.width * 0.08f, size.height * 0.2f) to 110.dp.toPx(),
        )
        spots.forEach { (center, side) ->
            rotate(45f, center) {
                drawRect(tint, topLeft = Offset(center.x - side / 2f, center.y - side / 2f), size = Size(side, side))
            }
        }
    }
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

private fun dayPart(copy: AppCopy): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> copy.morning
        hour < 17 -> copy.afternoon
        else -> copy.evening
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(Modifier.padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { index ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index + 1 == step) 28.dp else 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (index + 1 <= step) Gold else Line),
            )
        }
    }
}

@Composable
private fun PinCaret() {
    val alpha by rememberInfiniteTransition(label = "pinCaret").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 650), RepeatMode.Reverse),
        label = "pinCaretAlpha",
    )
    Box(Modifier.width(2.dp).height(22.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFC9961B).copy(alpha = alpha)))
}

@Composable
private fun DigitBoxes(label: String, value: String, length: Int, mask: Boolean, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = if (focused) value.length.coerceAtMost(length - 1) else -1
    Text(label, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(length)) },
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(length) { index ->
                        val filled = index < value.length
                        val on = index == active
                        Box(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Color(0xFFFFF4D4) else if (filled) Color(0xFFFFF8E6) else Color.White)
                                .border(if (on) 2.5.dp else 1.5.dp, if (on || filled) Color(0xFFC9961B) else Color(0xFFE4C56A), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (filled) {
                                Text(
                                    if (mask) "•" else value[index].toString(),
                                    color = Navy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                )
                            } else if (on) {
                                PinCaret()
                            }
                        }
                    }
                }
                Box(Modifier.matchParentSize()) { inner() }
            }
        },
    )
}

@Composable
private fun LanguageSwitch(lang: String, onPick: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f))) {
        listOf("en" to "EN", "sw" to "SW", "fr" to "FR").forEach { (code, label) ->
            val selected = lang == code
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Orange else Color.Transparent)
                    .clickable { onPick(code) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun FamilyWelcomeScreen(welcome: FamilyWelcome, onContinue: () -> Unit) {
    val copy = LocalCopy.current
    val role = relationLabel(copy, welcome.relation)
    val who = welcome.registeredAs.trim()
    Column(Modifier.fillMaxSize().background(Paper)) {
        SystemBars(Blue, Paper, darkNavIcons = true)
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Blue, BlueDeep))).padding(horizontal = 22.dp, vertical = 22.dp)) {
            Text(copy.familyTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(copy.familyHint, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
            if (who.isNotBlank() || role.isNotBlank()) {
                Text(
                    listOf(copy.familySchool, role, who).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            welcome.students.forEach { student ->
                SurfaceCard {
                    StudentBlock(student)
                    Text(money(student.balance ?: 0), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.padding(top = 12.dp))
                    if (student.cardDisabled) {
                        Text(copy.cardDisabled, color = Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    } else if (!student.cardLinked) {
                        Text(copy.noCard, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        OrangeButton(copy.continueHome, true, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp).navigationBarsPadding(), onContinue)
    }
}

@Composable
private fun HomeScreen(
    parent: ParentAccount?,
    students: List<StudentWallet>,
    found: StudentWallet?,
    matches: List<StudentWallet>,
    searching: Boolean,
    busy: Boolean,
    error: String,
    notice: String,
    model: PocketModel,
) {
    var regno by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(students.isEmpty()) }
    val reset by model.resetSearch.collectAsState()
    LaunchedEffect(regno) { model.search(regno) }
    LaunchedEffect(reset) { if (reset > 0) { regno = ""; adding = false } }
    LaunchedEffect(found?.id) { found?.let { model.remember(it) } }
    val copy = LocalCopy.current
    val lang by model.lang.collectAsState()
    val agent = parent?.agent == true
    val greet = dayPart(copy).removeSuffix("!").trim()
    val who = parent?.name?.takeIf { it.isNotBlank() }
    Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
        SystemBars(Blue, Paper, darkNavIcons = true)
        Bar(
            if (who == null) greet else "$greet, $who",
            copy.homeHint,
            students.sumOf { it.balance ?: 0 },
            showBack = false,
            lang = lang,
            onLang = { model.setLang(it) },
        ) { }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Banner(error, false)
            Banner(notice, true)
            Text(if (agent) copy.agentWallets else copy.yourStudents, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
            students.forEach { student ->
                Spacer(Modifier.height(12.dp))
                SurfaceCard {
                    StudentBlock(student)
                    Text(money(student.balance ?: 0), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, modifier = Modifier.padding(top = 10.dp))
                    if (student.cardDisabled) Text(copy.cardDisabled, color = Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    else if (!student.cardLinked) Text(copy.noCard, color = Danger, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton(copy.sendMoney, !busy && student.cardLinked && !student.cardDisabled, Modifier.weight(1f)) { model.open(Destination.Pay(student.id)) }
                        NavyButton(copy.activity, Modifier.weight(1f)) { model.open(Destination.Activity(student.id)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!adding && students.isNotEmpty()) {
                Text(copy.addAnother, color = Blue, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { adding = true }.padding(vertical = 8.dp))
            }
            if (adding || students.isEmpty()) {
            SurfaceCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (students.isEmpty()) copy.addStudent else copy.addAnother, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (searching) CircularProgressIndicator(color = Navy, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                if (students.isEmpty() && !agent) {
                    Text(copy.addByReg, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Field(copy.regNo, regno, { regno = it.filter { ch -> ch.isDigit() }.take(12) }, "260270886", KeyboardType.Number)
                if (found == null && matches.size > 1) {
                    matches.forEach { student ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { model.choose(student); regno = student.regno }
                                .border(1.dp, Line, RoundedCornerShape(12.dp)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(student.name, fontWeight = FontWeight.Bold, color = Ink)
                                Text(student.regno + " · " + student.className, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                found?.let { student ->
                    Spacer(Modifier.height(12.dp))
                    StudentBlock(student)
                    Spacer(Modifier.height(8.dp))
                    if (student.cardDisabled) {
                        Text(copy.cardDisabled, color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    } else if (student.cardLinked) {
                        Text(copy.cardOnly, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        if (student.balance != null) {
                            Text(money(student.balance), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        TopUpPanel(student, parent?.phone.orEmpty(), busy, model)
                    } else {
                        Text(copy.noCard, color = Danger, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            }
            Text(copy.signOut, color = Danger, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp).clickable { model.logout() })
            Spacer(Modifier.height(280.dp))
        }
    }
}

@Composable
private fun TopUpPanel(student: StudentWallet, parentPhone: String, busy: Boolean, model: PocketModel) {
    var custom by remember(student.id) { mutableStateOf("") }
    var phone by remember(student.id) { mutableStateOf(parentPhone) }
    val amount = custom.toIntOrNull() ?: 0
    val copy = LocalCopy.current
    val ready = amount in 200..500000 && phone.isNotBlank()
    Column(Modifier.padding(top = 8.dp)) {
        Field(copy.amount, custom, { custom = it.filter { ch -> ch.isDigit() }.take(6) }, "0", KeyboardType.Number)
        Text(copy.amountHint, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFCC00)).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("MTN MoMo", fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Field(copy.momoNumber, phone, { phone = it }, "07xxxxxxxx", KeyboardType.Phone)
        val percent by model.chargePercent.collectAsState()
        val quote = chargeQuote(amount, percent)
        if (amount in 200..500000 && quote.fee > 0) {
            Text(
                copy.feeLine(amount, quote.transferFee, quote.withdrawFee, quote.debit),
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        GoldButton(
            if (busy) copy.pleaseWait else "${copy.sendMoney} ${if (ready) money(amount) else ""}".trim(),
            !busy && ready,
        ) { model.pay(student.id, amount, phone, "mtn") }
    }
}

@Composable
private fun PayScreen(student: StudentWallet, parentPhone: String, busy: Boolean, error: String, payMessage: String, model: PocketModel) {
    Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
        SystemBars(Blue, Paper, darkNavIcons = true)
        Bar(student.name, student.regno, student.balance ?: 0) { model.open(Destination.Home) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Banner(error, false)
            Banner(payMessage, payMessage.isNotBlank() && error.isBlank())
            SurfaceCard {
                StudentBlock(student)
                if (student.cardDisabled) {
                    Text(LocalCopy.current.cardDisabled, color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                } else {
                Text(LocalCopy.current.cardOnly, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(money(student.balance ?: 0), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, modifier = Modifier.padding(top = 4.dp))
                TopUpPanel(student, parentPhone, busy, model)
                }
            }
            Spacer(Modifier.height(280.dp))
        }
    }
}

@Composable
private fun ActivityScreen(student: StudentWallet, ledger: List<LedgerItem>, busy: Boolean, error: String, notice: String, model: PocketModel) {
    val copy = LocalCopy.current
    Column(Modifier.fillMaxSize().background(Paper)) {
        SystemBars(Blue, Paper, darkNavIcons = true)
        Bar(student.name, LocalCopy.current.activity, student.balance ?: 0) { model.open(Destination.Home) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Banner(error, false)
            Banner(notice, true)
            SurfaceCard {
                StudentBlock(student)
                Text(money(student.balance ?: 0), color = Navy, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                if (student.cardDisabled) Text(copy.cardDisabled, color = Danger, fontWeight = FontWeight.Bold)
                else if (!student.cardLinked) Text(copy.noCard, color = Danger)
            }
            Spacer(Modifier.height(12.dp))
            SurfaceCard {
                Text(copy.recentActivity, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                if (ledger.isEmpty()) Text(if (busy) copy.loading else copy.noActivity, color = Muted, modifier = Modifier.padding(top = 8.dp))
                ledger.forEach { item ->
                    val incoming = item.type == 0 || item.type == 3
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.label, fontWeight = FontWeight.Bold, color = Ink)
                            Text((if (incoming) "+" else "−") + money(item.amount), color = if (incoming) Ok else Danger, fontWeight = FontWeight.Bold)
                        }
                        if (item.lines.isNotEmpty()) {
                            item.lines.forEach { line ->
                                val unit = if (line.unit.isBlank()) "" else " ${line.unit}"
                                Text("${copy.took}: ${line.name}$unit × ${line.qty}", color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        } else if (item.detail.isNotBlank()) {
                            val text = if (item.type == 2) "${copy.withdrawReason}: ${item.detail}" else item.detail
                            Text(text, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (item.type == 2 && item.reason.isNotBlank() && item.reason != item.detail) {
                            Text("${copy.withdrawReason}: ${item.reason}", color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text(item.createdAt + if (item.merchant.isNotBlank()) " · ${item.merchant}" else "", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(item.status.replaceFirstChar { it.uppercase() } + (item.balance?.let { " · balance ${money(it)}" } ?: ""), color = Muted, fontSize = 12.sp)
                        if (item.status == "failed" && item.message.isNotBlank()) Text(item.message, color = Danger, fontSize = 12.sp)
                    }
                }
                NavyButton(copy.backToDashboard, Modifier.padding(top = 8.dp)) { model.open(Destination.Home) }
            }
            Spacer(Modifier.height(12.dp))
            SurfaceCard {
                Text(copy.cardOnly, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Remove from your account", color = Danger, modifier = Modifier.clickable { model.unlink(student.id) }.padding(top = 12.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bar(
    title: String,
    subtitle: String,
    balance: Int?,
    showBack: Boolean = true,
    lang: String? = null,
    onLang: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    if (!showBack) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Blue, BlueDeep))).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Image(
                        painterResource(R.drawable.logo_xander),
                        contentDescription = "Xander Tech",
                        modifier = Modifier.height(26.dp).width(108.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (lang != null && onLang != null) LanguageSwitch(lang, onLang)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            if (balance != null) {
                Text(money(balance), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Blue, BlueDeep))).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹", color = Color.White, fontSize = 32.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
        }
        if (balance != null) {
            Text(
                money(balance),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.background(Orange, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun StudentBlock(student: StudentWallet) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (student.photoUrl.isNotBlank()) {
            AsyncImage(student.photoUrl, student.name, Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE7EEF8)))
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Orange), contentAlignment = Alignment.Center) {
                Text(student.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(student.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val role = relationLabel(LocalCopy.current, student.relation)
            if (role.isNotBlank()) {
                Text(
                    role,
                    color = Orange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(OrangeSoft).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(student.regno + " · " + student.className, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Text(student.school, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(8.dp, Shape, ambientColor = Color(0x140B49B0), spotColor = Color(0x1A0B49B0)).clip(Shape).background(Card).padding(16.dp),
    ) { content() }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Text(label, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring).onFocusEvent { state ->
            if (state.isFocused) scope.launch { bring.bringIntoView() }
        },
        placeholder = { Text(placeholder, color = Muted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Orange,
            unfocusedBorderColor = Line,
            cursorColor = Blue,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
        ),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun GoldButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (enabled) Orange else Orange.copy(alpha = 0.45f)
    Text(
        label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).clip(Pill).background(bg).clickable(enabled = enabled, onClick = onClick).padding(vertical = 16.dp),
    )
}

@Composable
private fun NavyButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = Blue,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp).clip(Pill).border(1.5.dp, Blue, Pill).clickable(onClick = onClick).padding(vertical = 16.dp),
    )
}

@Composable
private fun Banner(text: String, ok: Boolean) {
    if (text.isBlank()) return
    Text(text, color = if (ok) Ok else Danger, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(if (ok) OkBg else DangerBg).padding(12.dp))
}
