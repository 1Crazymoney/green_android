package com.blockstream.green.utils

import com.blockstream.gdk.data.Balance
import com.blockstream.gdk.data.Settings
import com.blockstream.gdk.data.Transaction
import com.blockstream.gdk.params.Convert
import com.blockstream.green.gdk.GreenSession
import com.greenaddress.greenapi.model.Conversion
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

// Use it for GDK purposes
// Lowercase & replace μbtc -> ubtc
fun getUnit(session: GreenSession) = session.getSettings()?.unit?.lowercase()
    ?.replace("\u00B5btc", "ubtc")
    ?: "btc"

// Use it for UI purposes
fun getFiatCurrency(session: GreenSession): String{
    return session.getSettings()?.pricing?.currency?.getFiatUnit(session) ?: "n/a"
}

// Use it for UI purposes
fun String.getFiatUnit(session: GreenSession ): String {
    return if(session.isTestnet) "FIAT" else this
}

// Use it for UI purposes
fun getBitcoinOrLiquidUnit(session: GreenSession, unitGdk: String = session.getSettings()?.unit ?: "n/a"): String {
    var unit = unitGdk

    if (session.isTestnet) {
        unit = when (unit.lowercase()) {
            "btc" -> "TEST"
            "mbtc" -> "mTEST"
            "\u00B5btc" -> "\u00B5TEST"
            "bits" -> "bTEST"
            "sats" -> "sTEST"
            else -> unit
        }
    }

    return if (session.isLiquid) {
        "L-$unit"
    } else {
        unit
    }
}

fun getBitcoinOrLiquidSymbol(session: GreenSession): String = if(session.network.isLiquid) "L-BTC" else "BTC"

fun getDecimals(unit: String): Int {
    return when (unit.lowercase()) {
        "btc" -> 8
        "mbtc" -> 5
        "ubtc", "bits", "\u00B5btc" -> 2
        else -> 0
    }
}

fun gdkNumberFormat(decimals: Int, withDecimalSeparator: Boolean = false) = (DecimalFormat.getInstance(Locale.US) as DecimalFormat).apply {
    minimumFractionDigits = if(withDecimalSeparator) decimals else 0
    maximumFractionDigits = decimals
    isGroupingUsed = false
    decimalFormatSymbols = DecimalFormatSymbols(Locale.US).also {
        it.decimalSeparator = '.'
        it.groupingSeparator = ',' // Unused
    }
}

fun userNumberFormat(decimals: Int,
                    withDecimalSeparator: Boolean,
                    withGrouping: Boolean = false,
                    withMinimumDigits:Boolean = false,
                    locale: Locale = Locale.getDefault()) = (DecimalFormat.getInstance(locale) as DecimalFormat).apply {
    minimumFractionDigits = if(withDecimalSeparator || withMinimumDigits) decimals else 0
    maximumFractionDigits = decimals
    isDecimalSeparatorAlwaysShown = withDecimalSeparator
    isGroupingUsed = withGrouping
}

fun Long.feeRateWithUnit(): String {
    val feePerByte = this / 1000.0
    return userNumberFormat(decimals = 2, withDecimalSeparator = true, withGrouping = false).format(feePerByte) + " satoshi / vbyte"
}

fun Double.feeRateWithUnit(): String {
    return userNumberFormat(decimals = 2, withDecimalSeparator = true, withGrouping = false).format(this) + " satoshi / vbyte"
}

fun Balance?.fiat(session: GreenSession, withUnit: Boolean = true): String {
    if(this == null) return "n/a"
    return fiatOrNull(session, withUnit = withUnit) ?: "n/a"
}

fun Balance?.fiatOrNull(session: GreenSession, withUnit: Boolean = true): String? {
    if(this == null) return null
    return try {
        val value = fiat!!.toDouble()
        userNumberFormat(decimals = 2, withDecimalSeparator = true, withGrouping = false).format(value)
    } catch (e: Exception) {
        return null
    } + if (withUnit) " ${fiatCurrency.getFiatUnit(session)}" else ""
}

fun Balance?.btc(session: GreenSession, withUnit: Boolean = true, withGrouping: Boolean = false, withMinimumDigits: Boolean = true): String {
    if(this == null) return "n/a"
    return try {
        val unit = session.getSettings()?.unit ?: "BTC"
        val value = getValue(unit).toDouble()
        userNumberFormat(decimals = getDecimals(unit), withDecimalSeparator = false, withGrouping = withGrouping, withMinimumDigits = withMinimumDigits).format(value)
    } catch (e: Exception) {
        "n/a"
    } + if (withUnit) " ${getBitcoinOrLiquidUnit(session)}" else ""
}

fun Long.btc(settings: Settings, withUnit: Boolean = true): String {
    return try {
        userNumberFormat(decimals = getDecimals(settings.unit), withDecimalSeparator = false, withGrouping = false).format(this)
    } catch (e: Exception) {
        "n/a"
    } + if (withUnit) " ${settings.unit}" else ""
}

fun Balance?.asset(withUnit: Boolean = true, withGrouping: Boolean = false): String {
    if(this == null) return "n/a"

    return try {
        userNumberFormat(assetInfo?.precision ?: 0, withDecimalSeparator = false, withGrouping = withGrouping).format(assetValue?.toDouble() ?: satoshi)
    } catch (e: Exception) {
        "n/a"
    } + if (withUnit) " ${assetInfo?.ticker ?: ""}" else ""
}

fun Long.toBTCLook(session: GreenSession, withUnit: Boolean = true, withDirection: Transaction.Type? = null, withGrouping: Boolean = false, withMinimumDigits: Boolean = true): String {
    return session.convertAmount(Convert(satoshi = this))?.btc(session, withUnit = withUnit, withGrouping = withGrouping, withMinimumDigits = withMinimumDigits)?.let{ amount ->
        withDirection?.let { direction ->
            return if(direction == Transaction.Type.REDEPOSIT || direction == Transaction.Type.OUT){
                "-$amount"
            }else{
                "+$amount"
            }
        } ?: amount
    } ?: "n/a"
}

fun Long.toAssetLook(session: GreenSession, assetId: String, withUnit: Boolean = true, withGrouping: Boolean = false, withDirection: Transaction.Type? = null): String {
    return session.convertAmount(Convert(satoshi = this, session.getAsset(assetId)), isAsset = true)?.asset(withUnit = withUnit, withGrouping = withGrouping)?.let{ amount ->
        withDirection?.let { direction ->
            if(direction == Transaction.Type.REDEPOSIT || direction == Transaction.Type.OUT){
                "-$amount"
            }else{
                "+$amount"
            }
        } ?: amount
    } ?: "n/a"
}

fun Long.toFeeRate(): String {
    val feePerByte = this / 1000.0
    return userNumberFormat(decimals = 2, withDecimalSeparator = true, withGrouping = true, withMinimumDigits = true).format(feePerByte).let {
        "$it satoshi / vbyte"
    }
}

fun Date.formatOnlyDate(): String = DateFormat.getDateInstance(DateFormat.LONG).format(this)

fun Date.formatWithTime(): String = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(this)

fun Date.formatAuto(): String =
    if ((this.time + 24 * 60 * 60 * 1000) > (System.currentTimeMillis())) {
        formatWithTime()
    } else {
        formatOnlyDate()
    }