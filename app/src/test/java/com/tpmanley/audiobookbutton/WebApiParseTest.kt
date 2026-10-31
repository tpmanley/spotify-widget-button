package com.tpmanley.audiobookbutton

import org.junit.Assert.assertEquals
import org.junit.Test

class WebApiParseTest {
    @Test fun playingOnSpeaker() = assertEquals(
        PlaybackCheck.Playing("Kitchen", "Speaker", "Chapter 3"),
        WebApi.parse("""{"is_playing":true,"device":{"name":"Kitchen","type":"Speaker"},"item":{"name":"Chapter 3"}}"""),
    )

    @Test fun pausedCountsAsNothing() = assertEquals(
        PlaybackCheck.NothingPlaying,
        WebApi.parse("""{"is_playing":false,"device":{"name":"Kitchen","type":"Speaker"}}"""),
    )

    @Test fun noItemStillReportsDevice() = assertEquals(
        PlaybackCheck.Playing("Living Room TV", "TV", null),
        WebApi.parse("""{"is_playing":true,"device":{"name":"Living Room TV","type":"TV"},"item":null}"""),
    )

    @Test fun emptyBody() = assertEquals(PlaybackCheck.NothingPlaying, WebApi.parse(""))
}
