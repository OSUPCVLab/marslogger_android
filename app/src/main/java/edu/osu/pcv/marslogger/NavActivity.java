package edu.osu.pcv.marslogger;

import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.BottomNavigationView;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import timber.log.Timber;

public class NavActivity extends AppCompatActivity implements
        ImuViewFragment.OnListFragmentInteractionListener,
        SettingsFragment.OnFragmentInteractionListener
{
    private static final int RESULT_SETTINGS = 1;
    public Menu settingsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nav);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_imuviewer, R.id.navigation_video, R.id.navigation_photo, R.id.navigation_about)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        settingsMenu = menu;
        return true;
    }

    @Override
    public void onListFragmentInteraction(ImuViewContent.SingleAxis item) {
        // The user selected an item from the ImuViewFragment
        // Do something here to display that item
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        item.setEnabled(false);
        switch (item.getItemId()) {
            case R.id.settings_option:
                Timber.d("Start settings");
                FragmentManager manager = getSupportFragmentManager();
                FragmentTransaction transaction = manager.beginTransaction();
                SettingsFragment fragment = new SettingsFragment();
                transaction.replace(R.id.nav_host_fragment, fragment);
                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            case R.id.help_option:
                Timber.d("Show help");
                item.setEnabled(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}
