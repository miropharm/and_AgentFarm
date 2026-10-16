package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.now.NowCopy
import java.io.File
import java.security.MessageDigest

/** Each farm's Now copy in the app's private files; it goes with the farm when the farm is forgotten. */
class NowStore(context: Context) {
    private val dir = File(context.filesDir, "now")

    fun load(farmId: String): NowCopy? = file(farmId).takeIf { it.isFile }?.let { NowCopy.decode(it.readText()) }

    fun save(farmId: String, copy: NowCopy) {
        dir.mkdirs()
        val f = file(farmId)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(copy.encode())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    fun forget(farmId: String) {
        file(farmId).delete()
    }

    /** A farm id is the farm's own string: its file is named by its hash, never by the string itself. */
    private fun file(farmId: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(farmId.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }
}
