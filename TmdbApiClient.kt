package com.cineflix.tv.data.remote

import com.cineflix.tv.data.model.CreditsResponse
import com.cineflix.tv.data.model.GenreDto
import com.cineflix.tv.data.model.GenreListResponse
import com.cineflix.tv.data.model.MediaItem
import com.cineflix.tv.data.model.PagedMediaResponse
import com.cineflix.tv.data.model.SeasonDetailResponse
import com.cineflix.tv.data.model.VideosResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TmdbApiException(
    message: String,
    val code: String = "API_ERROR",
    val httpStatus: Int? = null
) : Exception(message)

data class CredentialVerificationResult(
    val valid: Boolean,
    val mode: String,
    val errorMessage: String? = null
)

class TmdbApiClient(
    private val credentialProvider: () -> String
) {
    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        val DEFAULT_GENRE_MAP: Map<Int, String> = mapOf(
            28 to "Action",
            12 to "Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            14 to "Fantasy",
            36 to "History",
            27 to "Horror",
            10402 to "Music",
            9648 to "Mystery",
            10749 to "Romance",
            878 to "Sci-Fi",
            10770 to "TV Movie",
            53 to "Thriller",
            10752 to "War",
            37 to "Western",
            10759 to "Action & Adventure",
            10762 to "Kids",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War & Politics"
        )

        fun cleanCredential(raw: String): String =
            raw.trim()
                .trim('"', '\'')
                .replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")
                .trim()

        fun isApiKeyV3(cleaned: String): Boolean =
            cleaned.matches(Regex("^[a-fA-F0-9]{32}$")) ||
                (!cleaned.startsWith("eyJ") && cleaned.length <= 40)

        fun scoreItem(item: MediaItem, affinity: Map<Int, Int>): Double {
            var score = (item.voteAverage * 2.0) + (item.popularity / 500.0).coerceAtMost(6.0)
            for (gid in item.allGenreIds()) {
                score += (affinity[gid] ?: 0) * 1.8
            }
            return score
        }

        fun sortPersonalized(items: List<MediaItem>, affinity: Map<Int, Int>): List<MediaItem> =
            items.sortedByDescending { scoreItem(it, affinity) }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(
        val body: String,
        val expiresAt: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun clearCache() {
        cache.clear()
    }

    suspend fun verifyCredential(rawCredential: String): CredentialVerificationResult =
        withContext(Dispatchers.IO) {
            val cleaned = cleanCredential(rawCredential)
            if (cleaned.isBlank()) {
                return@withContext CredentialVerificationResult(
                    valid = false,
                    mode = "api_key",
                    errorMessage = "Please enter a TMDB API Key (v3) or Read Access Token (v4)."
                )
            }

            val v3Mode = isApiKeyV3(cleaned)
            val urlBuilder = "$BASE_URL/configuration".toHttpUrl().newBuilder()
            val reqBuilder = Request.Builder().header("Accept", "application/json")
            if (v3Mode) {
                urlBuilder.addQueryParameter("api_key", cleaned)
            } else {
                reqBuilder.header("Authorization", "Bearer $cleaned")
            }
            val request = reqBuilder.url(urlBuilder.build()).get().build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext CredentialVerificationResult(
                            valid = true,
                            mode = if (v3Mode) "api_key" else "bearer"
                        )
                    }

                    // Fallback check as api_key if bearer failed
                    if (!v3Mode) {
                        val fallbackUrl = "$BASE_URL/configuration".toHttpUrl().newBuilder()
                            .addQueryParameter("api_key", cleaned)
                            .build()
                        val fallbackReq = Request.Builder()
                            .url(fallbackUrl)
                            .header("Accept", "application/json")
                            .get()
                            .build()
                        httpClient.newCall(fallbackReq).execute().use { fbResp ->
                            if (fbResp.isSuccessful) {
                                return@withContext CredentialVerificationResult(
                                    valid = true,
                                    mode = "api_key"
                                )
                            }
                        }
                    }

                    val errorBody = response.body?.string().orEmpty()
                    val statusMsg = Regex("\"status_message\"\\s*:\\s*\"([^\"]+)\"")
                        .find(errorBody)?.groupValues?.getOrNull(1)
                        ?: "TMDB returned HTTP ${response.code}. Please verify your credential."

                    CredentialVerificationResult(
                        valid = false,
                        mode = if (v3Mode) "api_key" else "bearer",
                        errorMessage = statusMsg
                    )
                }
            } catch (e: IOException) {
                CredentialVerificationResult(
                    valid = false,
                    mode = if (v3Mode) "api_key" else "bearer",
                    errorMessage = "Network error while verifying key with api.themoviedb.org."
                )
            }
        }

    private suspend inline fun <reified T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        ttlMs: Long = CACHE_TTL_MS
    ): T = withContext(Dispatchers.IO) {
        val rawCred = cleanCredential(credentialProvider())
        if (rawCred.isBlank() ||
            rawCred == "MY_TMDB_ACCESS_TOKEN" ||
            rawCred == "MY_TMDB_API_KEY"
        ) {
            throw TmdbApiException(
                message = "TMDB API Key not configured. Open Settings or Setup to enter your TMDB API Key or Read Access Token.",
                code = "MISSING_TOKEN"
            )
        }

        val v3Mode = isApiKeyV3(rawCred)
        val urlBuilder = "$BASE_URL$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) ->
            if (v.isNotBlank()) urlBuilder.addQueryParameter(k, v)
        }
        if (v3Mode) {
            urlBuilder.addQueryParameter("api_key", rawCred)
        }

        val finalUrl = urlBuilder.build()
        val cacheKey = "${finalUrl}_${rawCred.takeLast(6)}"
        val now = System.currentTimeMillis()
        val cached = cache[cacheKey]
        if (cached != null && cached.expiresAt > now) {
            return@withContext json.decodeFromString<T>(cached.body)
        }

        val reqBuilder = Request.Builder()
            .url(finalUrl)
            .header("Accept", "application/json")
            .get()
        if (!v3Mode) {
            reqBuilder.header("Authorization", "Bearer $rawCred")
        }

        val responseBody = try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val statusMsg = Regex("\"status_message\"\\s*:\\s*\"([^\"]+)\"")
                        .find(raw)?.groupValues?.getOrNull(1)
                        ?: "TMDB request failed (HTTP ${response.code})"
                    when (response.code) {
                        401 -> throw TmdbApiException("Invalid TMDB credential: $statusMsg", "UNAUTHORIZED", 401)
                        429 -> throw TmdbApiException("TMDB rate limit exceeded. Please try again in a moment.", "RATE_LIMITED", 429)
                        404 -> throw TmdbApiException("Requested title not found on TMDB.", "NOT_FOUND", 404)
                        else -> throw TmdbApiException(statusMsg, "API_ERROR", response.code)
                    }
                }
                raw
            }
        } catch (e: TmdbApiException) {
            throw e
        } catch (e: IOException) {
            throw TmdbApiException("Unable to reach TMDB API. Check your TV network connection.", "NETWORK_ERROR")
        }

        cache[cacheKey] = CacheEntry(responseBody, now + ttlMs)
        json.decodeFromString<T>(responseBody)
    }

    suspend fun getTrendingAllDay(page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/trending/all/day",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.filter { it.mediaType != "person" })
    }

    suspend fun getPopularMovies(page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/movie/popular",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = "movie") })
    }

    suspend fun getTopRatedMovies(page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/movie/top_rated",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = "movie") })
    }

    suspend fun getUpcomingMovies(page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/movie/upcoming",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = "movie") })
    }

    suspend fun getPopularTv(page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/tv/popular",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = "tv") })
    }

    suspend fun getTopRatedTv(page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/tv/top_rated",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = "tv") })
    }

    suspend fun getDetails(mediaType: String, id: Int): MediaItem {
        val normalized = if (mediaType == "tv") "tv" else "movie"
        val item = get<MediaItem>("/$normalized/$id", mapOf("language" to "en-US"))
        return item.copy(mediaType = normalized)
    }

    suspend fun getTvSeason(tvId: Int, seasonNumber: Int): SeasonDetailResponse =
        get("/tv/$tvId/season/$seasonNumber", mapOf("language" to "en-US"))

    suspend fun getCredits(mediaType: String, id: Int): CreditsResponse {
        val normalized = if (mediaType == "tv") "tv" else "movie"
        return get("/$normalized/$id/credits", mapOf("language" to "en-US"))
    }

    suspend fun getVideos(mediaType: String, id: Int): VideosResponse {
        val normalized = if (mediaType == "tv") "tv" else "movie"
        return get("/$normalized/$id/videos", mapOf("language" to "en-US"))
    }

    suspend fun getSimilar(mediaType: String, id: Int, page: Int = 1): PagedMediaResponse {
        val normalized = if (mediaType == "tv") "tv" else "movie"
        val resp = get<PagedMediaResponse>(
            "/$normalized/$id/similar",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = normalized) })
    }

    suspend fun getRecommendations(mediaType: String, id: Int, page: Int = 1): PagedMediaResponse {
        val normalized = if (mediaType == "tv") "tv" else "movie"
        val resp = get<PagedMediaResponse>(
            "/$normalized/$id/recommendations",
            mapOf("language" to "en-US", "page" to page.toString())
        )
        return resp.copy(results = resp.results.map { it.copy(mediaType = it.mediaType ?: normalized) })
    }

    suspend fun getMovieGenres(): List<GenreDto> =
        get<GenreListResponse>("/genre/movie/list", mapOf("language" to "en-US"), 30 * 60 * 1000L).genres

    suspend fun getTvGenres(): List<GenreDto> =
        get<GenreListResponse>("/genre/tv/list", mapOf("language" to "en-US"), 30 * 60 * 1000L).genres

    suspend fun discoverMovies(
        genreQuery: String? = null,
        sortBy: String = "popularity.desc",
        page: Int = 1
    ): PagedMediaResponse {
        val mappedSort = if (sortBy == "release_date.desc") "primary_release_date.desc" else sortBy
        val params = mutableMapOf(
            "language" to "en-US",
            "include_adult" to "false",
            "sort_by" to mappedSort,
            "page" to page.toString()
        )
        if (!genreQuery.isNullOrBlank()) params["with_genres"] = genreQuery
        val resp = get<PagedMediaResponse>("/discover/movie", params)
        return resp.copy(results = resp.results.map { it.copy(mediaType = "movie") })
    }

    suspend fun discoverTv(
        genreQuery: String? = null,
        sortBy: String = "popularity.desc",
        page: Int = 1
    ): PagedMediaResponse {
        val mappedSort = if (sortBy == "release_date.desc") "first_air_date.desc" else sortBy
        val params = mutableMapOf(
            "language" to "en-US",
            "include_adult" to "false",
            "sort_by" to mappedSort,
            "page" to page.toString()
        )
        if (!genreQuery.isNullOrBlank()) params["with_genres"] = genreQuery
        val resp = get<PagedMediaResponse>("/discover/tv", params)
        return resp.copy(results = resp.results.map { it.copy(mediaType = "tv") })
    }

    suspend fun searchMulti(query: String, page: Int = 1): PagedMediaResponse {
        val resp = get<PagedMediaResponse>(
            "/search/multi",
            mapOf(
                "language" to "en-US",
                "include_adult" to "false",
                "query" to query,
                "page" to page.toString()
            ),
            ttlMs = 2 * 60 * 1000L
        )
        return resp.copy(results = resp.results.filter { it.mediaType == "movie" || it.mediaType == "tv" })
    }
}
