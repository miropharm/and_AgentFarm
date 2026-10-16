package com.muvusoft.agentfarm.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.menu.MenuGroup
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.ui.components.ErrorBanner
import com.muvusoft.agentfarm.ui.components.MainNav
import com.muvusoft.agentfarm.ui.components.MainTab
import com.muvusoft.agentfarm.ui.components.ScreenBar
import com.muvusoft.agentfarm.ui.components.SectionTitle
import com.muvusoft.agentfarm.ui.components.SkeletonRows
import com.muvusoft.agentfarm.ui.components.StaleBanner

/** What the Menu's rows do. */
class MenuActions(
    val onNow: () -> Unit,
    val onPage: (String) -> Unit,
    val onFarms: () -> Unit,
    val onSettings: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The Menu: the farm's pages in the groups the farm gives, then the phone's own. While the farm's list is not here
 * the wait says why, and the farm's own page still opens: every page stays one tap away. It only draws.
 */
@Composable
fun MenuScreen(groups: List<MenuGroup>, loading: Boolean, failure: String?, offline: String?, actions: MenuActions) {
    Scaffold(
        topBar = {
            ScreenBar {
                Text(stringResource(R.string.nav_menu), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
        },
        bottomBar = { MainNav(MainTab.MENU, onNow = actions.onNow, onMenu = {}) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp).testTag("menu-list")) {
            if (offline != null) item("offline") { StaleBanner(offline) }
            if (failure != null) {
                item("failure") { ErrorBanner(failure, actions.onRetry) }
                item("farm-pages") { MenuRow("menu-farm-pages", stringResource(R.string.menu_farm_pages)) { actions.onPage(PageRoute.HOME_PAGE) } }
            }
            if (loading) item("loading") { SkeletonRows(6) }
            for (g in groups) {
                item("g-${g.id}") { SectionTitle(groupTitle(g.id)) }
                items(g.pages, key = { "p-${it.id}" }) { p -> MenuRow("menu-${p.id}", p.label) { actions.onPage(p.id) } }
            }
            item("g-phone") { SectionTitle(stringResource(R.string.menu_phone)) }
            item("farms") { MenuRow("menu-farms", stringResource(R.string.farms), actions.onFarms) }
            item("phone-settings") { MenuRow("menu-phone-settings", stringResource(R.string.menu_phone_settings), actions.onSettings) }
        }
    }
}

/** A group this build does not know is shown as the farm named it. */
@Composable
private fun groupTitle(id: String): String = when (id) {
    "work" -> stringResource(R.string.menu_work)
    "info" -> stringResource(R.string.menu_info)
    "settings" -> stringResource(R.string.settings_title)
    "more" -> stringResource(R.string.menu_more)
    else -> id
}

@Composable
private fun MenuRow(tag: String, label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
