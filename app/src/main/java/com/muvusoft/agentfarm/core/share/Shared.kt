package com.muvusoft.agentfarm.core.share

/** What another app handed over: words, a picture, or both. Null from `of` when there is neither. */
data class Shared(val text: String, val image: SharedImage? = null) {
    companion object {
        fun of(subject: String?, body: String?, image: SharedImage?): Shared? {
            val text = Share.text(subject, body).orEmpty()
            return if (text.isEmpty() && image == null) null else Shared(text, image)
        }
    }
}

/** A shared picture: its content uri (read when it is sent), the name and size the sending app gave, its type. */
data class SharedImage(val uri: String, val name: String, val mime: String, val bytes: Long?) {
    /** The farm takes a picture of up to `Share.IMAGE_MAX_BYTES`; an unknown size is tried and the farm decides. */
    val tooBig: Boolean get() = bytes != null && bytes > Share.IMAGE_MAX_BYTES
}
