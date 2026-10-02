package com.muvusoft.agentfarm.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.muvusoft.agentfarm.R

/**
 * The pairing QR, read in the app: not every camera app opens an agentfarm:// link (some only show
 * its text). Google's code scanner draws its own camera screen, so the app asks for no camera
 * permission. A phone without Google Play services gets a sentence pointing to the paste field.
 */
object QrScan {
    /** [onText] gets the code's text; [onFail] a problem in the user's words; a cancelled scan calls neither. */
    fun start(context: Context, onText: (String) -> Unit, onFail: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val text = code.rawValue?.trim().orEmpty()
                if (text.isNotEmpty()) onText(text) else onFail(context.getString(R.string.scan_empty))
            }
            .addOnFailureListener { onFail(context.getString(R.string.scan_unavailable)) }
    }
}
