package com.muvusoft.agentfarm.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.share.SharedImage
import com.muvusoft.agentfarm.net.SharedImages
import com.muvusoft.agentfarm.ui.components.AFTip

/** What is about to be sent: the words (cut, the whole text in its sheet) and the picture with its name and size. */
@Composable
fun SharePreview(shared: Shared) {
    if (shared.text.isNotEmpty()) TextPreview(shared.text)
    shared.image?.let { ImagePreview(it) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextPreview(text: String) {
    var whole by remember { mutableStateOf(false) }
    if (whole) AFTip(listOf(text), onDismiss = { whole = false })
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            Share.preview(text),
            Modifier.combinedClickable(
                onClickLabel = stringResource(R.string.share_whole),
                onClick = { whole = true },
                onLongClick = { whole = true },
            ).padding(12.dp).testTag("share-preview"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ImagePreview(image: SharedImage) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, image.uri) { value = SharedImages.thumbnail(context, image) }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("share-image")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            thumb?.let {
                Image(it.asImageBitmap(), stringResource(R.string.share_image), Modifier.size(72.dp), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(start = if (thumb != null) 12.dp else 0.dp)) {
                Text(image.name, style = MaterialTheme.typography.bodyMedium)
                image.bytes?.let { Text(Share.size(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
