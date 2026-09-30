package com.muvusoft.agentfarm.core.contract

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Every example in the contract copy is decoded by the Kotlin models and survives a round trip. The kinds
// and types come from the JSON, so a frame added to the contract without a model fails here.
class ContractExamplesTest {
    private val contract: JsonObject by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "contract/remote-contract.v1.json") }
            .first { it.isFile }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun section(name: String) = contract.getValue(name).jsonObject

    @Test
    fun versionMatchesTheCode() {
        assertEquals(CONTRACT_NAME, contract.getValue("contract").jsonPrimitive.content)
        assertEquals(CONTRACT_VERSION, contract.getValue("version").jsonPrimitive.long)
    }

    @Test
    fun everyFrameExampleDecodesAndRoundTrips() {
        for ((kind, spec) in section("frames")) {
            val example = spec.jsonObject.getValue("example").toString()
            val frame = Codec.decode(example)
            assertNotNull("frame $kind has no Kotlin model", frame)
            val again = Codec.decode(Codec.encode(frame!!))
            assertEquals("frame $kind round trip", frame, again)
            assertEquals("frame $kind re-encodes its kind", kind, Json.parseToJsonElement(Codec.encode(frame)).jsonObject.getValue("kind").jsonPrimitive.content)
        }
    }

    @Test
    fun everyEventTypeHasAModelThatReadsItsExample() {
        val types = section("events").keys
        assertEquals("EVENT_TYPES covers exactly the contract's events", types, EVENT_TYPES.keys)
        for ((type, spec) in section("events")) {
            val data = spec.jsonObject.getValue("example").jsonObject
            val parsed = Codec.eventData(EventFrame(seq = 1, type = type, ts = 1, data = data))
            assertNotNull("event $type does not decode", parsed)
        }
    }

    @Test
    fun unknownKindAndTypeAreIgnoredNotFatal() {
        assertNull(Codec.decode("""{"kind":"teleport","to":"mars"}"""))
        assertNull(Codec.decode("not json"))
        assertNull(Codec.decode("""{"kind":"ping"}"""))
        assertNull(Codec.eventData(EventFrame(1, "weather.changed", 1, JsonObject(emptyMap()))))
    }

    @Test
    fun extraFieldsFromANewerPeerAreAccepted() {
        assertEquals(Ping(5), Codec.decode("""{"kind":"ping","ts":5,"newField":true}"""))
    }

    @Test
    fun pairingRequestAndResponseExamplesDecode() {
        val pairing = section("pairing")
        val req = pairing.getValue("request").jsonObject.getValue("example")
        val res = pairing.getValue("response").jsonObject.getValue("example")
        assertEquals("Pixel 8", Codec.json.decodeFromJsonElement(PairRequest.serializer(), req).name)
        assertEquals("manage", Codec.json.decodeFromJsonElement(PairResponse.serializer(), res).scope)
    }

    @Test
    fun signedTextFollowsTheTransportRule() {
        val rule = section("transport").getValue("signature").jsonPrimitive.content
        assertTrue(rule.contains("agentfarm.remote/$CONTRACT_VERSION|"))
        assertEquals("agentfarm.remote/1|farm_7f3a|dev_k3m9x2|n1", Codec.signedText("farm_7f3a", "dev_k3m9x2", "n1"))
    }
}
