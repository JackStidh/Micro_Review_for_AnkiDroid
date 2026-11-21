package com.orlikalabs.microreview

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.orlikalabs.microreview.ui.CurrentCard
import com.orlikalabs.microreview.ui.DeckItem
import com.orlikalabs.microreview.ui.HomeActions
import com.orlikalabs.microreview.ui.HomeScreen
import com.orlikalabs.microreview.ui.MoveState
import com.orlikalabs.microreview.ui.MoveSummary
import com.orlikalabs.microreview.ui.HomeStatus
import com.orlikalabs.microreview.ui.ScreenState
import com.orlikalabs.microreview.ui.SetupNeed
import com.orlikalabs.microreview.ui.aero.AeroPalette
import com.orlikalabs.microreview.ui.aero.AeroTheme
import com.orlikalabs.microreview.ui.aero.AeroThemes
import com.orlikalabs.microreview.ui.aero.ThemeVisit
import com.orlikalabs.microreview.ui.aero.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

private const val DECK_CHANGE_DEBOUNCE_MS = 800L
private const val STATUS_REFRESH_MS = 10_000L
private const val TAG = "BackgroundService"

/**
 * The home screen ([HomeScreen]): where things stand (the next card, a pause, a snooze), the next card, what is
 * left to allow, the decks to review in their order, and the theme. Loads the decks off the main thread.
 */
class MainActivity : ComponentActivity() {
    private var screenState by mutableStateOf<ScreenState>(ScreenState.Loading)
    private val decks = mutableStateListOf<DeckItem>()
    private val selectedDeckIds = mutableStateListOf<Long>()
    private var currentCard by mutableStateOf<CurrentCard>(CurrentCard.NotStarted)
    private var status by mutableStateOf(HomeStatus())
    private var needs by mutableStateOf(emptyList<SetupNeed>())
    private var palette by mutableStateOf(AeroThemes.SAKURA)
    private var themeChoice by mutableStateOf(ThemeChoice())
    private var move by mutableStateOf(MoveSummary())

