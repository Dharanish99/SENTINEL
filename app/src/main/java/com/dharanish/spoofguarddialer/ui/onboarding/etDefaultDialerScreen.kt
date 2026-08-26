package com.dharanish.spoofguarddialer.ui.onboarding

import android.app.role.RoleManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharanish.spoofguarddialer.ui.theme.*

@Composable
fun SetDefaultDialerScreen(
    onRoleGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val held = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        if (held) {
            onRoleGranted()
        } else {
            Toast.makeText(context, "SENTINAL wasn't set as default dialer", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(SentinalBg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(SentinalBtnBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, null, tint = SentinalEmerald, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Set SENTINAL as your default dialer",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp),
            color = SentinalTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Real-time scam and spoof-call protection only works when SENTINAL handles your calls directly. Set it as your default phone app to continue.",
            color = SentinalTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(36.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SentinalEmerald, RoundedCornerShape(999.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                        if (intent != null) {
                            roleLauncher.launch(intent)
                        } else {
                            Toast.makeText(context, "Default dialer role isn't available on this device", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Set as Default Dialer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}