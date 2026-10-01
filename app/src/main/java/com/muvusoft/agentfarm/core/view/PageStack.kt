package com.muvusoft.agentfarm.core.view

/**
 * The farm pages opened from one another in a shell, oldest first: a page link adds one, Back
 * returns to the one before, and Back on the first page leaves the farm. An entry is whatever
 * names a page view — the shell keeps a page with its arguments, so two transcripts are two entries.
 */
object PageStack {
    /** How many pages Back can walk through; older ones are forgotten. */
    const val MAX = 20

    /** The stack after opening [page]; opening the page already shown changes nothing. */
    fun <T> open(stack: List<T>, page: T): List<T> =
        if (stack.lastOrNull() == page) stack else (stack + page).takeLast(MAX)

    /** The stack after Back, or null when Back leaves the farm. */
    fun <T> back(stack: List<T>): List<T>? = if (stack.size <= 1) null else stack.dropLast(1)
}
