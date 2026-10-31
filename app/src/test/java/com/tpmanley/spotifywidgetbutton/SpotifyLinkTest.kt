package com.tpmanley.spotifywidgetbutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLinkTest {
    private val id = "7iHfbu1YPACw6oZPAFJtqe"

    @Test fun webUrlWithShareParam() =
        assertEquals("spotify:audiobook:$id", SpotifyLink.toUri("https://open.spotify.com/audiobook/$id?si=abc123"))

    @Test fun webUrlWithoutParams() =
        assertEquals("spotify:audiobook:$id", SpotifyLink.toUri("https://open.spotify.com/audiobook/$id"))

    @Test fun localizedWebUrl() =
        assertEquals("spotify:audiobook:$id", SpotifyLink.toUri("https://open.spotify.com/intl-de/audiobook/$id"))

    @Test fun shareTextWithSurroundingWords() =
        assertEquals("spotify:audiobook:$id", SpotifyLink.toUri("Listen to this book https://open.spotify.com/audiobook/$id?si=x on Spotify"))

    @Test fun spotifyUri() =
        assertEquals("spotify:audiobook:$id", SpotifyLink.toUri("  spotify:audiobook:$id "))

    @Test fun showUrl() =
        assertEquals("spotify:show:$id", SpotifyLink.toUri("https://open.spotify.com/show/$id"))

    @Test fun garbage() = assertNull(SpotifyLink.toUri("hello world"))

    @Test fun wrongIdLength() = assertNull(SpotifyLink.toUri("spotify:audiobook:abc"))

    @Test fun unsupportedType() = assertNull(SpotifyLink.toUri("https://open.spotify.com/user/$id"))

    @Test fun shortenedLinkNotSupported() = assertNull(SpotifyLink.toUri("https://spotify.link/AbCdEf"))

    @Test fun webUrlRoundTrip() =
        assertEquals("https://open.spotify.com/audiobook/$id", SpotifyLink.toWebUrl("spotify:audiobook:$id"))

    @Test fun alternateOfAudiobookIsShow() =
        assertEquals("spotify:show:$id", SpotifyLink.alternateUri("spotify:audiobook:$id"))

    @Test fun noAlternateForAlbum() = assertNull(SpotifyLink.alternateUri("spotify:album:$id"))

    @Test fun idOfUri() = assertEquals(id, SpotifyLink.idOf("spotify:audiobook:$id"))

    @Test fun playlistUrl() =
        assertEquals("spotify:playlist:$id", SpotifyLink.toUri("https://open.spotify.com/playlist/$id?si=abc"))

    @Test fun albumUrl() =
        assertEquals("spotify:album:$id", SpotifyLink.toUri("https://open.spotify.com/album/$id"))

    @Test fun artistUrl() =
        assertEquals("spotify:artist:$id", SpotifyLink.toUri("https://open.spotify.com/artist/$id"))

    @Test fun trackUri() = assertEquals("spotify:track:$id", SpotifyLink.toUri("spotify:track:$id"))
}
