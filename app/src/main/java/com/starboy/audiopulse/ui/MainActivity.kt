package com.starboy.audiopulse.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.starboy.audiopulse.R

/** Main activity hosting the session list and listening insights tabs. */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkBluetoothPermissions()

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_list -> SessionListFragment()
                R.id.nav_dashboard -> DashboardFragment()
                else -> return@setOnItemSelectedListener false
            }

            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        val selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB, R.id.nav_list) ?: R.id.nav_list
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = selectedTab
        } else {
            bottomNav.menu.findItem(selectedTab)?.isChecked = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, bottomNav.selectedItemId)
        super.onSaveInstanceState(outState)
    }

    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                BLUETOOTH_PERMISSION_REQUEST
            )
        }
    }

    private companion object {
        const val BLUETOOTH_PERMISSION_REQUEST = 100
        const val STATE_SELECTED_TAB = "selected_tab"
    }
}
