package com.example.proyecto_android_studio_isw_28

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyecto_android_studio_isw_28.ui.theme.Proyecto_Android_studio_isw28Theme
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 🎨 Paleta de colores dinámica
data class AppColors(
    val Background: Color,
    val Surface: Color,
    val SurfaceAlt: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
    val AccentBlue: Color,
    val AccentOrange: Color,
    val AccentRed: Color
)

val LightColors = AppColors(
    Background = Color.White,
    Surface = Color(0xFFF2F2F7),
    SurfaceAlt = Color(0xFFE5E5EA),
    TextPrimary = Color(0xFF1C1C1E),
    TextSecondary = Color(0xFF8E8E93),
    TextTertiary = Color(0xFFAEAEB2),
    AccentBlue = Color(0xFF007AFF),
    AccentOrange = Color(0xFFFF9500),
    AccentRed = Color(0xFFFF3B30)
)

val DarkColors = AppColors(
    Background = Color(0xFF1C1C1E),
    Surface = Color(0xFF2C2C2E),
    SurfaceAlt = Color(0xFF3A3A3C),
    TextPrimary = Color.White,
    TextSecondary = Color(0xFF8E8E93),
    TextTertiary = Color(0xFF636366),
    AccentBlue = Color(0xFF0A84FF),
    AccentOrange = Color(0xFFFF9F0A),
    AccentRed = Color(0xFFFF453A)
)

enum class CalcMode { SCIENTIFIC, CURRENCY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val historyRepository = HistoryRepository(applicationContext)

        setContent {
            Proyecto_Android_studio_isw28Theme {
                // ✅ Opción 2: Llamamos a una función @Composable
                CalculatorMainScreen(historyRepository)
            }
        }
    }
}

