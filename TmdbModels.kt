package com.cineflix.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class MediaItem(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val runtime: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val tagline: String? = null,
    val status: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("created_by") val createdBy: List<CreatorDto> = emptyList(),
    val seasons: List<SeasonSummaryDto> = emptyList()
) {
    val resolvedTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
            ?: "Untitled"

    val resolvedYear: String
        get() {
            val raw = releaseDate?.takeIf { it.isNotBlank() } ?: firstAirDate
            if (raw.isNullOrBlank() || raw.length < 4) return "TBA"
            val year = raw.substring(0, 4)
            return if (year.all { it.isDigit() }) year else "TBA"
        }

    fun resolvedMediaType(defaultType: String = "movie"): String {
        if (mediaType == "movie" || mediaType == "tv") return mediaType
        if ((!firstAirDate.isNullOrBlank() && releaseDate.isNullOrBlank()) ||
            (!name.isNullOrBlank() && title.isNullOrBlank())
        ) {
            return "tv"
        }
        return defaultType
    }

    fun posterUrl(size: String = "w500"): String? =
        posterPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun backdropUrl(size: String = "w1280"): String? =
        backdropPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun allGenreIds(): List<Int> =
        if (genreIds.isNotEmpty()) genreIds else genres.map { it.id }

    fun matchPercentage(affinity: Map<Int, Int>): Int {
        val base = ((if (voteAverage > 0.0) voteAverage else 7.5) * 9.5).roundToInt()
        var bonus = 0
        for (gid in allGenreIds()) {
            if (affinity.containsKey(gid)) bonus += 3
        }
        return (base + bonus).coerceIn(78, 99)
    }

    fun formattedRuntimeOrSeasons(): String? {
        if (resolvedMediaType() == "movie" && runtime != null && runtime > 0) {
            val h = runtime / 60
            val m = runtime % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
        if (resolvedMediaType() == "tv") {
            val parts = mutableListOf<String>()
            if (numberOfSeasons != null && numberOfSeasons > 0) {
                parts += "$numberOfSeasons Season${if (numberOfSeasons > 1) "s" else ""}"
            }
            if (numberOfEpisodes != null && numberOfEpisodes > 0) {
                parts += "$numberOfEpisodes Episodes"
            }
            if (parts.isNotEmpty()) return parts.joinToString(" · ")
        }
        return null
    }
}

@Serializable
data class GenreDto(
    val id: Int,
    val name: String
)

@Serializable
data class GenreListResponse(
    val genres: List<GenreDto> = emptyList()
)

@Serializable
data class CreatorDto(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
data class PagedMediaResponse(
    val page: Int = 1,
    val results: List<MediaItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0
)

@Serializable
data class CastMemberDto(
    val id: Int,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null
) {
    fun profileUrl(size: String = "w185"): String? =
        profilePath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }
}

@Serializable
data class CrewMemberDto(
    val id: Int,
    val name: String = "",
    val job: String? = null,
    val department: String? = null
)

@Serializable
data class CreditsResponse(
    val id: Int = 0,
    val cast: List<CastMemberDto> = emptyList(),
    val crew: List<CrewMemberDto> = emptyList()
)

@Serializable
data class VideoDto(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val size: Int = 1080,
    val type: String = "",
    val official: Boolean = false
)

@Serializable
data class VideosResponse(
    val id: Int = 0,
    val results: List<VideoDto> = emptyList()
) {
    fun selectBestTrailers(): List<VideoDto> {
        val youtube = results.filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
        val sorted = youtube.sortedWith(
            compareByDescending<VideoDto> { it.official }
                .thenBy {
                    when (it.type) {
                        "Trailer" -> 0
                        "Teaser" -> 1
                        "Clip" -> 2
                        "Featurette" -> 3
                        else -> 4
                    }
                }
                .thenByDescending { it.size }
        )
        return sorted
    }
}

@Serializable
data class SeasonSummaryDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("season_number") val seasonNumber: Int = 1,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("air_date") val airDate: String? = null
)

@Serializable
data class EpisodeDto(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 1,
    @SerialName("season_number") val seasonNumber: Int = 1,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0
) {
    fun stillUrl(size: String = "w500"): String? =
        stillPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }
}

@Serializable
data class SeasonDetailResponse(
    val id: Int = 0,
    val name: String = "",
    @SerialName("season_number") val seasonNumber: Int = 1,
    val episodes: List<EpisodeDto> = emptyList()
)
