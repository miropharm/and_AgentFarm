package com.muvusoft.agentfarm.core.share

import com.muvusoft.agentfarm.core.contract.Codec
import org.junit.Assert.assertEquals
import org.junit.Test

class StopTest {
    private fun json(s: String) = Codec.json.parseToJsonElement(s)

    @Test
    fun onlyARunningTurnCanBeStopped() {
        val rows = json(
            """[{"id":"c1","agent":"developer","status":"working"},{"id":"c0","agent":"ops","status":"running"},{"id":"c2","agent":"yazar","status":"idle"},
            {"id":"c3","agent":"psikolog","status":"working","asking":{"q":1}},{"id":"c4","agent":"x","status":"ended"}]""",
        )
        assertEquals(listOf("c3", "c1"), Stop.running(rows).map { it.id })
        assertEquals(emptyList<ShareTarget>(), Stop.running(json("""{"not":"a list"}""")))
    }

    @Test
    fun theStopSaysHowTheFarmStoppedIt() {
        assertEquals(
            "Stopped: interrupted over the control channel — the process stays.",
            Stop.outcome(true, json("""{"interrupted":true,"how":"interrupted over the control channel — the process stays"}"""), null),
        )
        assertEquals("Stopped.", Stop.outcome(true, json("""{"interrupted":true}"""), null))
        assertEquals("Not stopped: no such live console session", Stop.outcome(false, null, "no such live console session"))
        assertEquals("Not stopped: the farm did not answer", Stop.outcome(false, null, null))
    }
}
