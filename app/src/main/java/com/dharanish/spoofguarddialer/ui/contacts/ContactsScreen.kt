package com.dharanish.spoofguarddialer.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Close
import com.dharanish.spoofguarddialer.util.PhoneActions
import com.dharanish.spoofguarddialer.ui.components.NumberActionsSheet
import com.dharanish.spoofguarddialer.telecomcore.DialString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallPlacer
import com.dharanish.spoofguarddialer.ui.components.BottomNavBar
import com.dharanish.spoofguarddialer.ui.components.PermissionNeededPrompt
import com.dharanish.spoofguarddialer.ui.components.SentinalTab
import com.dharanish.spoofguarddialer.ui.components.SmallAvatar
import com.dharanish.spoofguarddialer.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val ALPHABET = ('A'..'Z').toList() + '#'
private val BUBBLE_SIZE = 56.dp

@Composable
fun ContactsScreen(
    currentTab: SentinalTab,
    onTabSelect: (SentinalTab) -> Unit,
    onEditNumber: (String) -> Unit = {},
    permissionRevision: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = viewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val favorites by viewModel.favorites.collectAsState()
    var selectedContact by remember { mutableStateOf<ContactEntry?>(null) }

    fun hasPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasPermission()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionGranted, permissionRevision) {
        permissionGranted = hasPermission()
        viewModel.loadContacts()
    }

    // Filtering/grouping thousands of contacts must not compete with fling frames.
    val sections by produceState<List<Pair<Char, List<ContactEntry>>>>(emptyList(), contacts, query, favoritesOnly, favorites) {
        val sourceContacts = contacts
        val filterQuery = query.trim()
        val onlyFavorites = favoritesOnly
        val favoriteNumbers = favorites
        if (filterQuery.isNotBlank()) delay(120)
        value = withContext(Dispatchers.Default) {
            val numberQuery = DialString.normalize(filterQuery)
            sourceContacts.asSequence().filter {
                (!onlyFavorites || it.normalizedNumber in favoriteNumbers) &&
                    (filterQuery.isBlank() || it.name.contains(filterQuery, ignoreCase = true) ||
                        (numberQuery.isNotBlank() && it.normalizedNumber.contains(numberQuery)))
            }.groupBy { entry ->
                entry.name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
            }.entries.sortedBy { if (it.key == '#') Char.MAX_VALUE else it.key }
                .map { it.key to it.value } // Provider already returns localized name order.
        }
    }

    val letterIndex = remember(sections) {
        val map = mutableMapOf<Char, Int>()
        var idx = 0
        sections.forEach { (letter, list) ->
            map[letter] = idx
            idx += 1 + list.size
        }
        map
    }

    val availableLetters = remember(sections) { sections.map { it.first }.toSet() }

    val listState = rememberLazyListState()

    // Single source of truth for "what letter is the sidebar currently asking for".
    // A dedicated collector below turns this into scroll actions, so gesture callbacks
    // never have to launch scroll coroutines themselves.
    var scrubLetter by remember { mutableStateOf<Char?>(null) }

    // One persistent collector instead of scope.launch-per-touch-event. collectLatest
    // guarantees at most one scrollToItem in flight — if the finger moves to a new
    // letter mid-scroll, the stale scroll is dropped (never even started) instead of
    // being launched and immediately cancelled, which is what was causing the churn.
    LaunchedEffect(letterIndex) {
        snapshotFlow { scrubLetter }
            .collectLatest { letter ->
                val index = letter?.let { letterIndex[it] } ?: return@collectLatest
                // Instant jump during scrubbing — an animated scroll re-triggered on
                // every drag pixel fights itself and looks stuttery. Instant tracking
                // is what makes an index scrubber feel smooth and responsive.
                listState.scrollToItem(index)
            }
    }

    Column(modifier = modifier.fillMaxSize().background(SentinalBg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Contacts", style = MaterialTheme.typography.headlineMedium, color = SentinalTextPrimary,
            modifier = Modifier.weight(1f).padding(start = 22.dp, top = 18.dp, bottom = 4.dp)
        )
        IconButton(onClick = { PhoneActions.addContact(context, "") }, modifier = Modifier.padding(end = 12.dp, top = 12.dp)) {
            Icon(Icons.Default.PersonAdd, "Create contact")
        }
        }

        if (!permissionGranted) {
            PermissionNeededPrompt(
                message = "SENTINAL needs access to your contacts to show and match them here.",
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp).fillMaxWidth()
                    .background(SentinalBtnBg, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Search, null, tint = SentinalTextMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search contacts", color = SentinalTextMuted, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = SentinalTextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(SentinalEmerald),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Clear search")
                }
            }

            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("All contacts") })
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favorites") }, leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) })
            }
            // This Box's height (weight(1f)) is exactly the area between the search bar
            // and the bottom nav — the alphabet sidebar and its bubble are measured
            // against THIS height, so they naturally never overlap the footer below.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 36.dp)
                ) {
                    sections.forEach { (letter, sectionContacts) ->
                        item(key = "header_$letter", contentType = "section") {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(letter.toString(), color = SentinalEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(sectionContacts, key = { it.normalizedNumber }, contentType = { "contact" }) { contact ->
                            ContactRow(contact, onOpen = { selectedContact = contact }, onCall = { CallPlacer.place(context, contact.number) })
                        }
                    }
                }

                AlphabetSidebar(
                    letters = ALPHABET,
                    availableLetters = availableLetters,
                    onScrub = { letter ->
                        scrubLetter = if (letter in availableLetters) letter
                        else availableLetters.minByOrNull { abs(it.code - letter.code) }
                    },
                    onScrubEnd = { scrubLetter = null },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
                )
                if (sections.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (viewModel.isLoading) CircularProgressIndicator()
                    else {
                        Text(viewModel.error ?: if (query.isNotBlank()) "No matching contacts" else if (favoritesOnly) "Your favorites will appear here" else "No contacts yet", style = MaterialTheme.typography.bodyLarge)
                        if (viewModel.error != null) TextButton(onClick = { viewModel.loadContacts() }) { Text("Try again") }
                        else if (favoritesOnly) Text("Open a contact to add it to favorites", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        BottomNavBar(current = currentTab, onSelect = onTabSelect)
    }
    selectedContact?.let { contact -> NumberActionsSheet(
        title = contact.name, number = contact.number, lookupUri = contact.lookupUri, details = contact.label,
        onDismiss = { selectedContact = null }, onEditNumber = onEditNumber,
        favorite = DialString.normalize(contact.number) in favorites, onFavorite = { viewModel.toggleFavorite(contact.number) },
    ) }
}

@Composable
private fun ContactRow(contact: ContactEntry, onOpen: () -> Unit, onCall: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 72.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SmallAvatar(initial = contact.initial)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, color = SentinalTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.number, color = SentinalTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onCall) { Icon(Icons.Default.Call, "Call " + contact.name) }
        }
        HorizontalDivider(color = SentinalDivider, thickness = 0.5.dp)
    }
}

