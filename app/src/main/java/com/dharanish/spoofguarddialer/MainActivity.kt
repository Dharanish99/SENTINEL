package com.dharanish.spoofguarddialer

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.ui.SentinalApp
import com.dharanish.spoofguarddialer.ui.call.CallScreen
import com.dharanish.spoofguarddialer.ui.call.CallViewModel
import com.dharanish.spoofguarddialer.ui.onboarding.SetDefaultDialerScreen
import com.dharanish.spoofguarddialer.ui.theme.SpoofGuardDialerTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results ignored here — screens re-query their data whenever they load/resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        requestNeededPermissions()
        setContent {
            SpoofGuardDialerTheme {
                val roleManager = remember { getSystemService(RoleManager::class.java) }
                var isDefaultDialer by remember {
                    mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false)
                }

                // Re-check on every resume — covers returning from the system role prompt.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val callViewModel: CallViewModel = viewModel()
                val hasCall by callViewModel.hasCall.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        !isDefaultDialer -> SetDefaultDialerScreen(
                            onRoleGranted = { isDefaultDialer = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                        hasCall -> CallScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = callViewModel
                        )
                        else -> SentinalApp(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}