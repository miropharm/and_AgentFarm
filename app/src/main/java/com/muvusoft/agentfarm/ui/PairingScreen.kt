package com.muvusoft.agentfarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.muvusoft.agentfarm.ui.components.AFTip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.state.PairedFarm

/** What the pairing screen shows; ShellApp owns it. */
data class PairingUi(
    val link: String = "",
    val busy: Boolean = false,
    /** The last outcome in the user's words: a problem's message or the farm that was paired. */
    val message: String? = null,
    val failed: Boolean = false,
)

@Composable
fun PairingScreen(
    versionName: String,
    farms: List<PairedFarm>,
    /** Each farm's link in the status strip's words, by farm id. */
    links: Map<String, LinkText.Text>,
    /** Each farm's link detail sheet (long-press on its row), by farm id. */
    tips: Map<String, List<String>>,
    ui: PairingUi,
    onLinkChange: (String) -> Unit,
    onPair: () -> Unit,
    onForget: (PairedFarm) -> Unit,
    onOpen: (PairedFarm) -> Unit,
    onSettings: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { ShellHeader(versionName) }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) { Icon(Icons.Filled.Settings, "Ayarlar") }
            }
            Text("Çiftlikler", style = MaterialTheme.typography.titleMedium)
            if (farms.isEmpty()) {
                Text("Henüz eşlenmiş çiftlik yok.", Modifier.testTag("farms-empty"), style = MaterialTheme.typography.bodyMedium)
            }
            farms.forEach { FarmRow(it, links[it.id], tips[it.id].orEmpty(), onForget = onForget, onOpen = onOpen) }
            Spacer(Modifier.height(16.dp))
            Text("Yeni eşleşme", style = MaterialTheme.typography.titleMedium)
            Text(
                "Agent Farm'daki eşleşme QR'ını kamerayla okutun ya da bağlantıyı yapıştırın.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = ui.link,
                onValueChange = onLinkChange,
                modifier = Modifier.fillMaxWidth().testTag("pair-link"),
                label = { Text("Eşleşme bağlantısı") },
                singleLine = true,
                enabled = !ui.busy,
                trailingIcon = {
                    if (ui.link.isNotEmpty()) {
                        IconButton(onClick = { onLinkChange("") }) { Icon(Icons.Filled.Clear, "Temizle") }
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = onPair, enabled = !ui.busy && ui.link.isNotBlank(), modifier = Modifier.testTag("pair-go")) {
                    Text(if (ui.busy) "Eşleşiyor…" else "Eşleş")
                }
            }
            ui.message?.let {
                Text(
                    it,
                    Modifier.padding(top = 8.dp).testTag("pair-message"),
                    color = if (ui.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FarmRow(
    farm: PairedFarm,
    link: LinkText.Text?,
    tip: List<String>,
    onForget: (PairedFarm) -> Unit,
    onOpen: (PairedFarm) -> Unit,
) {
    var showTip by remember { mutableStateOf(false) }
    if (showTip && tip.isNotEmpty()) AFTip(tip, onDismiss = { showTip = false })
    Card(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("farm-${farm.id}")) {
        Row(
            Modifier.combinedClickable(onClick = { onOpen(farm) }, onLongClick = { showTip = true }, onLongClickLabel = "Bağlantı ayrıntısı")
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(farm.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    link?.short ?: farm.addresses.firstOrNull().orEmpty(),
                    Modifier.testTag("link-${farm.id}"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { onForget(farm) }, modifier = Modifier.testTag("forget-${farm.id}")) { Text("Unut") }
        }
    }
}
