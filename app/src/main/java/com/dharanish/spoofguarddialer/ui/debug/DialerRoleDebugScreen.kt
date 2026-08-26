package com.dharanish.spoofguarddialer.ui.debug

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DialerRoleDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DialerRoleDebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val roleManager = context.getSystemService(RoleManager::class.java)
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    LaunchedEffect(Unit) {
        viewModel.refreshStatus()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStatus()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStatus()
    }

    val canUseFullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager?.canUseFullScreenIntent() == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Default Dialer Role — Debug",
            style = MaterialTheme.typography.titleMedium
        )
        Text(text = "Role available: ${viewModel.roleAvailable}", modifier = Modifier.padding(top = 16.dp))
        Text(text = "Role held (we are default dialer): ${viewModel.roleHeld}")
        Text(text = "Permissions granted: ${viewModel.permissionsGranted}")
        Text(text = "Full-screen intent allowed: $canUseFullScreenIntent")

        Button(
            onClick = { viewModel.refreshStatus() },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Refresh Status")
        }

        if (!viewModel.permissionsGranted) {
            Button(
                onClick = { permissionLauncher.launch(viewModel.requiredPermissions()) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Request Permissions")
            }
        }

        if (viewModel.roleAvailable && !viewModel.roleHeld) {
            Button(
                onClick = {
                    val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    if (intent != null) {
                        roleLauncher.launch(intent)
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Set as Default Dialer")
            }
        }

        if (!canUseFullScreenIntent) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Grant Full-Screen Intent Permission")
            }
        }
    }
}