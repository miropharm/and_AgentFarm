package com.muvusoft.agentfarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebStorage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.net.LinkService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.ui.page.FarmPage
import com.muvusoft.agentfarm.core.pairing.PairingStep
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.FarmStore
import com.muvusoft.agentfarm.net.PairClient
import com.muvusoft.agentfarm.core.lock.LockPolicy
import com.muvusoft.agentfarm.core.speech.SpeakMode
import com.muvusoft.agentfarm.net.ShellPrefs
import com.muvusoft.agentfarm.ui.lock.Destructive
import com.muvusoft.agentfarm.ui.lock.LockGate
import com.muvusoft.agentfarm.ui.lock.OwnerCheck
import com.muvusoft.agentfarm.ui.lock.rememberDestructiveConfirm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The shell's state and actions. `incomingLink` is a pairing link the system handed over (QR via camera, a tapped link). */
@Composable
fun ShellApp(
    versionName: String,
    store: FarmStore,
    prefs: ShellPrefs,
    manager: ConnectionManager,
    incomingLink: String?,
    /** Text another app shared to Agent Farm; `onShareDone` once it was sent or given up. */
    incomingShare: String?,
    onShareDone: () -> Unit,
    /** The widget or the tile was tapped: open the focused farm's Now page; `onOpenNowDone` once it is. */
    openNow: Boolean,
    onOpenNowDone: () -> Unit,
) {
    val context = LocalContext.current
    var lockOnOpen by remember { mutableStateOf(prefs.lockOnOpen) }
    var speakMode by remember { mutableStateOf(prefs.speakMode) }
    var voiceLanguage by remember { mutableStateOf(prefs.voiceLanguage) }
    val availability = OwnerCheck.availability(context)
    fun askOwner(title: String, then: (Boolean) -> Unit) = OwnerCheck.ask(context, title, null, then)
    LockGate(
        enabled = lockOnOpen && availability == LockPolicy.Availability.READY,
        ask = { askOwner(context.getString(R.string.lock_ask_open), it) },
        // Pages keep no copy of the farm behind the lock; their HTTP cache goes with each WebView's release.
        onLocked = { WebStorage.getInstance().deleteAllData() },
    ) {
        if (incomingShare != null) {
            ShareScreen(text = incomingShare, manager = manager, onDone = onShareDone)
            return@LockGate
        }
        Shell(
            versionName = versionName,
            store = store,
            manager = manager,
            incomingLink = incomingLink,
            openNow = openNow,
            onOpenNowDone = onOpenNowDone,
            settings = SettingsUi(
                lockOnOpen = lockOnOpen,
                availability = availability,
                onLockToggle = { wanted ->
                    askOwner(context.getString(if (wanted) R.string.lock_ask_turn_on else R.string.lock_ask_turn_off)) { ok ->
                        if (ok) { prefs.lockOnOpen = wanted; lockOnOpen = wanted }
                    }
                },
                speakMode = speakMode,
                onSpeakMode = { prefs.speakMode = it; speakMode = it },
                voiceLanguage = voiceLanguage,
                onVoiceLanguage = { prefs.voiceLanguage = it; voiceLanguage = it },
            ),
        )
    }
}

/** What the settings screen shows and the one way it changes the lock. */
private data class SettingsUi(
    val lockOnOpen: Boolean,
    val availability: LockPolicy.Availability,
    val onLockToggle: (Boolean) -> Unit,
    val speakMode: SpeakMode,
    val onSpeakMode: (SpeakMode) -> Unit,
    val voiceLanguage: String,
    val onVoiceLanguage: (String) -> Unit,
)

@Composable
private fun Shell(
    versionName: String,
    store: FarmStore,
    manager: ConnectionManager,
    incomingLink: String?,
    openNow: Boolean,
    onOpenNowDone: () -> Unit,
    settings: SettingsUi,
) {
    var farms by remember { mutableStateOf(store.load()) }
    var ui by remember { mutableStateOf(PairingUi()) }
    val scope = rememberCoroutineScope()
    val shell by manager.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var inSettings by rememberSaveable { mutableStateOf(false) }
    val confirm = rememberDestructiveConfirm()

    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(farms) {
        manager.setFarms(farms)
        LinkService.sync(context, anyFarm = farms.isNotEmpty())
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (farms.isNotEmpty() && Build.VERSION.SDK_INT >= 33 && !granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); now = System.currentTimeMillis() }
    }

    LaunchedEffect(openNow) {
        if (!openNow) return@LaunchedEffect
        val target = shell.focused?.takeIf { f -> farms.any { it.id == f } } ?: farms.firstOrNull()?.id
        if (target != null) { manager.focus(target); inSettings = false; opened = target }
        onOpenNowDone()
    }

    LaunchedEffect(incomingLink) {
        if (!incomingLink.isNullOrBlank()) ui = PairingUi(link = incomingLink)
    }

    val pair = pair@{
        val info = when (val step = PairingVerdict.read(ui.link)) {
            is PairingStep.Rejected -> { ui = ui.copy(message = step.problem.message, failed = true); return@pair }
            is PairingStep.Ready -> step.info
        }
        ui = ui.copy(busy = true, message = null)
        scope.launch {
            val who = DeviceIdentity(Build.MODEL, "android ${Build.VERSION.RELEASE}", versionName)
            ui = when (val r = PairClient.pair(info, who)) {
                is PairingVerdict.Result.Paired -> {
                    farms = store.save(r.farm)
                    PairingUi(message = context.getString(R.string.paired_with, r.farm.name))
                }
                is PairingVerdict.Result.Failed -> ui.copy(busy = false, message = r.problem.message, failed = true)
            }
        }
        Unit
    }

    val open = opened
    if (open != null) {
        FarmPage(farmId = open, page = PageRoute.HOME_PAGE, manager = manager, onBack = { opened = null })
        return
    }

    if (inSettings) {
        SettingsScreen(
            versionName = versionName,
            lockOnOpen = settings.lockOnOpen,
            availability = settings.availability,
            onLockToggle = settings.onLockToggle,
            speakMode = settings.speakMode,
            onSpeakMode = settings.onSpeakMode,
            voiceLanguage = settings.voiceLanguage,
            onVoiceLanguage = settings.onVoiceLanguage,
            onBack = { inSettings = false },
        )
        return
    }

    PairingScreen(
        versionName = versionName,
        farms = farms,
        links = shell.farms.associate { it.farm.id to LinkText.of(it.link, now) },
        tips = shell.farms.associate { it.farm.id to LinkText.sheet(it.link, it.farm.addresses, now) },
        ui = ui,
        onLinkChange = { ui = ui.copy(link = it, message = null, failed = false) },
        onPair = pair,
        onScan = {
            QrScan.start(
                context,
                onText = { ui = PairingUi(link = it); pair() },
                onFail = { ui = ui.copy(message = it, failed = true) },
            )
        },
        onForget = { farm ->
            confirm(
                Destructive(
                    title = context.getString(R.string.forget_title, farm.name),
                    detail = context.getString(R.string.forget_detail),
                    verb = context.getString(R.string.forget),
                    run = { farms = store.forget(farm.id) },
                ),
            )
        },
        onOpen = { manager.focus(it.id); opened = it.id },
        onSettings = { inSettings = true },
    )
}
