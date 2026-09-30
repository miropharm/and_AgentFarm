package com.muvusoft.agentfarm.ui.page

import android.content.ActivityNotFoundException
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The page's chrome client: a file input opens the system picker. The page's callback is answered
 * exactly once, with null on cancel, or WebView never opens a picker for that page again.
 */
@Composable
fun rememberPageChrome(): WebChromeClient {
    val waiting = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        waiting[0]?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        waiting[0] = null
    }
    return remember {
        object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                waiting[0]?.onReceiveValue(null)
                waiting[0] = callback
                return try {
                    picker.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    waiting[0] = null
                    false
                }
            }
        }
    }
}
