package com.greenaddress.greenapi.data;

import com.blockstream.gdk.data.Device;
import com.blockstream.gdk.data.DeviceSupportsAntiExfilProtocol;
import com.blockstream.gdk.data.DeviceSupportsLiquid;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@Deprecated
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class HWDeviceData extends JSONData {
    public enum HWDeviceDataLiquidSupport {
        None,
        Lite,
        Full;

        @JsonValue
        public int toValue() {
            return ordinal();
        }
    }

    public enum HWDeviceAntiExfilSupport {
        None,
        Optional,
        Mandatory;

        @JsonValue
        public int toValue() {
            return ordinal();
        }
    }

    private HWDeviceDetailData device;

    public HWDeviceData() {}

    public HWDeviceData(final String name,
                        final boolean supportsLowR,
                        final boolean supportsArbitraryScripts,
                        final boolean supportsHostUnblinding,
                        final HWDeviceDataLiquidSupport supportsLiquid,
                        final HWDeviceAntiExfilSupport aeProtocolSupportLevel) {
        device = new HWDeviceDetailData(name, supportsLowR, supportsArbitraryScripts, supportsHostUnblinding, supportsLiquid, aeProtocolSupportLevel);
    }

    public HWDeviceDetailData getDevice() {
        return device;
    }

    public void setDevice(final HWDeviceDetailData device) {
        this.device = device;
    }
}