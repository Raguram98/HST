package com.starboy.audiopulse.ui

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.starboy.audiopulse.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.content.IntentFilter
import com.starboy.audiopulse.service.HeadphoneReceiver
import com.starboy.audiopulse.service.DiagnosticLogger

/**
 * Main activity with bottom navigation between List and Dashboard views.
 * Tab fragments are kept in the FragmentManager so switching tabs does not
 * recreate their views or reset their scroll positions.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private val headphoneReceiver = HeadphoneReceiver("dynamic_receiver")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep diagnostic history across launches; the logger retains the latest 500 entries.
        DiagnosticLogger.log(
            context = this,
            source = "main_activity",
            event = "APP_LIFECYCLE",
            deviceName = "N/A",
            deviceType = "APP",
            details = "callback=onCreate"
        )
        setContentView(R.layout.activity_main)
        checkBluetoothPermissions()

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_list, R.id.nav_dashboard -> {
                    showTab(item.itemId)
                    true
                }
                else -> false
            }
        }
        // Tapping the already-selected tab should leave its current position alone.
        bottomNav.setOnItemReselectedListener { }

        val selectedTab = savedInstanceState?.getInt(SELECTED_TAB_KEY) ?: R.id.nav_list
        bottomNav.selectedItemId = selectedTab
        showTab(selectedTab)
    }

    private fun showTab(itemId: Int) {
        val fragmentManager = supportFragmentManager
        val targetTag = when (itemId) {
            R.id.nav_dashboard -> DASHBOARD_TAG
            else -> SESSION_LIST_TAG
        }

        val targetFragment = fragmentManager.findFragmentByTag(targetTag)
            ?: if (itemId == R.id.nav_dashboard) {
                DashboardFragment()
            } else {
                SessionListFragment()
            }

        val transaction = fragmentManager.beginTransaction()
        val sessionList = fragmentManager.findFragmentByTag(SESSION_LIST_TAG)
        val dashboard = fragmentManager.findFragmentByTag(DASHBOARD_TAG)

        if (sessionList != null && sessionList != targetFragment) {
            transaction.hide(sessionList)
        }
        if (dashboard != null && dashboard != targetFragment) {
            transaction.hide(dashboard)
        }

        if (targetFragment.isAdded) {
            transaction.show(targetFragment)
        } else {
            transaction.add(R.id.fragment_container, targetFragment, targetTag)
        }

        transaction.commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SELECTED_TAB_KEY, bottomNav.selectedItemId)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        DiagnosticLogger.log(
            context = this,
            source = "main_activity",
            event = "APP_LIFECYCLE",
            deviceName = "N/A",
            deviceType = "APP",
            details = "callback=onStart"
        )

        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        ContextCompat.registerReceiver(
            this,
            headphoneReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStop() {
        DiagnosticLogger.log(
            context = this,
            source = "main_activity",
            event = "APP_LIFECYCLE",
            deviceName = "N/A",
            deviceType = "APP",
            details = "callback=onStop; unregistering dynamic wired receiver"
        )
        try {
            unregisterReceiver(headphoneReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        super.onStop()
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    100 // Permission request code
                )
            }
        }
    }

    companion object {
        private const val SELECTED_TAB_KEY = "selected_bottom_tab"
        private const val SESSION_LIST_TAG = "session_list_tab"
        private const val DASHBOARD_TAG = "dashboard_tab"
    }
}
