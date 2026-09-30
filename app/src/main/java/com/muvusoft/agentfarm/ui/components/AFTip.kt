package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The shared detail sheet: the whole text of one subject, scrollable and selectable. The first line is
 * the long form of what the row showed short. A tap outside or Back closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AFTip(lines: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("aftip")) {
        SelectionContainer {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                lines.forEachIndexed { i, line ->
                    Text(
                        line,
                        Modifier.padding(top = if (i == 0) 0.dp else 8.dp),
                        style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
