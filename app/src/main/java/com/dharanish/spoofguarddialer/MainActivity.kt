package com.dharanish.spoofguarddialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dharanish.spoofguarddialer.ui.SentinalApp
import com.dharanish.spoofguarddialer.ui.onboarding.SetDefaultDialerScreen
import com.dharanish.spoofguarddialer.ui.theme.SpoofGuardDialerTheme
import com.dharanish.spoofguarddialer.util.PhoneIntents

class MainActivity : ComponentActivity() {
    private var dialNumber by mutableStateOf<String?>(null)
    private var dialRequest by mutableLongStateOf(0)
    private var openRecents by mutableStateOf(false)
    private var permissionRevision by mutableIntStateOf(0)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionRevision++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) acceptIntent(intent)
        else {
            dialNumber = savedInstanceState.getString("dial_number")
            dialRequest = savedInstanceState.getLong("dial_request")
            openRecents = savedInstanceState.getBoolean("open_recents")
        }
        setContent {
            SpoofGuardDialerTheme {
                val roleManager = remember { getSystemService(RoleManager::class.java) }
                var isDefault by remember { mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true) }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
                            permissionRevision++
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(isDefault) { if (isDefault) requestNeededPermissions() }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (!isDefault) SetDefaultDialerScreen(onRoleGranted = { isDefault = true }, modifier = Modifier.padding(padding))
                    else SentinalApp(
                        modifier = Modifier.padding(padding), initialNumber = dialNumber, dialRequest = dialRequest,
                        openRecents = openRecents, permissionRevision = permissionRevision,
                        onIntentConsumed = { dialNumber = null; openRecents = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent) {
        PhoneIntents.number(intent)?.let { dialNumber = it; dialRequest++; openRecents = false }
        if (intent.getBooleanExtra("open_recents", false)) openRecents = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("dial_number", dialNumber)
        outState.putLong("dial_request", dialRequest)
        outState.putBoolean("open_recents", openRecents)
        super.onSaveInstanceState(outState)
    }

    private fun requestNeededPermissions() {
        // The role request comes first; optional contact/history denial must never disable dialing.
        val prefs = getSharedPreferences("dialer", MODE_PRIVATE)
        if (prefs.getBoolean("asked_permissions_v2", false)) return
        val needed = buildList {
            add(Manifest.permission.CALL_PHONE); add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CONTACTS); add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        prefs.edit().putBoolean("asked_permissions_v2", true).apply()
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
