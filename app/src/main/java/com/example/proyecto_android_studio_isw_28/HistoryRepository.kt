package com.example.proyecto_android_studio_isw_28

import java.util.Locale

data class Currency(
    val code: String,
    val name: String,
    val rateToUsd: Double  // cuántas unidades de esta divisa = 1 USD
)

object CurrencyRepository {

    // Tasas relativas al USD (base). Actualízalas manualmente o vía API.
    val currencies: List<Currency> = listOf(
        Currency("USD", "Dólar estadounidense", 1.0),
        Currency("EUR", "Euro", 0.92),
        Currency("MXN", "Peso mexicano", 17.15),
        Currency("GBP", "Libra esterlina", 0.79),
        Currency("JPY", "Yen japonés", 149.50),
        Currency("CAD", "Dólar canadiense", 1.36),
        Currency("BRL", "Real brasileño", 4.98),
        Currency("ARS", "Peso argentino", 350.0),
        Currency("COP", "Peso colombiano", 3950.0),
        Currency("CLP", "Peso chileno", 890.0)
    )

    fun byCode(code: String): Currency? = currencies.firstOrNull { it.code == code }

    /**
     * Convierte [amount] de [fromCode] a [toCode].
     * Devuelve null si alguna moneda no existe.
     */
    fun convert(amount: Double, fromCode: String, toCode: String): Double? {
        val from = byCode(fromCode) ?: return null
        val to = byCode(toCode) ?: return null
        // amount / rateFrom => valor en USD; * rateTo => valor en destino
        return amount / from.rateToUsd * to.rateToUsd
    }

    fun formatResult(value: Double): String =
        String.format(Locale.US, "%,.2f", value)

    /** Formatea la operación completa para el historial. */
    fun buildHistoryExpression(amount: String, from: String, to: String): String =
        "$amount $from → $to"
}