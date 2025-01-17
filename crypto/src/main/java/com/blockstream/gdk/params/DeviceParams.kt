package com.blockstream.gdk.params

import com.blockstream.gdk.GAJson
import com.blockstream.gdk.data.Device
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceParams constructor(
    @SerialName("device") val device: Device? = null,
) : GAJson<DeviceParams>() {
    override val encodeDefaultsValues = false

    override fun kSerializer(): KSerializer<DeviceParams> {
        return serializer()
    }
}
