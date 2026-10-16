package com.muvusoft.agentfarm.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.muvusoft.agentfarm.core.contract.Ops
import com.muvusoft.agentfarm.core.contract.PagesList
import com.muvusoft.agentfarm.core.conversation.ConversationText
import com.muvusoft.agentfarm.core.menu.MenuBuild
import com.muvusoft.agentfarm.core.session.SessionText
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.MenuStore
import com.muvusoft.agentfarm.net.read

/** The Menu of one farm: its last list at once, then the farm's answer (pages.list), kept for the next time. */
@Composable
fun MenuRoute(farmId: String, manager: ConnectionManager, onNow: () -> Unit, onPage: (String) -> Unit, onFarms: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MenuStore(context.applicationContext) }
    var list by remember(farmId) { mutableStateOf(store.load(farmId)) }
    var failure by remember(farmId) { mutableStateOf<String?>(null) }
    var asks by remember { mutableIntStateOf(0) }
    val shell by manager.state.collectAsState()
    val link = shell.farm(farmId)?.link
    val online = link is Link.Online
    LaunchedEffect(farmId, online, asks) {
        if (!online) return@LaunchedEffect
        val r = manager.read(farmId, MenuBuild.OP)
        val fresh = r?.takeIf { it.ok }?.let { Ops.read(PagesList.serializer(), it.result) }
        if (fresh != null) {
            list = fresh
            failure = null
            store.save(farmId, fresh)
        } else {
            failure = ConversationText.notRead(r, online = manager.state.value.farm(farmId)?.link is Link.Online)
        }
    }
    BackHandler(onBack = onNow)
    val held = list
    MenuScreen(
        groups = MenuBuild.groups(held),
        loading = held == null && online && failure == null,
        failure = failure.takeIf { held == null },
        offline = if (held == null && link != null) SessionText.stale(link, 0L, System.currentTimeMillis()) else null,
        actions = MenuActions(onNow = onNow, onPage = onPage, onFarms = onFarms, onSettings = onSettings, onRetry = { asks++ }),
    )
}
