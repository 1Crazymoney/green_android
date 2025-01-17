package com.blockstream.green.settings

import android.content.SharedPreferences
import android.os.Parcelable
import com.blockstream.gdk.data.Network
import kotlinx.parcelize.Parcelize


@Parcelize
data class ApplicationSettings constructor(
    val testnet: Boolean = false,
    val proxyUrl: String? = null,
    val tor: Boolean = false,
    val electrumNode: Boolean = false,
    val spv: Boolean = false,
    val multiServerValidation: Boolean = false,

    val personalBitcoinElectrumServer: String? = null,
    val personalLiquidElectrumServer: String? = null,
    val personalTestnetElectrumServer: String? = null,

    val spvBitcoinElectrumServer: String? = null,
    val spvLiquidElectrumServer: String? = null,
    val spvTestnetElectrumServer: String? = null,
) : Parcelable {

    fun getPersonalElectrumServer(network: Network) = when {
            Network.isMainnet(network.id) -> {
                personalBitcoinElectrumServer
            }
            Network.isTestnet(network.id) -> {
                personalTestnetElectrumServer
            }
            else -> {
                personalLiquidElectrumServer
            }
        }

    fun getSpvElectrumServer(network: Network) = when {
        Network.isMainnet(network.id) -> {
            spvBitcoinElectrumServer
        }
        Network.isTestnet(network.id) -> {
            spvLiquidElectrumServer
        }
        else -> {
            spvTestnetElectrumServer
        }
    }

    companion object {
        private const val TESTNET = "testnet"
        private const val PROXY_URL = "proxyURL"
        private const val TOR = "tor"
        private const val ELECTRUM_NODE = "electrumNode"
        private const val SPV = "spv"
        private const val MULTI_SERVER_VALIDATION = "multiServerValidation"

        private const val PERSONAL_BITCOIN_ELECTRUM_SERVER = "personalBitcoinElectrumServer"
        private const val PERSONAL_LIQUID_ELECTRUM_SERVER = "personalLiquidElectrumServer"
        private const val PERSONAL_TESTNET_ELECTRUM_SERVER = "personalTestnetElectrumServer"

        private const val SPV_BITCOIN_ELECTRUM_SERVER = "spvBitcoinElectrumServer"
        private const val SPV_LIQUID_ELECTRUM_SERVER = "spvLiquidElectrumServer"
        private const val SPV_TESTNET_ELECTRUM_SERVER = "spvTestnetElectrumServer"

        fun fromSharedPreferences(prefs: SharedPreferences): ApplicationSettings {
            return ApplicationSettings(
                testnet = prefs.getBoolean(TESTNET, false),
                proxyUrl = prefs.getString(PROXY_URL, null),
                tor = prefs.getBoolean(TOR, false),
                electrumNode = prefs.getBoolean(ELECTRUM_NODE, false),
                spv = prefs.getBoolean(SPV, false),
                multiServerValidation = prefs.getBoolean(MULTI_SERVER_VALIDATION, false),

                personalBitcoinElectrumServer = prefs.getString(PERSONAL_BITCOIN_ELECTRUM_SERVER, null),
                personalLiquidElectrumServer = prefs.getString(PERSONAL_LIQUID_ELECTRUM_SERVER, null),
                personalTestnetElectrumServer = prefs.getString(PERSONAL_TESTNET_ELECTRUM_SERVER, null),

                spvBitcoinElectrumServer = prefs.getString(SPV_BITCOIN_ELECTRUM_SERVER, null),
                spvLiquidElectrumServer = prefs.getString(SPV_LIQUID_ELECTRUM_SERVER, null),
                spvTestnetElectrumServer = prefs.getString(SPV_TESTNET_ELECTRUM_SERVER, null),
            )
        }

        fun toSharedPreferences(appSettings: ApplicationSettings, prefs: SharedPreferences) {
            prefs.edit().also {
                it.putBoolean(TESTNET, appSettings.testnet)
                it.putString(PROXY_URL, appSettings.proxyUrl)
                it.putBoolean(TOR, appSettings.tor)
                it.putBoolean(ELECTRUM_NODE, appSettings.electrumNode)
                it.putBoolean(SPV, appSettings.spv)
                it.putBoolean(MULTI_SERVER_VALIDATION, appSettings.multiServerValidation)

                it.putString(PERSONAL_BITCOIN_ELECTRUM_SERVER, appSettings.personalBitcoinElectrumServer)
                it.putString(PERSONAL_LIQUID_ELECTRUM_SERVER, appSettings.personalLiquidElectrumServer)
                it.putString(PERSONAL_TESTNET_ELECTRUM_SERVER, appSettings.personalTestnetElectrumServer)

                it.putString(SPV_BITCOIN_ELECTRUM_SERVER, appSettings.spvBitcoinElectrumServer)
                it.putString(SPV_LIQUID_ELECTRUM_SERVER, appSettings.spvLiquidElectrumServer)
                it.putString(SPV_TESTNET_ELECTRUM_SERVER, appSettings.spvTestnetElectrumServer)
            }.apply()
        }
    }
}