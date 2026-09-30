package com.muvusoft.agentfarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The shell's state and actions. `incomingLink` is a pairing link the system handed over (QR via camera, a tapped link). */
@Composable
fun ShellApp(versionName: String, store: FarmStore, manager: ConnectionManager, incomingLink: String?) {
    var farms by remember { mutableStateOf(store.load()) }
    var ui by remember { mutableStateOf(PairingUi()) }
    val scope = rememberCoroutineScope()
    val shell by manager.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }

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
                    PairingUi(message = "${r.farm.name} ile eşlendi.")
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

    PairingScreen(
        versionName = versionName,
        farms = farms,
        links = shell.farms.associate { it.farm.id to LinkText.of(it.link, now) },
        ui = ui,
        onLinkChange = { ui = ui.copy(link = it, message = null, failed = false) },
        onPair = pair,
        onForget = { farms = store.forget(it.id) },
        onOpen = { manager.focus(it.id); opened = it.id },
    )
}
