package com.muvusoft.agentfarm.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muvusoft.agentfarm.core.lock.LockPolicy

/**
 * Shows `content` only to the owner when the open lock is on. A lock switched on while the app is open
 * does not lock it: the owner just proved themselves to switch it on.
 */
@Composable
fun LockGate(enabled: Boolean, ask: (onResult: (Boolean) -> Unit) -> Unit, content: @Composable () -> Unit) {
    var unlocked by rememberSaveable { mutableStateOf(!enabled) }
    var backgroundSince by rememberSaveable { mutableStateOf<Long?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundSince = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    if (LockPolicy.locked(enabled, unlocked, backgroundSince, System.currentTimeMillis())) unlocked = false
                    backgroundSince = null
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!LockPolicy.locked(enabled, unlocked, backgroundSince = null, now = 0)) return content()

    var asking by remember { mutableStateOf(false) }
    val askNow = {
        if (!asking) {
            asking = true
            ask { ok -> asking = false; if (ok) unlocked = true }
        }
    }
    LaunchedEffect(Unit) { askNow() }
    LockedScreen(onOpen = askNow)
}

@Composable
private fun LockedScreen(onOpen: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("lock-screen")) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Agent Farm kilitli", style = MaterialTheme.typography.titleLarge)
            Text("Açmak için kimliğinizi doğrulayın.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpen, modifier = Modifier.padding(top = 24.dp).testTag("lock-open")) { Text("Aç") }
        }
    }
}
