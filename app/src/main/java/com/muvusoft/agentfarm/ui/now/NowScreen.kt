package com.muvusoft.agentfarm.ui.now

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.now.Waiting
import com.muvusoft.agentfarm.core.session.Quick
import com.muvusoft.agentfarm.core.session.SessionText
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.ui.components.Dot
import com.muvusoft.agentfarm.ui.components.ErrorBanner
import com.muvusoft.agentfarm.ui.components.MainNav
import com.muvusoft.agentfarm.ui.components.MainTab
import com.muvusoft.agentfarm.ui.components.RecentRow
import com.muvusoft.agentfarm.ui.components.RunningCard
import com.muvusoft.agentfarm.ui.components.ScreenBar
import com.muvusoft.agentfarm.ui.components.SectionTitle
import com.muvusoft.agentfarm.ui.components.SkeletonRows
import com.muvusoft.agentfarm.ui.components.StaleBanner
import com.muvusoft.agentfarm.ui.components.WaitingRow
import com.muvusoft.agentfarm.ui.theme.LocalStatusColors

class NowActions(
    val onOpen: (String) -> Unit,
    val onQuick: (Waiting, Quick) -> Unit,
    val onNew: () -> Unit,
    val onRefresh: () -> Unit,
    val onMoreRecent: () -> Unit,
    val onRetry: () -> Unit,
    val onFarm: () -> Unit,
    val onMenu: () -> Unit,
)

/** The link's colour: live, on its way, or not there. */
@Composable
fun linkColor(link: Link): Color = when (link) {
    is Link.Online -> LocalStatusColors.current.running
    is Link.Connecting -> LocalStatusColors.current.waiting
    is Link.Offline -> MaterialTheme.colorScheme.outline
    is Link.Refused -> MaterialTheme.colorScheme.error
}

/** The main screen: what waits for you, what runs, what finished. It only draws; NowState is the farm's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(state: NowState, now: Long, refreshing: Boolean, actions: NowActions) {
    Scaffold(
        topBar = {
            ScreenBar {
                Column(Modifier.clickable(onClickLabel = stringResource(R.string.farm_link_detail), onClick = actions.onFarm).testTag("now-farm")) {
                    Text(state.farm, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(linkColor(state.link))
                        Text(
                            LinkText.of(state.link, now).short,
                            Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        bottomBar = { MainNav(MainTab.NOW, onNow = actions.onRefresh, onMenu = actions.onMenu) },
        floatingActionButton = {
            if (!state.empty) {
                ExtendedFloatingActionButton(
                    onClick = actions.onNew,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.new_session)) },
                    modifier = Modifier.testTag("now-new"),
                )
            }
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = actions.onRefresh, modifier = Modifier.padding(pad).fillMaxSize()) {
            if (state.empty) EmptyNow(state, now, actions.onNew) else NowList(state, now, actions)
        }
    }
}

@Composable
private fun NowList(state: NowState, now: Long, actions: NowActions) {
    LazyColumn(Modifier.fillMaxSize().testTag("now-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
        SessionText.stale(state.link, state.updatedAt, now)?.let { item(key = "stale") { Box(Modifier.padding(bottom = 8.dp)) { StaleBanner(it) } } }
        state.error?.let { item(key = "error") { Box(Modifier.padding(bottom = 8.dp)) { ErrorBanner(it, actions.onRetry) } } }
        if (state.loading) item(key = "loading") { SkeletonRows(4, Modifier.padding(top = 12.dp)) }
        if (state.waiting.isNotEmpty()) {
            item(key = "h-waiting") { SectionTitle(stringResource(R.string.now_waiting), state.waiting.size) }
            items(state.waiting, key = { "w-" + it.session }) { w ->
                Box(Modifier.padding(bottom = 10.dp)) { WaitingRow(w, now, onOpen = { actions.onOpen(w.session) }, onQuick = { actions.onQuick(w, it) }) }
            }
        }
        if (state.running.isNotEmpty()) {
            item(key = "h-running") { SectionTitle(stringResource(R.string.now_running), state.running.size) }
            items(state.running, key = { "r-" + it.session }) { r ->
                Box(Modifier.padding(bottom = 10.dp)) { RunningCard(r, now, onOpen = { actions.onOpen(r.session) }) }
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "h-recent") { SectionTitle(stringResource(R.string.now_recent)) }
            items(state.recent, key = { "d-" + it.session }) { r -> RecentRow(r, now, onOpen = { actions.onOpen(r.session) }) }
        }
        if (state.moreRecent) {
            item(key = "more") {
                SkeletonRows(2, Modifier.padding(top = 12.dp))
                LaunchedEffect(state.recent.size) { actions.onMoreRecent() }
            }
        }
    }
}

@Composable
private fun EmptyNow(state: NowState, now: Long, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("now-empty"), horizontalAlignment = Alignment.CenterHorizontally) {
        SessionText.stale(state.link, state.updatedAt, now)?.let { StaleBanner(it) }
        Column(Modifier.padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.now_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Button(onClick = onNew) {
                Icon(Icons.Filled.Add, null)
                Text(stringResource(R.string.new_session), Modifier.padding(start = 8.dp))
            }
        }
    }
}
