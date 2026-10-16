package com.muvusoft.agentfarm.core.menu

import com.muvusoft.agentfarm.core.contract.MenuPage
import com.muvusoft.agentfarm.core.contract.PagesList

/** One of the Menu's groups as the farm named it (work, info, settings, more, or one this build does not know). */
data class MenuGroup(val id: String, val pages: List<MenuPage>)

/** The phone's Menu out of the farm's pages.list: the farm owns the order, of the groups and of the pages in each. */
object MenuBuild {
    const val OP = "pages.list"

    fun groups(list: PagesList?): List<MenuGroup> =
        list?.pages.orEmpty().groupBy { it.group }.map { (id, pages) -> MenuGroup(id, pages) }
}
