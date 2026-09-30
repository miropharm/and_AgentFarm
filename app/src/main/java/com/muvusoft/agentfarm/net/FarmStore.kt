package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.state.PairedFarm
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The paired farms, kept in the app's private files. A farm is written once pairing succeeded, never before. */
class FarmStore(context: Context) {
    private val file = File(context.filesDir, "farms.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PairedFarm.serializer())

    fun load(): List<PairedFarm> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /** Adds the farm, or replaces the record of one with the same id (its superseded key is deleted). */
    fun save(farm: PairedFarm): List<PairedFarm> {
        val old = load()
        val farms = old.filterNot { it.id == farm.id } + farm
        write(farms)
        old.filter { it.id == farm.id && it.key != farm.key }.forEach { DeviceKeys.delete(it.key) }
        return farms
    }

    fun forget(farmId: String): List<PairedFarm> {
        val old = load()
        write(old.filterNot { it.id == farmId })
        old.filter { it.id == farmId }.forEach { DeviceKeys.delete(it.key) }
        return load()
    }

    private fun write(farms: List<PairedFarm>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, farms))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
