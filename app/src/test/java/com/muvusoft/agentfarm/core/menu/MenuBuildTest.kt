package com.muvusoft.agentfarm.core.menu

import com.muvusoft.agentfarm.core.contract.MenuPage
import com.muvusoft.agentfarm.core.contract.PagesList
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuBuildTest {
    @Test
    fun theFarmsOrderIsTheMenusAndAGroupThisBuildDoesNotKnowIsItsOwn() {
        val list = PagesList(
            listOf(
                MenuPage("tasks", "Tasks", "work"),
                MenuPage("notices", "Notices", "info"),
                MenuPage("chains", "Chains", "work"),
                MenuPage("later", "Later", "tomorrow"),
            ),
        )
        val groups = MenuBuild.groups(list)
        assertEquals(listOf("work", "info", "tomorrow"), groups.map { it.id })
        assertEquals("a group's pages keep the farm's order", listOf("tasks", "chains"), groups[0].pages.map { it.id })
        assertEquals("no list yet is no group", emptyList<MenuGroup>(), MenuBuild.groups(null))
    }
}
