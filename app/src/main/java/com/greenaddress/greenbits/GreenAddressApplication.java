package com.greenaddress.greenbits;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.multidex.MultiDexApplication;

import com.blockstream.libgreenaddress.GDK;
import com.blockstream.libwally.Wally;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.greenaddress.gdk.GDKSession;
import com.greenaddress.gdk.JSONConverterImpl;
import com.greenaddress.greenapi.ConnectionManager;
import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenapi.HWWallet;
import com.greenaddress.greenapi.data.HWDeviceData;
import com.greenaddress.greenapi.data.NetworkData;
import com.greenaddress.greenapi.model.Model;
import com.greenaddress.greenbits.spv.SPV;
import com.greenaddress.greenbits.ui.FailHardActivity;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;
import com.greenaddress.greenbits.ui.preferences.PrefKeys;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GreenAddressApplication extends MultiDexApplication {

    private static final String TAG = GreenAddressApplication.class.getSimpleName();
    private final ListeningExecutorService mExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(8));
    private Model mModel;
    private final ConnectionManager mConnectionManager = new ConnectionManager();
    private static AtomicBoolean isRunningTest;
    private final SPV mSPV = new SPV();
    public HWDeviceData mHWDevice;
    public HWWallet mHWWallet;

    private void failHard(final String title, final String message) {
        final Intent fail = new Intent(this, FailHardActivity.class);
        fail.putExtra("errorTitle", title);
        final String supportMessage = "Please contact info@greenaddress.it for support.";
        fail.putExtra("errorContent", String.format("%s. %s", message, supportMessage));
        fail.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(fail);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if(isRunningTest())
            return;

        if (!Wally.isEnabled()) {
            failHard("Unsupported platform", "A suitable libwallycore.so was not found");
            return;
        }

        if (!CryptoHelper.initialize()) {
            failHard("Initialization failed", "Cryptographic initialization failed");
            return;
        }

        // GDK initialization parameters
        final ObjectNode details = (new ObjectMapper()).createObjectNode();
        details.put("datadir", getFilesDir().getAbsolutePath());
        try {
            GDK.init(new JSONConverterImpl(), details);
        } catch (final Exception e) {
            failHard("GDK initialization failed", e.getMessage());
            return;
        }

        migratePreferences();
    }

    public static synchronized boolean isRunningTest() {
        if (null == isRunningTest) {
            boolean istest;
            try {
                Class.forName("com.blockstream.libgreenaddress.GSDK");
                istest = true;
            } catch (ClassNotFoundException e) {
                istest = false;
            }
            isRunningTest = new AtomicBoolean(istest);
        }
        return isRunningTest.get();
    }

    // migrate preferences from previous GreenBits app with single network

    private void migratePreferences() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean migrated = sharedPreferences.getBoolean(PrefKeys.PREF_MIGRATED_V2_v3,false);
        if (!migrated) {
            // SPV_SYNCRONIZATION is now off by default unless a user had set the trusted peers,
            // in that case it stay how it was

            final SharedPreferences preferences = getSharedPreferences(getCurrentNetwork(), MODE_PRIVATE);
            final boolean isEnabled = preferences.getBoolean(PrefKeys.SPV_ENABLED, false);
            final boolean haveTrustedPeers = !"".equals(preferences.getString(PrefKeys.TRUSTED_ADDRESS, "").trim());
            if (haveTrustedPeers && isEnabled) {
                preferences.edit().putBoolean(PrefKeys.SPV_ENABLED, true);
            }
            // mainnet PIN migration
            copyPreferences(getSharedPreferences("pin", MODE_PRIVATE), getSharedPreferences("mainnet_pin", MODE_PRIVATE));
            sharedPreferences.edit().putBoolean(PrefKeys.PREF_MIGRATED_V2_v3, true).apply();
        }
    }

    private static void copyPreferences(final SharedPreferences source, final SharedPreferences destination) {
        if (source.getAll().isEmpty())
            return;
        final SharedPreferences.Editor destinationEditor = destination.edit();
        for (final Map.Entry<String, ?> entry : source.getAll().entrySet())
            writePreference(entry.getKey(), entry.getValue(), destinationEditor);
        destinationEditor.apply();
    }

    private static SharedPreferences.Editor writePreference(final String key, final Object value, final SharedPreferences.Editor preferences) {
        if (value instanceof Boolean)
            return preferences.putBoolean(key, (Boolean) value);
        else if (value instanceof String)
            return preferences.putString(key, (String) value);
        else if (value instanceof Long)
            return preferences.putLong(key, (Long) value);
        else if (value instanceof Integer)
            return preferences.putInt(key, (Integer) value);
        else if (value instanceof Float)
            return preferences.putFloat(key, (Float) value);
        else if (value instanceof Set)
            return preferences.putStringSet(key, (Set<String>) value);
        else
            throw new RuntimeException("Unknown preference type");
    }

    // get Network function
    public static NetworkData getNetworkData(final String network) {
        final List<NetworkData> networks = GDKSession.getNetworks();
        for (final NetworkData n : networks) {
            if (n.getNetwork().equals(network)) {
                return n;
            }
        }
        return null;
    }

    public void setCurrentNetwork(final String networkId) {
        final boolean res = PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PrefKeys.NETWORK_ID_ACTIVE, networkId).commit();
        if (res == false) {
            failHard(getString(R.string.id_error), getString(R.string.id_operation_failure));
        }
    }

    public String getCurrentNetwork() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(PrefKeys.NETWORK_ID_ACTIVE, "mainnet");
    }
    public NetworkData getCurrentNetworkData() {
        return getNetworkData(getCurrentNetwork());
    }

    public Model getModel() {
        return mModel;
    }

    public void setModel(final Model model) {
        mModel = model;
    }

    public ConnectionManager getConnectionManager() {
        return mConnectionManager;
    }

    public boolean isWatchOnly() {
        return mConnectionManager.isWatchOnly();
    }

    public String getWatchOnlyUsername() {
        return mConnectionManager.getWatchOnlyUsername();
    }

    public boolean warnIfOffline(final Activity activity) {
        if(getConnectionManager().isOffline()) {
            UI.toast(activity, R.string.id_connection_failed, Toast.LENGTH_LONG);
            return true;
        }
        return false;
    }

    public ListeningExecutorService getExecutor() {
        return mExecutor;
    }

    public SPV getSpv() {
        return mSPV;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.i("LoggedActivity","onLowMemory app");
    }
}
