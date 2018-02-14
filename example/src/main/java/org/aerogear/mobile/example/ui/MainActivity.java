package org.aerogear.mobile.example.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.aerogear.mobile.auth.AuthService;
import org.aerogear.mobile.example.R;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends BaseActivity
    implements NavigationView.OnNavigationItemSelectedListener {

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.drawer_layout)
    DrawerLayout drawer;

    @BindView(R.id.nav_view)
    NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        navigateTo(new HomeFragment());
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AuthFragment.LOGIN_RESULT_CODE) {
            //this works but isn't great.
            //At this point the AuthService instance should have been intialised already in the AuthFragment.
            //the mobileCore cached the instance automatically for us.
            //need to think about how to make it more obvious.
            AuthService authService = (AuthService) mobileCore.getInstance(AuthService.class);
            authService.handleAuthResult(data);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_http:
                navigateTo(new HttpFragment());
                break;
            case R.id.nav_auth:
                navigateTo(new AuthFragment());
                break;
            default:
                navigateTo(new HomeFragment());
                break;
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void navigateTo(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.content, fragment)
            .commit();
    }

}