package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A section title of a pick-and-act screen (share, stop). */
@Composable
fun ChoiceSection(title: String) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

/** Exactly one of `rows` (id to label): radio rows tagged `<tag>-<id>`, the chosen one selected. */
@Composable
fun Choices(rows: List<Pair<String, String>>, chosen: String?, tag: String, onChoose: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        rows.forEach { (id, label) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = id == chosen, role = Role.RadioButton, onClick = { onChoose(id) })
                    .testTag("$tag-$id"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = id == chosen, onClick = null)
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
    }
}