@Composable
private fun AlphabetSidebar(
    letters: List<Char>,
    availableLetters: Set<Char>,
    onScrub: (Char) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    var activeIndex by remember { mutableIntStateOf(-1) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }

    val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }

    fun updateFromY(y: Float) {
        if (heightPx <= 0f) return
        val perLetter = heightPx / letters.size
        val idx = (y / perLetter).toInt().coerceIn(0, letters.size - 1)
        touchY = y.coerceIn(0f, heightPx)
        if (idx != activeIndex) {
            activeIndex = idx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnScrub(letters[idx])
        }
    }

    // Bubble's own top-left Y, clamped so the full 56dp bubble always stays
    // within the sidebar's own height — never spilling past the bottom (or top).
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { heightPx = it.height.toFloat() }
                // Single unified gesture loop instead of separate detectTapGestures +
                // detectDragGestures. Two detectors on the same target both process the
                // same pointer stream, and detectDragGestures imposes a touch-slop
                // threshold before it starts reporting movement — that gap between
                // "finger touched down" and "drag officially recognized" is what made
                // the scrubber feel like it was catching up to your finger. Tracking
                // every move from the first frame gives true 1:1 tracking.
                .pointerInput(letters) {
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            updateFromY(down.position.y)
                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                change.consume()
                                updateFromY(change.position.y)
                            }
                        } finally {
                            activeIndex = -1
                            currentOnScrubEnd()
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            letters.forEachIndexed { index, letter ->
                LetterTick(
                    letter = letter,
                    isActive = index == activeIndex,
                    isAvailable = letter in availableLetters
                )
            }
        }

        // Anchored to the TOP of the sidebar (not center) — its offset is purely
        // "distance from the top," which is what actually matches your finger.
        AnimatedVisibility(
            visible = activeIndex in letters.indices,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = with(density) { (-60).dp.roundToPx() },
                        // Read motion state during layout, not composition on every pixel.
                        y = (touchY - bubbleSizePx / 2f)
                            .coerceIn(0f, (heightPx - bubbleSizePx).coerceAtLeast(0f)).roundToInt()
                    )
                }
        ) {
            Box(
                modifier = Modifier.size(BUBBLE_SIZE).background(SentinalEmerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letters.getOrElse(activeIndex) { ' ' }.toString(),
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Pulled out into its own composable so each letter is its own recomposition scope.
// Previously isActive was computed inline inside the loop, so every activeIndex change
// recomposed all 27 letters (plus their animateFloatAsState instances). Now Compose can
// skip any LetterTick whose (letter, isActive, isAvailable) params didn't change — only
// the one or two ticks that actually flipped state get recomposed.
@Composable
private fun LetterTick(
    letter: Char,
    isActive: Boolean,
    isAvailable: Boolean
) {
    val scale by animateFloatAsState(if (isActive) 1.6f else 1f, label = "letterScale")
    Text(
        text = letter.toString(),
        fontSize = 10.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        color = when {
            isActive -> SentinalEmerald
            isAvailable -> SentinalTextSecondary
            else -> SentinalTextMuted.copy(alpha = 0.35f)
        },
        modifier = Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = if (isActive) -14f else 0f
        }
    )
}