// ✅ NUEVA FUNCIÓN @Composable: Aquí `remember` es válido
@Composable
fun CalculatorMainScreen(historyRepository: HistoryRepository) {
    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(systemDarkTheme) }

    // Si el usuario cambia el tema del sistema, actualizamos el estado
    LaunchedEffect(systemDarkTheme) {
        isDarkMode = systemDarkTheme
    }

    val colors = if (isDarkMode) DarkColors else LightColors

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.Background
    ) {
        CalculatorApp(
            historyRepository = historyRepository,
            colors = colors,
            isDarkMode = isDarkMode,
            onToggleTheme = { isDarkMode = !isDarkMode }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorApp(
    historyRepository: HistoryRepository,
    colors: AppColors,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 🔊 Generador de Sonido (ToneGenerator)
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun playSound(isEqualsOrConvert: Boolean = false) {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (isEqualsOrConvert) {
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { toneGen.release() }
    }

    var mode by remember { mutableStateOf(CalcMode.SCIENTIFIC) }
    var showModeMenu by remember { mutableStateOf(false) }

    // Científica
    var expression by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("0") }
    var errorText by remember { mutableStateOf("") }
    var isDegreeMode by remember { mutableStateOf(true) }
    var showScientificPanel by remember { mutableStateOf(true) }

    // Divisas
    var amountText by remember { mutableStateOf("100") }
    var fromCurrency by remember { mutableStateOf("USD") }
    var toCurrency by remember { mutableStateOf("MXN") }
    var currencyResult by remember { mutableStateOf("") }
    var currencyError by remember { mutableStateOf("") }
    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }

    // Historial
    val history by historyRepository.historyFlow.collectAsState(initial = emptyList())
    var showHistorySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun normalizeExpression(expr: String): String {
        if (!isDegreeMode) return expr
        val pattern = Regex("(sin|cos|tan)\\(([^()]+)\\)")
        return pattern.replace(expr) { m ->
            "${m.groupValues[1]}(rad(${m.groupValues[2]}))"
        }
    }

    fun evaluateScientific() {
        if (expression.isBlank()) { displayText = "0"; return }
        try {
            val result = ExpressionBuilder(normalizeExpression(expression)).build().evaluate()
            if (result.isNaN() || result.isInfinite()) {
                errorText = "Error matemático"
            } else {
                val formatted = formatNumber(result)
                displayText = formatted
                errorText = ""
                scope.launch { historyRepository.addEntry(expression, formatted) }
            }
        } catch (e: Exception) {
            errorText = "Expresión inválida"
        }
    }

    fun appendToExpression(token: String) {
        expression += token
        errorText = ""
    }

    fun clearAll() {
        expression = ""
        displayText = "0"
        errorText = ""
    }

    fun backspace() {
        if (expression.isNotEmpty()) expression = expression.dropLast(1)
    }

    fun evaluateCurrency() {
        val amount = amountText.toDoubleOrNull()
        if (amount == null) {
            currencyError = "Introduce un número válido"
            return
        }
        val converted = CurrencyRepository.convert(amount, fromCurrency, toCurrency)
        if (converted == null) {
            currencyError = "Moneda no soportada"
            return
        }
        val formatted = CurrencyRepository.formatResult(converted)
        currencyResult = formatted
        currencyError = ""
        scope.launch {
            historyRepository.addEntry(
                CurrencyRepository.buildHistoryExpression(amountText, fromCurrency, toCurrency),
                "$formatted $toCurrency"
            )
        }
    }

    fun handleNumericKey(key: String, isCurrency: Boolean) {
        if (isCurrency) {
            when (key) {
                "⌫" -> {
                    if (amountText.isNotEmpty()) {
                        amountText = amountText.dropLast(1)
                        if (amountText.isEmpty()) amountText = "0"
                    }
                }
                "." -> if (!amountText.contains(".")) amountText += "."
                else -> amountText = if (amountText == "0") key else amountText + key
            }
            evaluateCurrency()
        } else {
            when (key) {
                "⌫" -> backspace()
                "." -> {
                    val lastNum = expression.split(Regex("[+\\-×÷^()]")).lastOrNull() ?: ""
                    if (!lastNum.contains(".")) appendToExpression(".")
                }
                else -> appendToExpression(key)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Background)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---------- PANTALLA (DISPLAY) ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 160.dp)
                .padding(8.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Menú hamburguesa
                        Box {
                            IconButton(onClick = { showModeMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú de modos",
                                    tint = colors.TextSecondary
                                )
                            }
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false },
                                modifier = Modifier.background(colors.Background)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Científica", color = colors.TextPrimary) },
                                    onClick = {
                                        playSound()
                                        mode = CalcMode.SCIENTIFIC
                                        showModeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Divisas", color = colors.TextPrimary) },
                                    onClick = {
                                        playSound()
                                        mode = CalcMode.CURRENCY
                                        showModeMenu = false
                                    }
                                )
                            }
                        }

                        // ✅ BOTÓN PARA CAMBIAR EL TEMA MANUALMENTE
                        IconButton(onClick = {
                            playSound()
                            onToggleTheme()
                        }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Cambiar Tema",
                                tint = colors.TextSecondary
                            )
                        }
                    }

                    // Historial
                    IconButton(onClick = {
                        playSound()
                        showHistorySheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historial",
                            tint = colors.AccentBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (mode == CalcMode.SCIENTIFIC) {
                    Text(
                        text = expression.ifEmpty { "0" },
                        color = colors.TextSecondary,
                        fontSize = 18.sp,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (errorText.isNotEmpty()) {
                        Text(
                            text = errorText,
                            color = colors.AccentRed,
                            fontSize = 24.sp,
                            textAlign = TextAlign.End
                        )
                    } else {
                        Text(
                            text = displayText,
                            color = colors.TextPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = "$amountText $fromCurrency",
                        color = colors.TextSecondary,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (currencyError.isNotEmpty()) {
                        Text(
                            text = currencyError,
                            color = colors.AccentRed,
                            fontSize = 20.sp,
                            textAlign = TextAlign.End
                        )
                    } else {
                        Text(
                            text = if (currencyResult.isNotEmpty()) currencyResult else "0.00",
                            color = colors.TextPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "→ $toCurrency",
                        color = colors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---------- CONTROLES DEL MODO ----------
        if (mode == CalcMode.SCIENTIFIC) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        playSound()
                        isDegreeMode = !isDegreeMode
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.TextPrimary)
                ) { Text(if (isDegreeMode) "DEG" else "RAD", fontSize = 14.sp) }

                OutlinedButton(
                    onClick = {
                        playSound()
                        showScientificPanel = !showScientificPanel
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.TextPrimary)
                ) { Text(if (showScientificPanel) "2nd ⌃" else "2nd ⌄", fontSize = 14.sp) }
            }

            if (showScientificPanel) {
                val sciButtons = listOf(
                    listOf("sin", "cos", "tan", "√", "x²"),
                    listOf("ln", "log", "π", "e", "^")
                )
                for (row in sciButtons) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (btn in row) {
                            ScientificButton(
                                text = btn,
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    playSound()
                                    when (btn) {
                                        "sin", "cos", "tan" -> appendToExpression("$btn(")
                                        "√" -> appendToExpression("sqrt(")
                                        "x²" -> appendToExpression("^2")
                                        "ln" -> appendToExpression("log(")
                                        "log" -> appendToExpression("log10(")
                                        "π" -> appendToExpression("pi")
                                        "e" -> appendToExpression("e")
                                        "^" -> appendToExpression("^")
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            playSound()
                            showFromMenu = true
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.TextPrimary)
                    ) {
                        Text(fromCurrency, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("▼", fontSize = 10.sp)
                    }
                    DropdownMenu(
                        expanded = showFromMenu,
                        onDismissRequest = { showFromMenu = false },
                        modifier = Modifier.background(colors.Background)
                    ) {
                        CurrencyRepository.currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.code} - ${c.name}", color = colors.TextPrimary) },
                                onClick = {
                                    playSound()
                                    fromCurrency = c.code
                                    showFromMenu = false
                                    evaluateCurrency()
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            playSound()
                            showToMenu = true
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.TextPrimary)
                    ) {
                        Text(toCurrency, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("▼", fontSize = 10.sp)
                    }
                    DropdownMenu(
                        expanded = showToMenu,
                        onDismissRequest = { showToMenu = false },
                        modifier = Modifier.background(colors.Background)
                    ) {
                        CurrencyRepository.currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.code} - ${c.name}", color = colors.TextPrimary) },
                                onClick = {
                                    playSound()
                                    toCurrency = c.code
                                    showToMenu = false
                                    evaluateCurrency()
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------- TECLADO ----------
        val buttons = if (mode == CalcMode.SCIENTIFIC) {
            listOf(
                listOf("C", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "⌫", "=")
            )
        } else {
            listOf(
                listOf("7", "8", "9"),
                listOf("4", "5", "6"),
                listOf("1", "2", "3"),
                listOf("0", ".", "⌫")
            )
        }

        for (row in buttons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (button in row) {
                    val isOperator = button in listOf("÷", "×", "−", "+", "=")
                    CalculatorButton(
                        text = button,
                        isOperator = isOperator,
                        isEquals = button == "=",
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (button == "=" || mode == CalcMode.CURRENCY) {
                                playSound(isEqualsOrConvert = true)
                            } else {
                                playSound()
                            }

                            if (mode == CalcMode.SCIENTIFIC) {
                                when (button) {
                                    "C" -> clearAll()
                                    "⌫" -> backspace()
                                    "=" -> evaluateScientific()
                                    "." -> {
                                        val lastNum = expression.split(Regex("[+\\-×÷^()]")).lastOrNull() ?: ""
                                        if (!lastNum.contains(".")) appendToExpression(".")
                                    }
                                    "÷" -> appendToExpression("/")
                                    "×" -> appendToExpression("*")
                                    "−" -> appendToExpression("-")
                                    "+" -> appendToExpression("+")
                                    else -> appendToExpression(button)
                                }
                            } else {
                                handleNumericKey(button, isCurrency = true)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ---------- BOTTOM SHEET HISTORIAL ----------
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = colors.SurfaceAlt
        ) {
            HistorySheetContent(
                history = history,
                colors = colors,
                onEntryClick = { entry ->
                    playSound()
                    if (entry.expression.contains("→")) {
                        val parts = entry.expression.split(" ")
                        if (parts.size >= 4) {
                            mode = CalcMode.CURRENCY
                            amountText = parts[0]
                            fromCurrency = parts[1]
                            toCurrency = parts[3]
                            currencyResult = entry.result.substringBefore(" ")
                            currencyError = ""
                        }
                    } else {
                        mode = CalcMode.SCIENTIFIC
                        expression = entry.expression
                        displayText = entry.result
                        errorText = ""
                    }
                    showHistorySheet = false
                },
                onEntryDelete = { entry ->
                    playSound()
                    scope.launch { historyRepository.removeEntry(entry.timestamp) }
                },
                onClear = {
                    playSound()
                    scope.launch { historyRepository.clear() }
                }
            )
        }
    }
}

// ---------- COMPONENTES ----------

@Composable
fun HistorySheetContent(
    history: List<HistoryEntry>,
    colors: AppColors,
    onEntryClick: (HistoryEntry) -> Unit,
    onEntryDelete: (HistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historial",
                color = colors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Borrar todo",
                        tint = colors.AccentRed
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Borrar todo", color = colors.AccentRed)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aún no hay operaciones",
                    color = colors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = history,
                    key = { "${it.timestamp}_${it.expression}" }
                ) { entry ->
                    HistoryRow(
                        entry = entry,
                        colors = colors,
                        onClick = { onEntryClick(entry) },
                        onDelete = { onEntryDelete(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryRow(
    entry: HistoryEntry,
    colors: AppColors,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val clipboard = LocalClipboardManager.current

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .background(colors.Background)
                .padding(12.dp)
        ) {
            Text(
                text = entry.expression,
                color = colors.TextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "= ${entry.result}",
                color = colors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormat.format(Date(entry.timestamp)),
                    color = colors.TextTertiary,
                    fontSize = 11.sp
                )
                Row {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(entry.result)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copiar resultado",
                            tint = colors.AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar entrada",
                            tint = colors.AccentRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatNumber(value: Double): String {
    return when {
        value.isNaN() || value.isInfinite() -> "Error"
        value == value.toLong().toDouble() -> value.toLong().toString()
        kotlin.math.abs(value) > 1e12 || (kotlin.math.abs(value) < 1e-6 && value != 0.0) ->
            String.format(Locale.US, "%.6e", value)
        else -> {
            val s = String.format(Locale.US, "%.10f", value)
            s.trimEnd('0').trimEnd('.')
        }
    }
}

@Composable
fun ScientificButton(
    text: String,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(2.dp).height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.SurfaceAlt,
            contentColor = colors.TextPrimary
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CalculatorButton(
    text: String,
    isOperator: Boolean,
    isEquals: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isEquals -> colors.AccentBlue
        isOperator -> colors.AccentOrange
        else -> colors.Surface
    }

    val textColor = if (isOperator || isEquals) Color.White else colors.TextPrimary

    Button(
        onClick = onClick,
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}