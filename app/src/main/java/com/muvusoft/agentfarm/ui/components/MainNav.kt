package com.muvusoft.agentfarm.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.muvusoft.agentfarm.R

enum class MainTab { NOW, MENU }

/** The app's two places: Now, where the work is, and the Menu, where every other page is. */
@Composable
fun MainNav(tab: MainTab, onNow: () -> Unit, onMenu: () -> Unit) {
    NavigationBar(Modifier.testTag("main-nav")) {
        NavigationBarItem(
            selected = tab == MainTab.NOW,
            onClick = onNow,
            icon = { Icon(Icons.Filled.Home, null) },
            label = { Text(stringResource(R.string.nav_now)) },
        )
        NavigationBarItem(
            selected = tab == MainTab.MENU,
            onClick = onMenu,
            icon = { Icon(Icons.Filled.Menu, null) },
            label = { Text(stringResource(R.string.nav_menu)) },
        )
    }
}
