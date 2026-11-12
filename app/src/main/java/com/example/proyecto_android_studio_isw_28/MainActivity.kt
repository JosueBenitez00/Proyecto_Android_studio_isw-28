package com.example.proyecto_android_studio_isw_28

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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

// 🔀 Paleta centralizada (mismos colores que ya usabas)
object AppColors {
    val Background = Color(0xFF1C1C1E)
    val Surface = Color(0xFF2C2C2E)
    val SurfaceAlt = Color(0xFF3A3A3C)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF8E8E93)
    val TextTertiary = Color(0xFF636366)
    val AccentBlue = Color(0xFF0A84FF)
    val AccentOrange = Color(0xFFFF9F0A)
    val AccentRed = Color(0xFFFF453A)
}

// 🔀 Modo de la app
enum class CalcMode { SCIENTIFIC, CURRENCY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val historyRepository = HistoryRepository(applicationContext)

        setContent {
            Proyecto_Android_studio_isw28Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColors.Background
                ) {
                    CalculatorApp(historyRepository)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorApp(historyRepository: HistoryRepository) {
    val scope = rememberCoroutineScope()

    // 🔀 Estado de modo
    var mode by remember { mutableStateOf(CalcMode.SCIENTIFIC) }

    // ---- Estado calculadora científica ----
    var expression by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("0") }
    var errorText by remember { mutableStateOf("") }
    var isDegreeMode by remember { mutableStateOf(true) }
    var showScientificPanel by remember { mutableStateOf(true) }

    // ---- Estado conversor de divisas ----
    var amountText by remember { mutableStateOf("100") }
    var fromCurrency by remember { mutableStateOf("USD") }
    var toCurrency by remember { mutableStateOf("MXN") }
    var currencyResult by remember { mutableStateOf("") }
    var currencyError by remember { mutableStateOf("") }
    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }

    // ---- Historial ----
    val history by historyRepository.historyFlow.collectAsState(initial = emptyList())
    var showHistorySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ---- Utilidades científicas ----
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

    // ---- Conversión de divisas ----
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

