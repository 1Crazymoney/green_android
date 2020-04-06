package com.greenaddress.greenbits.ui.notifications;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.greenaddress.gdk.GDKSession.getSession;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenaddress.gdk.GDKTwoFactorCall;
import com.greenaddress.greenapi.data.EventData;
import com.greenaddress.greenapi.data.TransactionData;
import com.greenaddress.greenapi.model.EventDataObservable;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.components.BottomOffsetDecoration;
import com.greenaddress.greenbits.ui.components.DividerItem;
import com.greenaddress.greenbits.ui.onboarding.SecurityActivity;
import com.greenaddress.greenbits.ui.preferences.GAPreferenceFragment;
import com.greenaddress.greenbits.ui.transactions.TransactionActivity;
import com.greenaddress.greenbits.wallets.HardwareCodeResolver;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class NotificationsFragment extends GAPreferenceFragment implements Observer {

    private ContextThemeWrapper mContextThemeWrapper;
    private PreferenceCategory mEmptyNotifications;


    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Context activityContext = getActivity();

        final PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(activityContext);
        setPreferenceScreen(preferenceScreen);

        final TypedValue themeTypedValue = new TypedValue();
        activityContext.getTheme().resolveAttribute(R.attr.preferenceTheme, themeTypedValue, true);
        mContextThemeWrapper = new ContextThemeWrapper(activityContext, themeTypedValue.resourceId);
        mEmptyNotifications = new PreferenceCategory(mContextThemeWrapper);
        mEmptyNotifications.setTitle(R.string.id_your_notifications_will_be);
    }

    private void setup(final EventDataObservable observable){
        final List<EventData> events = observable.getEventDataList();
        getPreferenceScreen().removeAll();
        for (final EventData e: events) {
            final Preference preference = new Preference(mContextThemeWrapper);
            preference.setTitle(e.getTitle());
            final String description = getDescription(e);
            preference.setSummary(description);
            if (e.getTitle() == R.string.id_system_message) {
                preference.setOnPreferenceClickListener(preference1 -> {
                    final Intent intent = new Intent(getActivity(), MessagesActivity.class);
                    intent.putExtra("message", description);
                    intent.putExtra("event", e);
                    startActivity(intent);
                    return false;
                });
            } else if (e.getTitle() == R.string.id_set_up_twofactor_authentication ||
                       e.getTitle() == R.string.id_you_only_have_one_twofactor) {
                preference.setOnPreferenceClickListener(preference1 -> {
                    final Intent intent = new Intent(getActivity(), SecurityActivity.class);
                    startActivity(intent);
                    return false;
                });
            }

            getPreferenceScreen().addPreference(preference);
        }

        if (getPreferenceScreen().getPreferenceCount() == 0) {
            getPreferenceScreen().addPreference(mEmptyNotifications);
        } else {
            getPreferenceScreen().removePreference(mEmptyNotifications);
        }
    }

    private String getDescription(final EventData event) {
        final int d = event.getDescription();

        if (d == R.string.id_new_incoming_transaction_in ||
            d == R.string.id_new_outgoing_transaction_from) {
            TransactionData tx = (TransactionData) event.getValue();
            final String accountName = getModel().getSubaccountsDataObservable().getSubaccountsDataWithPointer(
                tx.getSubaccount()).getNameWithDefault(getString(R.string.id_main_account));
            final long satoshi = tx.getSatoshi().get("btc");
            String amount;
            try {
                amount = getModel().getBtc(satoshi, true);
            } catch (final Exception e) {
                Log.e("", "Conversion error: " + e.getLocalizedMessage());
                amount = "";
            }
            final Object[] formatArgs = {accountName, amount};
            return getString(d, formatArgs);
        }
        if (d == R.string.id_new_transaction_involving) {
            TransactionData tx = (TransactionData) event.getValue();
            StringBuffer subaccounts = new StringBuffer();
            for (final Integer subaccount : tx.getSubaccounts()) {
                final String accountName =
                    getModel().getSubaccountsDataObservable().getSubaccountsDataWithPointer(
                        subaccount).getNameWithDefault(getString(R.string.id_main_account));
                if (subaccounts.length() != 0) { subaccounts.append(", "); }
                subaccounts.append(accountName);
            }
            final Object[] formatArgs = {subaccounts.toString()};
            return getString(d, formatArgs);
        }
        if (d == R.string.notification_format_string ||
            d == R.string.id_your_wallet_is_locked_for_a ||
            d == R.string.id_days_remaining_s ||
            d == R.string.id_s_blocks_left) {
            return getString(d, event.getValue());
        }

        return getString(d);
    }

    @Override
    public void update(final Observable observable, final Object o) {
        if (observable instanceof EventDataObservable)
            setup((EventDataObservable) observable);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isZombie())
            return;
        final EventDataObservable observable = getModel().getEventDataObservable();
        if (observable != null)
            observable.deleteObserver(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isZombie())
            return;
        final EventDataObservable observable = getModel().getEventDataObservable();
        if (observable != null)
            observable.addObserver(this);
        setup(observable);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView listView = getListView();
        listView.addItemDecoration(new DividerItem(getContext()));
        float offsetPx = getResources().getDimension(R.dimen.adapter_bar);
        final BottomOffsetDecoration bottomOffsetDecoration = new BottomOffsetDecoration((int) offsetPx);
        listView.addItemDecoration(bottomOffsetDecoration);
        setDivider(null);
        listView.setFocusable(false);
    }
}
