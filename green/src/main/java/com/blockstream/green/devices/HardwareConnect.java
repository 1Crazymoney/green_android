package com.blockstream.green.devices;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.blockstream.DeviceBrand;
import com.blockstream.gdk.data.Device;
import com.blockstream.gdk.data.DeviceSupportsAntiExfilProtocol;
import com.blockstream.gdk.data.DeviceSupportsLiquid;
import com.blockstream.green.BuildConfig;
import com.blockstream.green.R;
import com.btchip.BTChipConstants;
import com.btchip.BTChipDongle;
import com.btchip.BTChipException;
import com.btchip.comm.BTChipTransport;
import com.btchip.comm.android.BTChipTransportAndroid;
import com.greenaddress.Bridge;
import com.greenaddress.greenapi.HWWallet;
import com.greenaddress.greenbits.wallets.BTChipHWWallet;
import com.greenaddress.greenbits.wallets.JadeHWWallet;
import com.greenaddress.greenbits.wallets.LedgerBLEAdapter;
import com.greenaddress.greenbits.wallets.TrezorHWWallet;
import com.greenaddress.jade.HttpRequestProvider;
import com.greenaddress.jade.JadeAPI;
import com.greenaddress.jade.entities.JadeError;
import com.greenaddress.jade.entities.JadeVersion;
import com.greenaddress.jade.entities.VersionInfo;
import com.satoshilabs.trezor.Trezor;

import java.util.Collections;
import java.util.List;

import hu.akarnokd.rxjava3.bridge.RxJavaBridge;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;


public class HardwareConnect {
    private static final String TAG = HardwareConnect.class.getSimpleName();

    private static final JadeVersion JADE_VERSION_SUPPORTS_HOST_UNBLINDING = new JadeVersion("0.1.27");

    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private HWWallet mHwWallet;
    private com.blockstream.green.devices.Device device;


    public void connectDevice(final HardwareConnectInteraction interaction, final HttpRequestProvider requestProvider, final com.blockstream.green.devices.Device device){
        this.device = device;
        if(device.isUsb()){
            switch (device.getDeviceBrand()){
                case Blockstream:
                    final JadeAPI jadeAPI = JadeAPI.createSerial(requestProvider, device.getUsbManager(), device.getUsbDevice(), 115200);
                    onJade(interaction, jadeAPI);
                    break;
                case Ledger:

                    BTChipTransport transport = null;
                    try {
                        transport = BTChipTransportAndroid.open(device.getUsbManager(), device.getUsbDevice());
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                    if (transport == null) {
                        interaction.showInstructions(R.string.id_please_reconnect_your_hardware);
                        return;
                    }

                    if (BTChipTransportAndroid.isLedgerWithScreen(device.getUsbDevice())) {
                        // User entered PIN on-device
                        onLedger(interaction, transport, true, null);
                    } else {
                        // Prompt for PIN to unlock device before setting it up
                        // showLedgerPinDialog(transport);
                        onLedger(interaction, transport, false, null);
                    }
                    break;
                case Trezor:
                    onTrezor(interaction, device.getUsbManager(), device.getUsbDevice());
                    break;
            }

        }else{

            switch (device.getDeviceBrand()){
                case Blockstream:
                    final JadeAPI jadeAPI = JadeAPI.createBle(requestProvider, device.getBleDevice());
                    onJade(interaction, jadeAPI);
                    break;
                case Ledger:
                    // Ledger (Nano X)

                // Ledger BLE adapter will call the 'onLedger' function when the BLE connection is established
                // LedgerBLEAdapter.connectLedgerBLE(this, btDevice, this::onLedger, this::onLedgerError);

                LedgerBLEAdapter.connectLedgerBLE(interaction.context(), device.getBleDevice().getBluetoothDevice(), (final BTChipTransport transport, final boolean hasScreen, final PublishSubject<Boolean> bleDisconnectEvent) -> {
                    onLedger(interaction, transport, hasScreen, bleDisconnectEvent);
                }, (final BTChipTransport transport) -> {
                    interaction.showInstructions(R.string.id_please_reconnect_your_hardware);
                    closeLedger(interaction, transport);
                });
                    break;
                case Trezor:
                    break;
            }
        }
    }

    void onJade(final HardwareConnectInteraction interaction, final JadeAPI jade) {
        // Connect to jade (using background thread)
        mDisposables.add(Observable.just(jade)
                .subscribeOn(Schedulers.io())
                .map(JadeAPI::connect)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        rslt -> {
                            if (rslt) {
                                // Connected - ok to proceed to fw check, pin, login etc.
                                final VersionInfo verInfo = jade.getVersionInfo();
                                onJadeConnected(interaction, verInfo, jade);
                            } else {
                                Log.e(TAG, "Failed to connect to Jade");
                                interaction.showInstructions(R.string.id_please_reconnect_your_hardware);
                                closeJade(interaction, jade);
                            }
                        },
                        throwable -> {
                            Log.e(TAG, "Exception connecting to Jade");
                            interaction.showInstructions(R.string.id_please_reconnect_your_hardware);
                            closeJade(interaction, jade);
                        }
                )
        );
    }

