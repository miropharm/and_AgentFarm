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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.now.NowOpen
import com.muvusoft.agentfarm.core.now.PageLink
import com.muvusoft.agentfarm.core.now.NowTarget
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.view.ShellRequest
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.ConversationPreload
import com.muvusoft.agentfarm.net.ConversationStore
import com.muvusoft.agentfarm.net.NowSource
import com.muvusoft.agentfarm.net.NowStore
import com.muvusoft.agentfarm.ui.conversation.ConversationRoute
import com.muvusoft.agentfarm.ui.menu.MenuRoute
import com.muvusoft.agentfarm.ui.page.AwayScreen
import com.muvusoft.agentfarm.ui.page.FarmPage
import com.muvusoft.agentfarm.ui.page.WaitScreen
import com.muvusoft.agentfarm.ui.page.WakeOffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The open Conversation survives a turn of the phone: its key is kept as the farm's own JSON. */
private val KeySaver = Saver<ConversationKey?, String>(
    save = { k -> k?.let { Codec.json.encodeToString(ConversationKey.serializer(), it) } },
    restore = { Codec.json.decodeFromString(ConversationKey.serializer(), it) },
)

/**
 * A farm's Now, fed by its NowSource while it is in view. A row opens its Conversation over it and + the
 * new-session sheet; the Menu, a need with no session and a Conversation's details open the farm's own pages
 * (Back returns to where they were opened).
 */
@Composable
fun NowRoute(
    farmId: String,
    manager: ConnectionManager,
    store: NowStore,
    conversations: ConversationStore,
    talk: String?,
    onTalkOpened: () -> Unit,
    onFarms: () -> Unit,
    onSettings: () -> Unit,
) {
    val source = remember(farmId) { NowSource(manager, store, farmId, preload = ConversationPreload(manager, conversations, farmId)) }
    val state by source.state.collectAsState()
    val refreshing by source.refreshing.collectAsState()
    var page by remember(farmId) { mutableStateOf<ShellRequest.Open?>(null) }
    // The Menu, under the pages opened from it: Back from such a page is the Menu.
    var menu by rememberSaveable(farmId) { mutableStateOf(false) }
    // A notification's session: its Conversation is the first frame, over Now, so Back is Now.
    val talking = { id: String -> (NowOpen.of(source.state.value, id) as? NowTarget.Conversation)?.key }
    var conversation by rememberSaveable(farmId, stateSaver = KeySaver) { mutableStateOf(talk?.let(talking)) }
    LaunchedEffect(talk) {
        val id = talk ?: return@LaunchedEffect
        talking(id)?.let {
            page = null
            menu = false
            conversation = it
        }
        onTalkOpened()
    }
    // The page a Conversation was opened from: Back from the Conversation goes back to it.
    var under by remember(farmId) { mutableStateOf<ShellRequest.Open?>(null) }
    val open = page
    if (open != null) {
        FarmPage(
            farmId = farmId, page = open.page, manager = manager, args = open.args,
            onBack = { page = null },
            onLink = { from, to ->
                val target = NowOpen.link(state, to)
                when (target) {
                    PageLink.Now -> {
                        page = null
                        menu = false
                    }
                    is PageLink.Conversation -> {
                        under = from
                        page = null
                        conversation = target.key
                    }
                    is PageLink.Page -> Unit
                }
                target !is PageLink.Page
            },
        )
        return
    }
    val talk = conversation
    if (talk != null) {
        ConversationRoute(
            farmId = farmId,
            key = talk,
            manager = manager,
            store = conversations,
            onBack = {
                conversation = null
                page = under
                under = null
            },
            onFarmPage = { page = NowOpen.page(talk) },
        )
        return
    }
    if (menu) {
        MenuRoute(
            farmId = farmId, manager = manager,
            onNow = { menu = false },
            onPage = { page = ShellRequest.Open(it) },
            onFarms = onFarms,
            onSettings = onSettings,
        )
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
            onOpen = {
                when (val to = NowOpen.of(state, it)) {
                    is NowTarget.Conversation -> conversation = to.key
                    is NowTarget.Page -> page = to.open
                }
            },
            onQuick = { w, q -> source.quick(w, q) },
            onNew = { creating = true },
            onRefresh = { scope.launch { source.refresh() } },
            onMoreRecent = { scope.launch { source.more() } },
            onRetry = { scope.launch { source.refresh() } },
            onFarm = onFarms,
            onMenu = { menu = true },
        ),
        wake = { WakeOffer(farmId = farmId, farmName = name, link = link, manager = manager, overCopy = true) },
    )
}
