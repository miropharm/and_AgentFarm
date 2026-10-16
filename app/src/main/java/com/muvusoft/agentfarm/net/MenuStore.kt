package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.PagesList
import java.io.File
import java.security.MessageDigest

/** Each farm's last Menu (pages.list) in the app's private files: the Menu opens at once, and offline too. */
class MenuStore(context: Context) {
    private val dir = File(context.filesDir, "menu")

    fun load(farmId: String): PagesList? = file(farmId).takeIf { it.isFile }?.let { f ->
        runCatching { Codec.json.decodeFromString(PagesList.serializer(), f.readText()) }.getOrNull()
    }

    fun save(farmId: String, list: PagesList) {
        dir.mkdirs()
        val f = file(farmId)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(Codec.json.encodeToString(PagesList.serializer(), list))
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
