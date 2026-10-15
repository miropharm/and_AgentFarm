package com.muvusoft.agentfarm.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.muvusoft.agentfarm.core.share.SharedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture another app shared, read through its content uri: what it is called, a thumbnail, its bytes. */
object SharedImages {
    /** The name and size the sending app gives; a uri it cannot describe still goes, under a plain name. */
    fun describe(context: Context, uri: Uri, mime: String): SharedImage {
        var name: String? = null
        var bytes: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
                    bytes = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                }
            }
        }
        val type = mime.substringAfter('/', "png").substringBefore(';').ifBlank { "png" }
        return SharedImage(uri.toString(), name?.takeIf { it.isNotBlank() } ?: "shared.$type", mime, bytes)
    }

    /** The picture's bytes as base64 for console.stage; null when the uri cannot be read any more. */
    suspend fun base64(context: Context, image: SharedImage): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(image.uri))?.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        }.getOrNull()
    }

    /** A small copy for the preview, decoded at a fraction of its size; null when it cannot be drawn. */
    suspend fun thumbnail(context: Context, image: SharedImage, edge: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(image.uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= edge && bounds.outHeight / (sample * 2) >= edge) sample *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }.getOrNull()
    }
}
