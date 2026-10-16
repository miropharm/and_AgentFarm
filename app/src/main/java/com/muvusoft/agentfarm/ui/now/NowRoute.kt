package com.muvusoft.agentfarm.ui.now

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.muvusoft.agentfarm.core.now.NowOpen
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.core.view.ShellRequest
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.NowSource
import com.muvusoft.agentfarm.net.NowStore
import com.muvusoft.agentfarm.ui.page.AwayScreen
import com.muvusoft.agentfarm.ui.page.FarmPage
import com.muvusoft.agentfarm.ui.page.WaitScreen
import com.muvusoft.agentfarm.ui.page.WakeOffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A farm's Now, fed by its NowSource while it is in view. + opens the new-session sheet; a row and the Menu open
 * the farm's own pages over it (Back returns here) until their native screens take those places.
 */
@Composable
fun NowRoute(farmId: String, manager: ConnectionManager, store: NowStore, onFarms: () -> Unit) {
    val source = remember(farmId) { NowSource(manager, store, farmId) }
    val state by source.state.collectAsState()
    val refreshing by source.refreshing.collectAsState()
    var page by remember(farmId) { mutableStateOf<ShellRequest.Open?>(null) }
    val open = page
    if (open != null) {
        FarmPage(farmId = farmId, page = open.page, manager = manager, onBack = { page = null }, args = open.args)
        return
    }
    // VS Code closed on the PC, or no farm and nothing saved: the screens that say so and offer the way back.
    val shell by manager.state.collectAsState()
    val status = shell.farm(farmId)
    val link = status?.link
    val away = status?.away
    val name = status?.farm?.name ?: farmId
    if (link is Link.Online && away != null) {
        BackHandler(onBack = onFarms)
        AwayScreen(farmId = farmId, farmName = name, away = away, manager = manager)
        return
    }
    if (link !is Link.Online && state.updatedAt == 0L) {
        BackHandler(onBack = onFarms)
        WaitScreen(farmId = farmId, farmName = name, link = link, manager = manager)
        return
    }
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(source) { source.run() }
    BackHandler(onBack = onFarms)
    var creating by rememberSaveable(farmId) { mutableStateOf(false) }
    if (creating) {
        NewSessionSheet(
            farmId = farmId,
            state = state,
            manager = manager,
            onOpened = {
                creating = false
                scope.launch { source.refresh() }
            },
            onDismiss = { creating = false },
        )
    }
    NowScreen(
        state = state,
        now = now,
        refreshing = refreshing,
        actions = NowActions(
            onOpen = { page = NowOpen.of(state, it) },
            onQuick = { w, q -> source.quick(w, q) },
            onNew = { creating = true },
            onRefresh = { scope.launch { source.refresh() } },
            onMoreRecent = { scope.launch { source.more() } },
            onRetry = { scope.launch { source.refresh() } },
            onFarm = onFarms,
            onMenu = { page = ShellRequest.Open(PageRoute.HOME_PAGE) },
        ),
        wake = { WakeOffer(farmId = farmId, farmName = name, link = link, manager = manager, overCopy = true) },
    )
}
