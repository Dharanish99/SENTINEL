package com.dharanish.spoofguarddialer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharanish.spoofguarddialer.ui.theme.SentinalDivider
import com.dharanish.spoofguarddialer.ui.theme.SentinalEmerald
import com.dharanish.spoofguarddialer.ui.theme.SentinalTextMuted

enum class SentinalTab(val label: String, val icon: ImageVector) {
    CONTACTS("Contacts", Icons.Filled.Person),
    RECENTS("Recents", Icons.Filled.History),
    KEYPAD("Keypad", Icons.Filled.Dialpad),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun BottomNavBar(
    current: SentinalTab,
    onSelect: (SentinalTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 0.5.dp, color = SentinalDivider)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        SentinalTab.entries.forEach { tab ->
            val active = tab == current
            val tint = if (active) SentinalEmerald else SentinalTextMuted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(tab) }
                    .padding(4.dp)
            ) {
                Icon(imageVector = tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.padding(bottom = 4.dp))
                Text(text = tab.label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}