    // ---- Teclado compartido ----
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
            .background(AppColors.Background)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---------- SELECTOR DE MODO ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                text = "Científica",
                selected = mode == CalcMode.SCIENTIFIC,
                modifier = Modifier.weight(1f),
                onClick = { mode = CalcMode.SCIENTIFIC }
            )
            ModeChip(
                text = "Divisas",
                selected = mode == CalcMode.CURRENCY,
                modifier = Modifier.weight(1f),
                onClick = { mode = CalcMode.CURRENCY }
            )
        }

        // ---------- PANTALLA ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Barra superior: historial + indicador de modo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historial",
                            tint = AppColors.AccentBlue
                        )
                    }
                    Text(
                        text = when {
                            mode == CalcMode.CURRENCY -> "DIVISAS"
                            isDegreeMode -> "DEG"
                            else -> "RAD"
                        },
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 🔀 Contenido variable según modo
                if (mode == CalcMode.SCIENTIFIC) {
                    Text(
                        text = expression.ifEmpty { "0" },
                        color = AppColors.TextSecondary,
                        fontSize = 20.sp,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (errorText.isNotEmpty()) {
                        Text(
                            text = errorText,
                            color = AppColors.AccentRed,
                            fontSize = 28.sp,
                            textAlign = TextAlign.End
                        )
                    } else {
                        Text(
                            text = displayText,
                            color = AppColors.TextPrimary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                } else {
                    // Divisas: cantidad origen + resultado grande
                    Text(
                        text = "$amountText $fromCurrency",
                        color = AppColors.TextSecondary,
                        fontSize = 20.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (currencyError.isNotEmpty()) {
                        Text(
                            text = currencyError,
                            color = AppColors.AccentRed,
                            fontSize = 24.sp,
                            textAlign = TextAlign.End
                        )
                    } else {
                        Text(
                            text = if (currencyResult.isNotEmpty()) currencyResult else "0.00",
                            color = AppColors.TextPrimary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "→ $toCurrency",
                        color = AppColors.TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // ---------- CONTROLES ESPECÍFICOS DEL MODO ----------
        if (mode == CalcMode.SCIENTIFIC) {
            // Toggles DEG/RAD y 2nd
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { isDegreeMode = !isDegreeMode },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (isDegreeMode) "DEG" else "RAD", color = AppColors.TextPrimary) }

                OutlinedButton(
                    onClick = { showScientificPanel = !showScientificPanel },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (showScientificPanel) "2nd ⌃" else "2nd ⌄", color = AppColors.TextPrimary) }
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
                                modifier = Modifier.weight(1f),
                                onClick = {
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
            // Divisas: selectores de moneda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Moneda origen
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showFromMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(fromCurrency, color = AppColors.TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("▼", color = AppColors.TextPrimary, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = showFromMenu,
                        onDismissRequest = { showFromMenu = false },
                        containerColor = AppColors.Surface
                    ) {
                        CurrencyRepository.currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.code} - ${c.name}", color = AppColors.TextPrimary) },
                                onClick = {
                                    fromCurrency = c.code
                                    showFromMenu = false
                                    currencyResult = ""
                                    currencyError = ""
                                }
                            )
                        }
                    }
                }

                // Moneda destino
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showToMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(toCurrency, color = AppColors.TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("▼", color = AppColors.TextPrimary, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = showToMenu,
                        onDismissRequest = { showToMenu = false },
                        containerColor = AppColors.Surface
                    ) {
                        CurrencyRepository.currencies.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.code} - ${c.name}", color = AppColors.TextPrimary) },
                                onClick = {
                                    toCurrency = c.code
                                    showToMenu = false
                                    currencyResult = ""
                                    currencyError = ""
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
                        modifier = Modifier.weight(1f),
                        onClick = {
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

        // ---------- BOTÓN PRINCIPAL ----------
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (mode == CalcMode.SCIENTIFIC) evaluateScientific()
                else evaluateCurrency()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (mode == CalcMode.SCIENTIFIC) AppColors.AccentBlue
                else AppColors.AccentOrange
            )
        ) {
            Text(
                text = if (mode == CalcMode.SCIENTIFIC) "Calcular" else "Convertir",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    // ---------- BOTTOM SHEET HISTORIAL ----------
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = AppColors.Surface
        ) {
            HistorySheetContent(
                history = history,
                onEntryClick = { entry ->
                    // Reutilizar la entrada según su tipo (heurística simple)
                    if (entry.expression.contains("→")) {
                        // Es de divisas: "100 USD → MXN"
                        val parts = entry.expression.split(" ")
                        if (parts.size >= 3) {
                            mode = CalcMode.CURRENCY
                            amountText = parts[0]
                            fromCurrency = parts[1]
                            toCurrency = parts[3].ifEmpty { toCurrency }
                            currencyResult = entry.result.substringBefore(" ").replace(",", "")
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
                    scope.launch { historyRepository.removeEntry(entry.timestamp) }
                },
                onClear = { scope.launch { historyRepository.clear() } }
            )
        }
    }
}

// ---------- COMPONENTES ----------

@Composable
fun ModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) AppColors.AccentBlue else AppColors.Surface
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = AppColors.TextPrimary
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun HistorySheetContent(
    history: List<HistoryEntry>,
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
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Borrar todo",
                        tint = AppColors.AccentRed
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Borrar todo", color = AppColors.AccentRed)
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
                    color = AppColors.TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(history, key = { it.timestamp }) { entry ->
                    HistoryRow(
                        entry = entry,
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
                .background(AppColors.SurfaceAlt)
                .padding(12.dp)
        ) {
            Text(
                text = entry.expression,
                color = AppColors.TextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "= ${entry.result}",
                color = AppColors.TextPrimary,
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
                    color = AppColors.TextTertiary,
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
                            tint = AppColors.AccentBlue,
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
                            tint = AppColors.AccentRed,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(2.dp).height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.SurfaceAlt,
            contentColor = AppColors.AccentBlue
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CalculatorButton(
    text: String,
    isOperator: Boolean,
    isEquals: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isEquals -> AppColors.AccentBlue
        isOperator -> AppColors.AccentOrange
        else -> AppColors.Surface
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}