    /** Off for a theme shuffled as the app comes back into view: it's simply there, no crossfade. */
    private var themeAnimated by mutableStateOf(true)

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.POST_NOTIFICATIONS] == false) {
                Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        palette = ThemeVisit.arrive(this)
        themeChoice = AeroThemes.choice(this)
        applyBars(palette)
        super.onCreate(savedInstanceState)
        Notifications.removeOldChannels(this)
        if (DeckSelection.load(this).isNotEmpty()) {
            // Reviewing is started: make sure the periodic check uses the current interval.
            PeriodicWorker.schedule(applicationContext)
        }
        // The monitor of the nudges and the app gate, if they are on: opening the app is always allowed to start it.
        UsageMonitorService.sync(applicationContext)
        requestMissingPermissions()

        val actions = HomeActions(
            onToggleDeck = ::toggleDeck,
            onMoveDeck = ::moveDeck,
            onGrantPermission = ::requestMissingPermissions,
            onRetry = ::refresh,
            onOpenSettings = { startActivity(Intent(this, FocusSettingsActivity::class.java)) },
            onCardNow = { startActivity(FocusReviewActivity.sessionIntent(this)) },
            onSnooze = ::snooze,
            onResume = {
                FocusState.resume(this)
                UsageMonitorService.refreshNotification(this)
                status = readStatus()
            },
            onSync = {
                // In front: allowed to start AnkiDroid's sync.
                AnkiSync.request(this, "from the app")
                status = readStatus()
            },
            onTheme = { tapped -> showTheme(AeroThemes.tap(this, tapped)) },
            onOpenMove = { startActivity(MoveActivity.intent(this)) },
            onThemeLock = {
                AeroThemes.toggleLock(this)
                themeChoice = AeroThemes.choice(this)
            },
            onFix = ::fix,
        )
        setContent {
            Crossfade(palette, animationSpec = if (themeAnimated) tween(450) else snap(), label = "theme") { shown ->
                AeroTheme(shown) {
                    HomeScreen(
                        state = screenState,
                        decks = decks,
                        selectedDeckIds = selectedDeckIds,
                        currentCard = currentCard,
                        status = status,
                        needs = needs,
                        snoozeMinutes = FocusSettings.snoozeMinutes(this),
                        actions = actions,
                        themeChoice = themeChoice,
                        move = move,
                    )
                }
            }
        }
        // The status moves on by itself (the next card's minutes, a snooze ending): read again while shown.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    status = readStatus()
                    delay(STATUS_REFRESH_MS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the settings or the system's: the theme, the decks or a permission may have changed.
        showTheme(AeroThemes.current(this))
        move = MoveSummary(MoveStore.phase(this) != null, MoveState(days = MoveStore.days(this)).doneToday)
        refresh()
    }

    override fun onStart() {
        super.onStart()
        // Back in view after a moment away: a new theme, there at once.
        val arriving = ThemeVisit.arrive(this)
        ThemeVisit.started()
        if (arriving.id != palette.id) showTheme(arriving, animated = false)
    }

    override fun onStop() {
        super.onStop()
        ThemeVisit.stopped()
    }

    private fun showTheme(shown: AeroPalette, animated: Boolean = true) {
        themeChoice = AeroThemes.choice(this)
        if (shown.id == palette.id) return
        themeAnimated = animated
        palette = shown
        applyBars(shown)
    }

    /** Light or dark status and navigation bar icons, for the theme. */
    private fun applyBars(palette: AeroPalette) {
        val bars = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
    }

    private fun snooze() {
        FocusState.snooze(this, FocusSettings.snoozeMinutes(this))
        val context = applicationContext
        thread { NudgeSession.withdraw(context) }
        UsageMonitorService.refreshNotification(this)
        status = readStatus()
    }

    private fun requestMissingPermissions() {
        val missing = buildList {
            if (AnkiDroidHelper.isApiAvailable(this@MainActivity) && !AnkiDroidHelper(this@MainActivity).isPermissionGranted) {
                add(AnkiDroidContract.READ_WRITE_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        status = readStatus()
        needs = readNeeds()
        if (!AnkiDroidHelper.isApiAvailable(this)) {
            screenState = ScreenState.ApiUnavailable
            return
        }
        if (!AnkiDroidHelper(this).isPermissionGranted) {
            screenState = ScreenState.PermissionMissing
            return
        }
        lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    // The card may have been reviewed or changed in AnkiDroid since it was shown.
                    CardStateController.syncWithAnkiDroid(applicationContext)
                    loadDecks()
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Could not load the decks", e)
                null
            }
            if (loaded == null) {
                screenState = ScreenState.LoadFailed
                return@launch
            }
            decks.clear()
            decks.addAll(loaded.decks)
            selectedDeckIds.clear()
            selectedDeckIds.addAll(loaded.selectedDeckIds)
            currentCard = loaded.currentCard
            screenState = ScreenState.Ready
        }
    }

    private class LoadedData(val decks: List<DeckItem>, val selectedDeckIds: List<Long>, val currentCard: CurrentCard)

    /** The decks and the displayed card, or null if AnkiDroid doesn't give its decks. */
    private fun loadDecks(): LoadedData? {
        val anki = AnkiDroidHelper(this)
        val names = anki.deckNames ?: return null
        val counts = anki.getDeckCounts()
        val decks = names.map { (id, name) ->
            val count = counts[id]
            DeckItem(id, name, newCount = count?.get(2), learnCount = count?.get(0), reviewCount = count?.get(1))
        }.sortedBy { it.name.lowercase() }
        val selected = DeckSelection.load(this).filter { it in names }
        return LoadedData(decks, selected, readCurrentCard(anki))
    }

    private fun readCurrentCard(anki: AnkiDroidHelper): CurrentCard {
        val state = anki.storedState ?: return CurrentCard.NotStarted
        if (!state.hasCard()) {
            return CurrentCard.NoneDue
        }
        val question = CardText.clean(state.question)
        // A card stored by an older version may have nothing left to show (e.g. a sound-only question).
        if (question.isEmpty() || CardText.hasSound(state.question)) {
            return CurrentCard.NotStarted
        }
        return CurrentCard.Showing(question, anki.getDeckName(state.deckId) ?: "")
    }

    private fun readStatus(): HomeStatus {
        val now = System.currentTimeMillis()
        val snoozeUntil = FocusSettings.snoozeUntil(this)
        val snoozed = QuietTime.isSnoozed(snoozeUntil, now)
        return HomeStatus(
            nudgesOn = FocusSettings.nudgesEnabled(this),
            gateOn = FocusSettings.gateEnabled(this),
            gatedApps = FocusSettings.gatedApps(this).size,
            nextCardMinutes = MonitorStatus.nextCardMs?.let { ((it + 59_999) / 60_000).toInt() },
            pause = if (snoozed) null else FocusState.pauseReason(this, now),
            snoozedUntil = if (snoozed) UsageMonitorService.formatTime(this, snoozeUntil) else null,
            pcPaired = FocusSettings.pcPairingCode(this) != null,
            answersWaiting = AnkiSync.isPending(this),
        )
    }

    private fun readNeeds(): List<SetupNeed> = buildList {
        val needsOverlay = FocusSettings.gateEnabled(this@MainActivity) || FocusSettings.nudgesEnabled(this@MainActivity)
        if (FocusSettings.monitorWanted(this@MainActivity) && !FocusState.hasUsageAccess(this@MainActivity)) add(SetupNeed.USAGE_ACCESS)
        if (needsOverlay && !FocusState.canOpenOverApps(this@MainActivity)) add(SetupNeed.OVERLAY)
        if (!NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) add(SetupNeed.NOTIFICATIONS)
        if (isBackgroundRestricted()) add(SetupNeed.BATTERY)
    }

    private fun fix(need: SetupNeed) {
        when (need) {
            SetupNeed.USAGE_ACCESS -> open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            SetupNeed.OVERLAY -> open(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            )
            SetupNeed.NOTIFICATIONS -> open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            // The app's info page, where "App battery usage" is.
            SetupNeed.BATTERY -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun open(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) startActivity(fallback)
        }
    }

    private fun toggleDeck(deckId: Long) {
        if (!selectedDeckIds.remove(deckId)) {
            selectedDeckIds.add(deckId)
        }
        onDeckSelectionChanged()
    }

    private fun moveDeck(deckId: Long, offset: Int) {
        val from = selectedDeckIds.indexOf(deckId)
        val to = from + offset
        if (from == -1 || to !in selectedDeckIds.indices) return
        selectedDeckIds.add(to, selectedDeckIds.removeAt(from))
        onDeckSelectionChanged()
    }

    private var deckChangeSync: Job? = null

    /**
     * Saves the selection and, once the user stops changing it, shows the new first card if it differs. Reviewing
     * starts with the first selected deck, and stops when none is left.
     */
    private fun onDeckSelectionChanged() {
        DeckSelection.save(this, selectedDeckIds.toList())
        if (selectedDeckIds.isNotEmpty()) PeriodicWorker.schedule(applicationContext)
        deckChangeSync?.cancel()
        deckChangeSync = lifecycleScope.launch {
            delay(DECK_CHANGE_DEBOUNCE_MS)
            val action = try {
                withContext(Dispatchers.IO) { CardStateController.syncWithAnkiDroid(applicationContext) }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Could not sync with AnkiDroid", e)
                null
            }
            // A failed sync goes through refresh() too, which shows the error screen if AnkiDroid still fails.
            if (action != CardSync.Action.NONE) refresh()
        }
    }

    /**
     * Whether the user restricted the app's background activity ("Restricted" battery usage), which stops the
     * hourly check and the monitor. The default battery optimization ("Optimized") is fine.
     */
    private fun isBackgroundRestricted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
}
