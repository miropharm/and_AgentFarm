package com.muvusoft.agentfarm.core.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRouteTest {
    @Test
    fun aPageUrlRoutesToTheHostsViewPath() {
        val url = PageRoute.pageUrl("now", "v1")
        assertEquals("https://farm.agentfarm/view/now?view=v1", url)
        assertEquals("/view/now?view=v1", PageRoute.hostPath(url))
    }

    @Test
    fun resourcesRouteToRes() {
        assertEquals("/res/media/needs.js", PageRoute.hostPath("https://farm.agentfarm/res/media/needs.js"))
    }

    @Test
    fun nothingElseLeavesThePhone() {
        listOf(
            "https://example.com/res/a.js",
            "http://farm.agentfarm/res/a.js",
            "https://farm.agentfarm:444/res/a.js",
            "https://farm.agentfarm/res/../secret",
            "https://farm.agentfarm/res/%2e%2e/secret",
            "https://farm.agentfarm/other",
            "https://farm.agentfarm/view/a/b",
            "https://farm.agentfarm/res/",
            "not a url",
        ).forEach { assertNull(it, PageRoute.hostPath(it)) }
    }

    @Test
    fun aPageIdIsOneSafeName() {
        listOf("now", "needs", "a2a.notes", "x_1-2").forEach { assertEquals(it, true, PageRoute.isPage(it)) }
        listOf("", "a/b", "../x", "a?b", "a b", "ş").forEach { assertEquals(it, false, PageRoute.isPage(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPageIdCannotCarryAPath() {
        PageRoute.pageUrl("../x", "v1")
    }

    @Test
    fun hostMessagesArriveAsAMessageEvent() {
        assertEquals(
            "window.dispatchEvent(new MessageEvent('message',{data:{\"a\":1}}));",
            PageRoute.deliverScript("{\"a\":1}"),
        )
    }
}
