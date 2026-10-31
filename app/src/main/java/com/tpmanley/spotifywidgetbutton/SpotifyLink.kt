package com.tpmanley.spotifywidgetbutton

/** Turns pasted Spotify links/URIs into canonical `spotify:<type>:<id>` URIs. */
object SpotifyLink {
    private val SUPPORTED_TYPES = setOf("audiobook", "show", "episode", "album", "playlist", "track", "chapter", "artist")
    private val ID = Regex("[A-Za-z0-9]{22}")
    private val URI_RE = Regex("""spotify:([a-z]+):([A-Za-z0-9]+)""")
    // Handles optional locale segment, e.g. open.spotify.com/intl-de/audiobook/<id>
    private val URL_RE = Regex("""https?://open\.spotify\.com/(?:intl-[a-zA-Z-]+/)?([a-z]+)/([A-Za-z0-9]+)""")

    /** Returns `spotify:<type>:<id>`, or null if [input] has no recognizable Spotify link. */
    fun toUri(input: String): String? {
        val text = input.trim()
        val match = URI_RE.find(text) ?: URL_RE.find(text) ?: return null
        val (type, id) = match.destructured
        if (type !in SUPPORTED_TYPES || !ID.matches(id)) return null
        return "spotify:$type:$id"
    }

    // Type and id are the last two segments, which also covers longer forms Spotify reports
    // for contexts, e.g. spotify:user:<owner>:playlist:<id> or spotify:station:artist:<id>.
    private val TAIL_RE = Regex("""spotify:(?:[^:]+:)*([a-z]+):([A-Za-z0-9]{22})""")
    private val SINGLE_TYPES = setOf("track", "episode", "chapter")
    // The player may report an audiobook as a show with the same id.
    private val BOOK_TYPES = setOf("audiobook", "show")

    /** The 22-char id at the end of a Spotify URI. */
    fun idOf(uri: String): String? = TAIL_RE.matchEntire(uri)?.groupValues?.get(2)

    /** The item type (track, album, playlist, …) of a Spotify URI. */
    fun typeOf(uri: String): String? = TAIL_RE.matchEntire(uri)?.groupValues?.get(1)

    /** Tracks, episodes and chapters play as the current track; everything else is a context. */
    fun isSingleItem(uri: String): Boolean = typeOf(uri) in SINGLE_TYPES

    /** Whether two URIs name the same item: same id and same type (audiobook ≡ show). */
    fun sameItem(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val ta = TAIL_RE.matchEntire(a) ?: return false
        val tb = TAIL_RE.matchEntire(b) ?: return false
        val (typeA, idA) = ta.destructured
        val (typeB, idB) = tb.destructured
        return idA == idB && (typeA == typeB || (typeA in BOOK_TYPES && typeB in BOOK_TYPES))
    }

    /**
     * Spotify's player sometimes knows audiobooks as shows (same id), so
     * `spotify:audiobook:<id>` has a `spotify:show:<id>` twin worth trying.
     */
    fun alternateUri(uri: String): String? {
        val match = URI_RE.matchEntire(uri) ?: return null
        val (type, id) = match.destructured
        return when (type) {
            "audiobook" -> "spotify:show:$id"
            "show" -> "spotify:audiobook:$id"
            else -> null
        }
    }

    /** `spotify:audiobook:<id>` -> `https://open.spotify.com/audiobook/<id>` */
    fun toWebUrl(uri: String): String? {
        val match = URI_RE.matchEntire(uri) ?: return null
        val (type, id) = match.destructured
        return "https://open.spotify.com/$type/$id"
    }
}
