package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R

/** A section's title with its count: "Running 3". */
@Composable
fun SectionTitle(title: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** The state dot: running, waiting, offline. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Rows still on their way: grey bars where the words will be, never a blank screen. */
@Composable
fun SkeletonRows(rows: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    val bar = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.testTag("skeleton").semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) { i ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.55f else 0.4f).height(14.dp).background(bar, RoundedCornerShape(4.dp)))
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.85f else 0.7f).height(12.dp).background(bar, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

/** The copy on screen is not live: how old it is, and that it refreshes by itself. */
@Composable
fun StaleBanner(text: String) {
    Surface(
        Modifier.fillMaxWidth().testTag("stale"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dot(MaterialTheme.colorScheme.outline)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The fresh answer failed: the farm's own reason and a way to try again. */
@Composable
fun ErrorBanner(reason: String, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("error"), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                reason,
                Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry, Modifier.testTag("retry")) { Text(stringResource(R.string.retry)) }
        }
    }
}
