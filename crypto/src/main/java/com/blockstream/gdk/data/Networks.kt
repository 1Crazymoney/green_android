package com.blockstream.gdk.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*


@Serializable
data class Networks(
    @SerialName("networks") val networks: Map<String, Network>,
) {

    val bitcoinGreen by lazy { getNetworkById(Network.GreenMainnet) }
    val liquidGreen by lazy { getNetworkById(Network.GreenLiquid) }
    val testnetGreen by lazy { getNetworkById(Network.GreenTestnet) }
    val testnetLiquidGreen by lazy { getNetworkById(Network.GreenTestnetLiquid) }

    val bitcoinElectrum by lazy { getNetworkById(Network.ElectrumMainnet) }
    val liquidElectrum by lazy { getNetworkById(Network.ElectrumLiquid) }
    val testnetElectrum by lazy { getNetworkById(Network.ElectrumTestnet) }
    val testnetLiquidElectrum by lazy { getNetworkById(Network.ElectrumTestnetLiquid) }

    val hardwareSupportedNetworks by lazy { listOf(bitcoinGreen, liquidGreen, testnetGreen) }

    fun getNetworkById(id: String): Network {
        return networks[id] ?: throw Exception("Network '$id' is not available in the current build of GDK")
    }

    companion object {
        /**
        Transform the gdk json to a more appropriate format
         */
        fun fromJsonElement(json: Json, element: JsonElement): Networks {

            val networks: MutableMap<String, JsonObject> = mutableMapOf()

            element.jsonObject["all_networks"]?.jsonArray?.let{
                for (key in it){
                    @Suppress("NAME_SHADOWING")
                    val key = key.jsonPrimitive.content
                    element.jsonObject[key]?.jsonObject?.let{ obj ->
                        networks[key] = buildJsonObject {
                            put("id", key)
                            for(k in obj){
                                put(k.key, k.value)
                            }
                        }
                    }
                }
            }

            return json.decodeFromJsonElement(buildJsonObject {
                putJsonObject("networks") {
                    for(k in networks){
                        put(k.key, k.value)
                    }
                }
            })
        }

    }
}