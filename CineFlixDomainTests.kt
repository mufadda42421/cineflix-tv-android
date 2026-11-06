package com.cineflix.tv

import com.cineflix.tv.data.model.CineFlixServers
import com.cineflix.tv.data.model.MediaItem
import com.cineflix.tv.data.model.VideoDto
import com.cineflix.tv.data.model.VideosResponse
import com.cineflix.tv.data.remote.TmdbApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineFlixDomainTests {

    @Test
    fun `credential cleaner strips quotes and Bearer prefix`() {
        assertEquals("abc123def456", TmdbApiClient.cleanCredential("  \"Bearer abc123def456\"  "))
        assertTrue(TmdbApiClient.isApiKeyV3("a1b2c3d4e5f60718293a4b5c6d7e8f90"))
        assertFalse(TmdbApiClient.isApiKeyV3("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIxMjM0NTYifQ.signature"))
    }

    @Test
    fun `media item resolves movie and tv metadata accurately`() {
        val movie = MediaItem(
            id = 27205,
            title = "Inception",
            releaseDate = "2010-07-16",
            voteAverage = 8.4,
            runtime = 148,
            genreIds = listOf(28, 878)
        )
        assertEquals("Inception", movie.resolvedTitle)
        assertEquals("2010", movie.resolvedYear)
        assertEquals("movie", movie.resolvedMediaType())
        assertEquals("2h 28m", movie.formattedRuntimeOrSeasons())

        val series = MediaItem(
            id = 1396,
            name = "Breaking Bad",
            firstAirDate = "2008-01-20",
            voteAverage = 8.9,
            numberOfSeasons = 5,
            numberOfEpisodes = 62
        )
        assertEquals("Breaking Bad", series.resolvedTitle)
        assertEquals("2008", series.resolvedYear)
        assertEquals("tv", series.resolvedMediaType())
        assertEquals("5 Seasons · 62 Episodes", series.formattedRuntimeOrSeasons())
    }

    @Test
    fun `streaming servers generate exact CineFlix movie and tv URLs`() {
        val vidsrcPm = CineFlixServers.findById("vidsrc_pm")
        assertEquals(
            "https://vidsrc.pm/embed/movie/550",
            vidsrcPm.urlBuilder(550, "movie", 1, 1, "", null)
        )
        assertEquals(
            "https://vidsrc.pm/embed/tv/1399/2/5",
            vidsrcPm.urlBuilder(1399, "tv", 2, 5, "", null)
        )

        val youtube = CineFlixServers.findById("tmdb_youtube")
        assertEquals(
            "https://www.youtube-nocookie.com/embed/YoHD9XEInc0?autoplay=1&rel=0&modestbranding=1&playsinline=1",
            youtube.urlBuilder(27205, "movie", 1, 1, "", "YoHD9XEInc0")
        )
    }

    @Test
    fun `video selector prioritizes official YouTube trailers first`() {
        val resp = VideosResponse(
            id = 1,
            results = listOf(
                VideoDto(id = "1", key = "clip1", site = "YouTube", type = "Clip", official = false, size = 720),
                VideoDto(id = "2", key = "trl1", site = "YouTube", type = "Trailer", official = true, size = 1080),
                VideoDto(id = "3", key = "tsr1", site = "YouTube", type = "Teaser", official = true, size = 1080)
            )
        )
        val ordered = resp.selectBestTrailers()
        assertEquals("trl1", ordered.first().key)
    }
}
