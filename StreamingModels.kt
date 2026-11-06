package com.cineflix.tv.data.model

import kotlinx.serialization.Serializable

enum class StreamServerKind {
    EXTERNAL_EMBED,
    DIRECT_MEDIA,
    TMDB_YOUTUBE
}

enum class PlaybackEngineMode {
    EXOPLAYER_NATIVE,
    HYBRID_WEBVIEW
}

data class StreamServer(
    val id: String,
    val name: String,
    val shortLabel: String,
    val badge: String,
    val speedTier: String,
    val kind: StreamServerKind,
    val description: String,
    val urlBuilder: (tmdbId: Int, mediaType: String, season: Int, episode: Int, customUrl: String, youtubeKey: String?) -> String
)

object CineFlixServers {
    val all: List<StreamServer> = listOf(
        StreamServer(
            id = "vidsrc_pm",
            name = "Server 4 · VidSrc Master Node",
            shortLabel = "Server 4 · Master",
            badge = "Best Experience",
            speedTier = "ultra_fast",
            kind = StreamServerKind.EXTERNAL_EMBED,
            description = "Primary CineFlix Master Node with high-bitrate 1080p streams and automatic failover",
            urlBuilder = { id, type, s, e, _, _ ->
                if (type == "tv") "https://vidsrc.pm/embed/tv/$id/$s/$e"
                else "https://vidsrc.pm/embed/movie/$id"
            }
        ),
        StreamServer(
            id = "vidsrc_to",
            name = "Server 1 · VidSrc TO",
            shortLabel = "Server 1 · VidSrc TO",
            badge = "1080p · Instant Start",
            speedTier = "ultra_fast",
            kind = StreamServerKind.EXTERNAL_EMBED,
            description = "High-speed global cluster with instant buffering, 1080p HD, and auto-subtitles",
            urlBuilder = { id, type, s, e, _, _ ->
                if (type == "tv") "https://vidsrc.to/embed/tv/$id/$s/$e"
                else "https://vidsrc.to/embed/movie/$id"
            }
        ),
        StreamServer(
            id = "vidsrc_sh",
            name = "Server 2 · VidSrc Cloud",
            shortLabel = "Server 2 · Cloud",
            badge = "1080p · High Speed",
            speedTier = "ultra_fast",
            kind = StreamServerKind.EXTERNAL_EMBED,
            description = "Verified direct TMDB node with automatic source failover and multi-channel audio",
            urlBuilder = { id, type, s, e, _, _ ->
                if (type == "tv") "https://vidsrc.sh/embed/tv?tmdb=$id&season=$s&episode=$e"
                else "https://vidsrc.sh/embed/movie?tmdb=$id"
            }
        ),
        StreamServer(
            id = "smashystream_xyz",
            name = "Server 3 · SmashyStream Cloud",
            shortLabel = "Server 3 · Smashy",
            badge = "Clean Player",
            speedTier = "fast",
            kind = StreamServerKind.EXTERNAL_EMBED,
            description = "Direct responsive cloud stream player with rapid loading",
            urlBuilder = { id, type, s, e, _, _ ->
                if (type == "tv") "https://smashystream.xyz/tv/$id?s=$s&e=$e"
                else "https://smashystream.xyz/movie/$id"
            }
        ),
        StreamServer(
            id = "anyembed_xyz",
            name = "Server 5 · AnyEmbed Node",
            shortLabel = "Server 5 · AnyEmbed",
            badge = "Fast Mirror",
            speedTier = "stable",
            kind = StreamServerKind.EXTERNAL_EMBED,
            description = "Clean direct TMDB stream mirror for movies and television series",
            urlBuilder = { id, type, s, e, _, _ ->
                if (type == "tv") "https://anyembed.xyz/embed/tmdb-tv-$id-$s-$e"
                else "https://anyembed.xyz/embed/tmdb-movie-$id"
            }
        ),
        StreamServer(
            id = "custom_url",
            name = "Server 6 · Native ExoPlayer Direct Stream",
            shortLabel = "Server 6 · ExoPlayer",
            badge = "Media3 · Tracks",
            speedTier = "ultra_fast",
            kind = StreamServerKind.DIRECT_MEDIA,
            description = "Hardware-accelerated AndroidX Media3 ExoPlayer for .mp4, .m3u8 HLS, and .mpd DASH with audio & subtitle track selection",
            urlBuilder = { _, _, _, _, customUrl, _ -> customUrl.trim() }
        ),
        StreamServer(
            id = "tmdb_youtube",
            name = "Server 7 · TMDB Official Trailer & Clips",
            shortLabel = "Server 7 · Trailer",
            badge = "Official Trailer",
            speedTier = "ultra_fast",
            kind = StreamServerKind.TMDB_YOUTUBE,
            description = "Watch the official 4K/1080p theatrical trailer, teaser, or clip directly from TMDB",
            urlBuilder = { _, _, _, _, _, ytKey ->
                if (!ytKey.isNullOrBlank()) {
                    "https://www.youtube-nocookie.com/embed/$ytKey?autoplay=1&rel=0&modestbranding=1&playsinline=1"
                } else ""
            }
        )
    )

    fun findById(id: String): StreamServer =
        all.find { it.id == id } ?: all.first()

    fun nextServer(currentId: String): StreamServer {
        val idx = all.indexOfFirst { it.id == currentId }
        val nextIdx = if (idx < 0) 0 else (idx + 1) % all.size
        return all[nextIdx]
    }
}

@Serializable
data class WatchlistEntry(
    val id: Int,
    val mediaType: String,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String = "",
    val voteAverage: Double = 0.0,
    val genreIds: List<Int> = emptyList(),
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toMediaItem(): MediaItem = MediaItem(
        id = id,
        mediaType = mediaType,
        title = if (mediaType == "movie") title else null,
        name = if (mediaType == "tv") title else null,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = if (mediaType == "movie") releaseDate else null,
        firstAirDate = if (mediaType == "tv") releaseDate else null,
        voteAverage = voteAverage,
        genreIds = genreIds
    )
}

@Serializable
data class WatchHistoryEntry(
    val id: Int,
    val mediaType: String,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String = "",
    val voteAverage: Double = 0.0,
    val genreIds: List<Int> = emptyList(),
    val progressSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val watchedAt: Long = System.currentTimeMillis()
) {
    fun toMediaItem(): MediaItem = MediaItem(
        id = id,
        mediaType = mediaType,
        title = if (mediaType == "movie") title else null,
        name = if (mediaType == "tv") title else null,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = if (mediaType == "movie") releaseDate else null,
        firstAirDate = if (mediaType == "tv") releaseDate else null,
        voteAverage = voteAverage,
        genreIds = genreIds
    )

    val progressFraction: Float
        get() = if (durationSeconds > 0L) {
            (progressSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

@Serializable
data class CloudSyncAccount(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val idToken: String = "",
    val refreshToken: String = ""
)

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val channelCount: Int,
    val isSelected: Boolean
)

data class SubtitleTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean
)
