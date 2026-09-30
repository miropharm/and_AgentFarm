package com.muvusoft.agentfarm.core.view

/**
 * The farm pages opened from one another in a shell, oldest first: a page link adds one, Back
 * returns to the one before, and Back on the first page leaves the farm.
 */
object PageStack {
    /** How many pages Back can walk through; older ones are forgotten. */
    const val MAX = 20

    /** The stack after opening [page]; opening the page already shown changes nothing. */
    fun open(stack: List<String>, page: String): List<String> =
        if (stack.lastOrNull() == page) stack else (stack + page).takeLast(MAX)

    /** The stack after Back, or null when Back leaves the farm. */
    fun back(stack: List<String>): List<String>? = if (stack.size <= 1) null else stack.dropLast(1)
}
