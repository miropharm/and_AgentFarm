package com.muvusoft.agentfarm.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.muvusoft.agentfarm.core.link.LinkText
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

    LaunchedEffect(farms) { manager.setFarms(farms) }
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

    PairingScreen(
        versionName = versionName,
        farms = farms,
        links = shell.farms.associate { it.farm.id to LinkText.of(it.link, now) },
        ui = ui,
        onLinkChange = { ui = ui.copy(link = it, message = null, failed = false) },
        onPair = pair,
        onForget = { farms = store.forget(it.id) },
    )
}
