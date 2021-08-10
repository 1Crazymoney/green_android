package com.greenaddress.greenbits.ui.notifications;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.FragmentTransaction;

import com.greenaddress.greenbits.ui.LoggedActivity;

public class NotificationsActivity extends LoggedActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState == null){
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(android.R.id.content, new NotificationsFragment()).commit();
        }

        setTitleBackTransparent();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinishing())
            return;

    }

    @Override
    public void onPause() {
        super.onPause();
        if (isFinishing())
            return;
    }
}
