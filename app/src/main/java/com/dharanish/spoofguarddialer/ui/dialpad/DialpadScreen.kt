package com.dharanish.spoofguarddialer.ui.dialpad

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.ui.components.BottomNavBar
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.components.SmallAvatar
import com.dharanish.spoofguarddialer.ui.contacts.ContactsViewModel
import com.dharanish.spoofguarddialer.ui.theme.*

/*
 * Telephone keypad mapping.
 *
 * "VM" is used under 1 as a subtle voicemail-related cue.
 */
private val keypadRows = listOf(
    listOf("1" to "VM", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "", "#" to "")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(
    currentTab: SentinalTab,
    onTabSelect: (SentinalTab) -> Unit,
    onCallClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    contactsViewModel: ContactsViewModel = viewModel()
) {
    var dialed by remember { mutableStateOf("") }

    val context = LocalContext.current
    val contacts by contactsViewModel.contacts.collectAsState()

    LaunchedEffect(Unit) {
        contactsViewModel.loadContacts()
    }

    // -------------------------------------------------------------
    // NORMALIZED NUMBER
    // -------------------------------------------------------------

    val normalizedDialed = remember(dialed) {
        dialed.filter { it.isDigit() }
    }

    // -------------------------------------------------------------
    // CONTACT MATCHING
    // -------------------------------------------------------------

    val matches = remember(contacts, normalizedDialed) {
        if (normalizedDialed.isBlank()) {
            emptyList()
        } else {
            contacts
                .filter {
                    it.number
                        .filter { c -> c.isDigit() }
                        .contains(normalizedDialed)
                }
                .take(3)
        }
    }

    // =============================================================
    // MAIN SCREEN
    // =============================================================

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SentinalBg)
    ) {

        // =========================================================
        // SENTINEL STATUS
        // =========================================================

        Text(
            text = "SENTINAL secured",
            color = SentinalTextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp)
        )

        // =========================================================
        // DIALED NUMBER
        // =========================================================

        Text(
            text = dialed.ifEmpty { " " },
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 26.sp
            ),
            color = SentinalTextPrimary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    top = 12.dp,
                    bottom = 8.dp
                )
        )

        // =========================================================
        // DYNAMIC CONTACT MATCHES / ADD CONTACT
        // =========================================================
        //
        // Avatars and labels sized up from 56dp/12sp to 68dp/13sp so
        // matches are actually easy to identify at a glance instead
        // of reading as an afterthought under the dialed number.
        // =========================================================

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
                .heightIn(min = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {

            if (normalizedDialed.isNotBlank()) {

                if (matches.isNotEmpty()) {

                    matches.forEach { contact ->

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(84.dp)
                                .clickable(
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    },
                                    indication = null,
                                    onClick = {
                                        onCallClick(contact.number)
                                    }
                                )
                        ) {

                            SmallAvatar(
                                initial = contact.name
                                    .take(1)
                                    .uppercase(),
                                size = 68.dp
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = contact.name,
                                color = SentinalTextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }

                } else {

                    // -------------------------------------------------
                    // ADD CONTACT
                    // -------------------------------------------------

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                            onClick = {

                                val intent = Intent(
                                    Intent.ACTION_INSERT,
                                    ContactsContract.Contacts.CONTENT_URI
                                ).apply {
                                    putExtra(
                                        ContactsContract.Intents.Insert.PHONE,
                                        dialed
                                    )
                                }

                                context.startActivity(intent)
                            }
                        )
                    ) {

                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(
                                    color = SentinalBtnBg,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                tint = SentinalEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "Add contact",
                            color = SentinalEmerald,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // =========================================================
        // KEYPAD
        // =========================================================
        //
        // The keypad Column now fills the FULL height of its
        // weight(1f) container (fillMaxHeight) instead of just
        // wrapping its own content. With Arrangement.SpaceEvenly,
        // the gaps between rows stretch to consume exactly whatever
        // space is available — so there is nothing left over to show
        // up as a dead zone, on any screen size. This is the same
        // technique stock Android/iOS dialers use: the keypad area
        // itself is elastic, not the padding around it.
        //
        // heightIn(min) is kept only as a floor so keys never get
        // uncomfortably cramped on very short screens.
        // =========================================================

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .heightIn(min = 320.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                keypadRows.forEach { row ->

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        row.forEach { (num, letters) ->

                            KeypadKey(
                                num = num,
                                letters = letters,
                                modifier = Modifier.size(
                                    width = 56.dp,
                                    height = 72.dp
                                ),
                                onClick = {
                                    dialed += num
                                }
                            )
                        }
                    }
                }
            }
        }

        // =========================================================
        // CALL CONTROLS
        // =========================================================
        //
        // Three equal zones:
        //
        //     [ SIM 1 ]     [ CALL ]     [ BACKSPACE ]
        //
        // This guarantees that the call button stays exactly
        // centered on the screen.
        // =========================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // -----------------------------------------------------
            // LEFT — SIM
            // -----------------------------------------------------

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {

                Row(
                    modifier = Modifier
                        .background(
                            color = SentinalBtnBg,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {

                    Icon(
                        imageVector = Icons.Filled.SimCard,
                        contentDescription = null,
                        tint = SentinalTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )

                    Text(
                        text = "SIM 1",
                        color = SentinalTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // -----------------------------------------------------
            // CENTER — CALL
            // -----------------------------------------------------

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            color = SentinalEmerald,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                            enabled = dialed.isNotBlank(),
                            onClick = {
                                onCallClick(dialed)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // -----------------------------------------------------
            // RIGHT — BACKSPACE
            // -----------------------------------------------------
            //
            // combinedClickable adds a long-press: holding Backspace
            // clears the whole dialed number in one go, matching the
            // universal convention on native dialers.
            // -----------------------------------------------------

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .combinedClickable(
                            enabled = dialed.isNotEmpty(),
                            onClick = {
                                dialed = dialed.dropLast(1)
                            },
                            onLongClick = {
                                dialed = ""
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = SentinalTextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // =========================================================
        // BOTTOM NAVIGATION
        // =========================================================

        BottomNavBar(
            current = currentTab,
            onSelect = onTabSelect
        )
    }
}

@Composable
private fun KeypadKey(
    num: String,
    letters: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
            indication = null,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Slightly larger digits to match the bigger key hit area.
            Text(
                text = num,
                color = SentinalTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium
            )

            if (letters.isNotEmpty()) {

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = letters,
                    color = SentinalTextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}