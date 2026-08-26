package com.dharanish.spoofguarddialer.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dharanish.spoofguarddialer.ui.components.BottomNavBar
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.theme.*
import com.dharanish.spoofguarddialer.util.SecuritySettings

@Composable
fun SettingsScreen(
    currentTab: SentinalTab,
    onTabSelect: (SentinalTab) -> Unit,
    onOpenDialerRoleDebug: () -> Unit = {},
    onOpenBridgeTestDebug: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var alwaysProtect by remember { mutableStateOf(SecuritySettings.isAlwaysProtectEnabled(context)) }
    var autoSecureUnknown by remember { mutableStateOf(SecuritySettings.isAutoSecureUnknownCallersEnabled(context)) }
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }

    fun checkDefaultDialer(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
    fun checkRoleAvailable(): Boolean = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) ?: false

    var isDefaultDialer by remember { mutableStateOf(checkDefaultDialer()) }
    var roleAvailable by remember { mutableStateOf(checkRoleAvailable()) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val stillNotDefault = !checkDefaultDialer()
        isDefaultDialer = !stillNotDefault
        if (stillNotDefault) {
            Toast.makeText(
                context,
                "SENTINAL wasn't set as default dialer (declined or cancelled)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultDialer = checkDefaultDialer()
                roleAvailable = checkRoleAvailable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxSize().background(SentinalBg)) {
        Text(
            "Settings", style = MaterialTheme.typography.headlineMedium, color = SentinalTextPrimary,
            modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 4.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp)
                    .background(SentinalEmerald.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .border(1.dp, SentinalEmerald.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Icon(Icons.Filled.Shield, null, tint = SentinalEmerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Zero storage, by default", color = SentinalEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Nothing from your calls is kept unless you flag it yourself. No exceptions, no fine print.",
                        color = SentinalTextSecondary, fontSize = 12.sp
                    )
                }
            }

            SettingsSection("Default phone app") {
                when {
                    isDefaultDialer -> SettingsRow(
                        label = "SENTINAL is your default dialer",
                        sub = "Required for call protection to work",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = SentinalEmerald, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Active", color = SentinalEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    roleAvailable -> SettingsRow(
                        label = "SENTINAL is not your default dialer",
                        sub = "Tap to set SENTINAL as default — protection won't work otherwise",
                        onClick = {
                            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            if (intent != null) {
                                roleLauncher.launch(intent)
                            } else {
                                Toast.makeText(context, "Couldn't start the default-dialer request — see Settings > Developer > Dialer role tools for diagnostics", Toast.LENGTH_LONG).show()
                            }
                        },
                        trailing = { Icon(Icons.Filled.ChevronRight, null, tint = SentinalTextMuted) }
                    )
                    else -> SettingsRow(
                        label = "Default dialer role unavailable on this device",
                        sub = "Check Settings > Developer > Dialer role tools, or your device's Default apps settings",
                        onClick = onOpenDialerRoleDebug,
                        trailing = { Icon(Icons.Filled.ChevronRight, null, tint = SentinalTextMuted) }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsSection("Protection") {
                SettingsRow(
                    label = "Always Protect",
                    sub = "Automatically secure every call",
                    trailing = {
                        Toggle(checked = alwaysProtect, onCheckedChange = {
                            alwaysProtect = it
                            SecuritySettings.setAlwaysProtectEnabled(context, it)
                        })
                    }
                )
                SettingsRow(
                    label = "Auto-secure unknown callers",
                    sub = "Automatically enable Secure Mode for numbers not in your contacts",
                    trailing = {
                        Toggle(checked = autoSecureUnknown, onCheckedChange = {
                            autoSecureUnknown = it
                            SecuritySettings.setAutoSecureUnknownCallersEnabled(context, it)
                        })
                    }
                )
                SettingsRow(
                    label = "Guardian Mode",
                    sub = "Falls back automatically if your carrier doesn't support full protection"
                )
            }

            Spacer(Modifier.height(22.dp))
            SettingsSection("About") {
                SettingsRow(label = "Version", trailing = { Text("1.0.0", color = SentinalTextMuted, fontSize = 11.sp) })
                SettingsRow(
                    label = "Permissions",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Manage", color = SentinalTextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Filled.ChevronRight, null, tint = SentinalTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }

            Spacer(Modifier.height(22.dp))
            SettingsSection("Developer") {
                SettingsRow(
                    label = "Dialer role tools",
                    sub = "Grant/verify default dialer, full-screen intent",
                    onClick = onOpenDialerRoleDebug,
                    trailing = { Icon(Icons.Filled.ChevronRight, null, tint = SentinalTextMuted) }
                )
                SettingsRow(
                    label = "Bridge test",
                    sub = "Test the call-merge / SIP bridge flow",
                    onClick = onOpenBridgeTestDebug,
                    trailing = { Icon(Icons.Filled.ChevronRight, null, tint = SentinalTextMuted) }
                )
            }
        }
        BottomNavBar(current = currentTab, onSelect = onTabSelect)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), color = SentinalTextMuted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
        Column(modifier = Modifier.background(SentinalBtnBg, RoundedCornerShape(14.dp)), content = content)
    }
}

@Composable
private fun SettingsRow(
    label: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = SentinalTextPrimary, fontSize = 14.sp)
            if (sub != null) Text(sub, color = SentinalTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
    }
}

@Composable
private fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bg = if (checked) SentinalEmerald else Color(0xFF3A3A3A)
    Box(modifier = Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(999.dp)).background(bg).clickable { onCheckedChange(!checked) }) {
        Box(
            modifier = Modifier.padding(3.dp).size(20.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(RoundedCornerShape(50)).background(Color.White)
        )
    }
}