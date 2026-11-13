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

/**
 * Main activity with bottom navigation between List and Dashboard views
 */
class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView
    private val headphoneReceiver = HeadphoneReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkBluetoothPermissions()

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_list -> {
                    supportFragmentManager.beginTransaction().apply {
                        replace(R.id.fragment_container, SessionListFragment())
                        addToBackStack(null)
                        commit()
                    }
                    true
                }
                R.id.nav_dashboard -> {
                    supportFragmentManager.beginTransaction().apply {
                        replace(R.id.fragment_container, DashboardFragment())
                        addToBackStack(null)
                        commit()
                    }
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                replace(R.id.fragment_container, SessionListFragment())
                commit()
            }
        }
    }

    override fun onStart() {
    super.onStart()

    val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)

    ContextCompat.registerReceiver(
        this,
        headphoneReceiver,
        filter,
        ContextCompat.RECEIVER_EXPORTED
    )
}

override fun onStop() {
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
}