    private void reconnectSession(final HardwareConnectInteraction interaction) throws Exception {
        Log.d(TAG, "(re-)connecting gdk session)");
        interaction.getGreenSession().disconnect(false);
        connect(interaction);
    }

    private void connect(final HardwareConnectInteraction interaction) throws Exception {
        interaction.getGreenSession().connect(interaction.getGreenSession().getNetworks().getBitcoinGreen());
    }

    private void onJadeConnected(final HardwareConnectInteraction interaction, final VersionInfo verInfo, final JadeAPI jade) {
        final JadeVersion version = new JadeVersion(verInfo.getJadeVersion());
        final boolean supportsHostUnblinding = JADE_VERSION_SUPPORTS_HOST_UNBLINDING.compareTo(version) <= 0;

        mDisposables.add(Single.just(interaction.getGreenSession())
                .subscribeOn(Schedulers.io())

                // Connect GDKSession first (on a background thread), as we use httpRequest() as part of
                // Jade login (to access firmware server and to interact with the pinserver).
                // This also acts as a handy check that we have network connectivity before we start.
                .map(session -> {
                    reconnectSession(interaction);
                    return session;
                })
                .doOnError(throwable -> Log.e(TAG, "Exception connecting GDK - " + throwable))

                // Then create JadeHWWallet instance and authenticate (with pinserver) still on background thread
                .doOnSuccess(session -> Log.d(TAG, "Creating Jade HW Wallet)"))
                .map(session -> new Device("Jade", true, true, supportsHostUnblinding,
                        DeviceSupportsLiquid.Lite,
                        DeviceSupportsAntiExfilProtocol.Optional))
                .map(device -> {
                    final JadeHWWallet jadeWallet = new JadeHWWallet(jade, interaction.getConnectionNetwork(), device, Bridge.INSTANCE.getHardwareQATester());
                    return jadeWallet;
                })
                .flatMap(jadeWallet -> jadeWallet.authenticate(interaction, interaction, interaction.getGreenSession()).as(RxJavaBridge.toV3Single()))

                // If all succeeded, set as current hw wallet and login ... otherwise handle error/display error
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        jadeWallet -> {
                            mHwWallet = jadeWallet;
                            doLogin(interaction);
                        },
                        throwable -> {
                            throwable.printStackTrace();
                            Log.e(TAG, "Connecting to Jade HW wallet got error: " + throwable);
                            if (throwable instanceof JadeError) {
                                final JadeError jaderr = (JadeError)throwable;
                                if (jaderr.getCode()  == JadeError.UNSUPPORTED_FIRMWARE_VERSION) {
                                    interaction.showInstructions(R.string.id_outdated_hardware_wallet);
                                } else if (jaderr.getCode()  == JadeError.CBOR_RPC_NETWORK_MISMATCH) {
                                    interaction.showInstructions(R.string.id_the_network_selected_on_the);
                                } else {
                                    // Error from Jade hw - show the hw error message as a toast
                                    interaction.showError(jaderr.getMessage());
                                    interaction.showInstructions(R.string.id_please_reconnect_your_hardware);
                                }
                            } else if ("GDK_ERROR_CODE -1 GA_connect".equals(throwable.getMessage())) {
                                interaction.showInstructions(R.string.id_unable_to_contact_the_green);
                            } else {
                                interaction.showInstructions(R.string.id_please_reconnect_your_hardware);
                            }
                            closeJade(interaction, jade);
                        }
                )
        );
    }

    private void closeJade(final HardwareConnectInteraction interaction, final JadeAPI jade) {
        try {
            jade.disconnect();
        }catch (Exception e){
            device.getProductId();
        }
        interaction.onDeviceFailed();
    }

    void onTrezor(final HardwareConnectInteraction interaction, UsbManager usbManager, UsbDevice usb) {
        final Trezor t = Trezor.getDevice(usbManager, Collections.singletonList(usb));

        if (interaction.getConnectionNetwork().isLiquid()) {
            interaction.showInstructions(R.string.id_hardware_wallet_support_for);
            closeTrezor(interaction, t);
            return;
        }
        if (t == null)
            return;

        final List<Integer> version = t.getFirmwareVersion();
        final int vendorId = t.getVendorId();
        Log.d(TAG,"Trezor Version: " + version + " vendorid:" + vendorId + " productid:" + t.getProductId());

        // Min allowed: v1.6.0 & v2.1.0
        final boolean isFirmwareOutdated = version.get(0) < 1 ||
                (version.get(0) == 1 && version.get(1) < 6) ||
                (version.get(0) == 1 && version.get(1) == 6 && version.get(2) < 0) ||
                (version.get(0) == 2 && version.get(1) < 1);
        if (isFirmwareOutdated) {
            interaction.askForFirmwareUpgrade(DeviceBrand.Trezor, null, !Bridge.INSTANCE.isDevelopmentFlavor(), isPositive -> {
                if(isPositive) {
                    onTrezorConnected(interaction, t);
                }else{
                    closeTrezor(interaction, t);
                }
                return null;
            });
            return;
        }

        // All good
        onTrezorConnected(interaction, t);
    }

    private void onTrezorConnected(final HardwareConnectInteraction interaction, final Trezor t) {
        Log.d(TAG, "Creating Trezor HW wallet");
        final Device device = new Device("Trezor", false , false, false, DeviceSupportsLiquid.None, DeviceSupportsAntiExfilProtocol.None);
        mHwWallet = new TrezorHWWallet(t, interaction.getConnectionNetwork(), device);

        doLogin(interaction);
    }

    private void closeTrezor(final HardwareConnectInteraction interaction, final Trezor t) {
        interaction.onDeviceFailed();
    }

    void onLedger(final HardwareConnectInteraction interaction, final BTChipTransport transport, @Nullable final boolean hasScreen, PublishSubject<Boolean> bleDisconnectEvent) {
        transport.setDebug(BuildConfig.DEBUG);
        try {
            final BTChipDongle dongle = new BTChipDongle(transport, hasScreen);
            try {
                // This should only be supported by the Nano X
                final BTChipDongle.BTChipApplication application = dongle.getApplication();
                Log.d(TAG, "Ledger application:" + application);

                if (application.getName().contains("OLOS")) {
                    interaction.showInstructions(R.string.id_ledger_dashboard_detected);
                    closeLedger(interaction, transport);
                    return;
                }

                final boolean netMainnet = interaction.getConnectionNetwork().isMainnet();
                final boolean netLiquid = interaction.getConnectionNetwork().isLiquid();
                final boolean hwMainnet = !application.getName().contains("Test");
                final boolean hwLiquid = application.getName().contains("Liquid");
                Log.d(TAG, "Ledger application:" + application.getName() + ", network is mainnet:"+ netMainnet);

                if (netMainnet != hwMainnet || netLiquid != hwLiquid) {
                    // We using the wrong app, prompt the user to open the right app.
                    interaction.showInstructions(R.string.id_the_network_selected_on_the);
                    closeLedger(interaction, transport);
                    return;
                }
            } catch (final Exception e) {
                // Log but otherwise ignore
                Log.e(TAG, "Error trying to get Ledger application details: " + e);
            }

            // We don't ask for firmware version while in the dashboard, since the Ledger Nano X would return invalid status
            final BTChipDongle.BTChipFirmware fw = dongle.getFirmwareVersion();
            Log.d(TAG, "BTChip/Ledger firmware version " + fw);

            boolean isFirmwareOutdated = true;
            if (fw.getArchitecture() == BTChipDongle.BTCHIP_ARCH_LEDGER_1 && fw.getMajor() > 0) {
                // Min allowed: v1.0.4
                isFirmwareOutdated = (fw.getMajor() == 1 && fw.getMinor() < 0) ||
                        (fw.getMajor() == 1 && fw.getMinor() == 0 && fw.getPatch() < 4);
            } else if (fw.getArchitecture() == BTChipDongle.BTCHIP_ARCH_NANO_SX && fw.getMajor() > 0) {
                // Min allowed: v1.3.7
                isFirmwareOutdated = (fw.getMajor() == 1 && fw.getMinor() < 3) ||
                        (fw.getMajor() == 1 && fw.getMinor() == 3 && fw.getPatch() < 7);
            }

            if (isFirmwareOutdated) {
                interaction.askForFirmwareUpgrade(DeviceBrand.Ledger, null, !Bridge.INSTANCE.isDevelopmentFlavor(), isPositive -> {
                    if(isPositive) {
                        onLedgerConnected(interaction, dongle, bleDisconnectEvent);
                    }else{
                        closeLedger(interaction, transport);
                    }
                    return null;
                });

                return;
            }

            // All good
            onLedgerConnected(interaction, dongle, bleDisconnectEvent);
        } catch (final BTChipException e) {
            if (e.getSW() != BTChipConstants.SW_INS_NOT_SUPPORTED)
                e.printStackTrace();

            if (e.getSW() == 0x6faa) {
                interaction.showInstructions(R.string.id_please_disconnect_your_ledger);
            } else {
                interaction.showInstructions(R.string.id_ledger_dashboard_detected);
            }
            closeLedger(interaction, transport);
        }
    }

    private void onLedgerConnected(final HardwareConnectInteraction interaction, final BTChipDongle dongle, @Nullable final PublishSubject<Boolean> bleDisconnectEvent) {
        mDisposables.add(Single
                .just(interaction.getGreenSession())
                .subscribeOn(Schedulers.io())
                .map(session -> {
                    String pin = null;
                    if (this.device.isUsb() && !BTChipTransportAndroid.isLedgerWithScreen(this.device.getUsbDevice())) {
                        pin = interaction.requestPin(DeviceBrand.Ledger).blockingGet();
                    }

                    Log.d(TAG, "Creating Ledger HW wallet" + (pin != null ? " with PIN" : ""));
                    final Device device = new Device("Ledger", true,false, false, DeviceSupportsLiquid.Lite, DeviceSupportsAntiExfilProtocol.None);
                    mHwWallet = new BTChipHWWallet(dongle, pin , interaction.getConnectionNetwork(), device, bleDisconnectEvent);
                    return mHwWallet;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        hwWallet -> {
                            doLogin(interaction);
                        },
                        throwable -> {
                            Log.e(TAG, "Connecting to Ledger HW wallet got error: " + throwable);
                            interaction.showError(throwable.getMessage());
                        }
                )
        );
    }

    private void closeLedger(final HardwareConnectInteraction interaction, final BTChipTransport transport) {
        try {
            transport.close();
        } catch (final BTChipException ignored) {}
        interaction.onDeviceFailed();
    }

    private void doLogin(final HardwareConnectInteraction interaction) {
        device.setHwWallet(mHwWallet);
        interaction.onDeviceReady();
    }

    public void onDestroy() {
        mDisposables.clear();
    }
}
