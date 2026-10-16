package com.muvusoft.agentfarm.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager

/** A farm that is not online: the wait (kind · reason · since), and a wake for its PC when it sleeps and its card is known. */
@Composable
fun WaitScreen(farmId: String, farmName: String, link: Link?, manager: ConnectionManager) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val text = link?.let { LinkText.of(it, System.currentTimeMillis()).long } ?: stringResource(R.string.farm_unpaired)
        Text(text, Modifier.testTag("page-wait"), style = MaterialTheme.typography.bodyLarge)
        WakeOffer(farmId, farmName, link, manager, overCopy = false)
    }
}
