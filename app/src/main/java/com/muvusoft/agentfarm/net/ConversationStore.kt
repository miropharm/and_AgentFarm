package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.conversation.ConversationCopy
import java.io.File
import java.security.MessageDigest

/**
 * Each farm's last opened Conversations, a copy each, in the app's private files: the one opened longest ago goes
 * past [KEEP], and all of them go with the farm when it is forgotten.
 */
class ConversationStore(context: Context) {
    private val root = File(context.filesDir, "conversations")

    fun load(farmId: String, key: String): ConversationCopy? =
        file(farmId, key).takeIf { it.isFile }?.let { ConversationCopy.decode(it.readText()) }

    fun save(farmId: String, key: String, copy: ConversationCopy) {
        val dir = dir(farmId).apply { mkdirs() }
        val f = file(farmId, key)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(copy.encode())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        dir.listFiles { x -> x.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
    }

    fun forget(farmId: String) {
        dir(farmId).deleteRecursively()
    }

    private fun dir(farmId: String) = File(root, hash(farmId))

    /** A farm id and a session id are their owners' strings: files are named by their hashes, never by the strings. */
    private fun file(farmId: String, key: String) = File(dir(farmId), hash(key) + ".json")

    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val KEEP = 10
    }
}
