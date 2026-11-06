package com.cineflix.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cineflix.tv.BuildConfig
import com.cineflix.tv.data.model.CloudSyncAccount
import com.cineflix.tv.data.model.WatchHistoryEntry
import com.cineflix.tv.data.model.WatchlistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.cineFlixDataStore by preferencesDataStore(name = "cineflix_tv_prefs")

data class AppPreferencesState(
    val tmdbCredential: String = "",
    val preferredServerId: String = "vidsrc_pm",
    val autoFailover: Boolean = true,
    val customStreamUrl: String = "",
    val autoplayTrailers: Boolean = true,
    val watchlist: List<WatchlistEntry> = emptyList(),
    val history: List<WatchHistoryEntry> = emptyList(),
    val genreAffinity: Map<Int, Int> = mapOf(28 to 3, 878 to 3, 18 to 2),
    val cloudAccount: CloudSyncAccount? = null
)

class CineFlixPreferences(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val TMDB_CREDENTIAL = stringPreferencesKey("cineflix_byok_tmdb_credential")
        val PREFERRED_SERVER = stringPreferencesKey("cineflix_preferred_server_v8")
        val AUTO_FAILOVER = booleanPreferencesKey("cineflix_auto_failover_v8")
        val CUSTOM_STREAM_URL = stringPreferencesKey("cineflix_custom_stream_url")
        val AUTOPLAY_TRAILERS = booleanPreferencesKey("cineflix_autoplay_trailers")
        val WATCHLIST_JSON = stringPreferencesKey("cineflix_watchlist_v2")
        val HISTORY_JSON = stringPreferencesKey("cineflix_history_v2")
        val GENRE_AFFINITY_JSON = stringPreferencesKey("cineflix_genre_affinity_v2")
        val CLOUD_ACCOUNT_JSON = stringPreferencesKey("cineflix_cloud_account_v1")
    }

    val stateFlow: Flow<AppPreferencesState> = context.cineFlixDataStore.data.map { prefs ->
        val buildFallbackCred = BuildConfig.DEFAULT_TMDB_ACCESS_TOKEN.ifBlank {
            BuildConfig.DEFAULT_TMDB_API_KEY
        }
        val storedCred = prefs[Keys.TMDB_CREDENTIAL].orEmpty().ifBlank { buildFallbackCred }

        val watchlist = prefs[Keys.WATCHLIST_JSON]?.let {
            runCatching { json.decodeFromString<List<WatchlistEntry>>(it) }.getOrNull()
        } ?: emptyList()

        val history = prefs[Keys.HISTORY_JSON]?.let {
            runCatching { json.decodeFromString<List<WatchHistoryEntry>>(it) }.getOrNull()
        } ?: emptyList()

        val affinity = prefs[Keys.GENRE_AFFINITY_JSON]?.let {
            runCatching { json.decodeFromString<Map<Int, Int>>(it) }.getOrNull()
        } ?: mapOf(28 to 3, 878 to 3, 18 to 2)

        val account = prefs[Keys.CLOUD_ACCOUNT_JSON]?.let {
            runCatching { json.decodeFromString<CloudSyncAccount>(it) }.getOrNull()
        }

        AppPreferencesState(
            tmdbCredential = storedCred,
            preferredServerId = prefs[Keys.PREFERRED_SERVER] ?: "vidsrc_pm",
            autoFailover = prefs[Keys.AUTO_FAILOVER] ?: true,
            customStreamUrl = prefs[Keys.CUSTOM_STREAM_URL].orEmpty(),
            autoplayTrailers = prefs[Keys.AUTOPLAY_TRAILERS] ?: true,
            watchlist = watchlist,
            history = history,
            genreAffinity = affinity,
            cloudAccount = account
        )
    }

    suspend fun saveTmdbCredential(credential: String) {
        context.cineFlixDataStore.edit { prefs ->
            if (credential.isBlank()) prefs.remove(Keys.TMDB_CREDENTIAL)
            else prefs[Keys.TMDB_CREDENTIAL] = credential.trim()
        }
    }

    suspend fun savePreferredServer(serverId: String) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_SERVER] = serverId
        }
    }

    suspend fun setAutoFailover(enabled: Boolean) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.AUTO_FAILOVER] = enabled
        }
    }

    suspend fun saveCustomStreamUrl(url: String) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_STREAM_URL] = url.trim()
        }
    }

    suspend fun setAutoplayTrailers(enabled: Boolean) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.AUTOPLAY_TRAILERS] = enabled
        }
    }

    suspend fun updateWatchlist(list: List<WatchlistEntry>) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.WATCHLIST_JSON] = json.encodeToString(list)
        }
    }

    suspend fun updateHistory(list: List<WatchHistoryEntry>) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.HISTORY_JSON] = json.encodeToString(list)
        }
    }

    suspend fun updateGenreAffinity(map: Map<Int, Int>) {
        context.cineFlixDataStore.edit { prefs ->
            prefs[Keys.GENRE_AFFINITY_JSON] = json.encodeToString(map)
        }
    }

    suspend fun saveCloudAccount(account: CloudSyncAccount?) {
        context.cineFlixDataStore.edit { prefs ->
            if (account == null) prefs.remove(Keys.CLOUD_ACCOUNT_JSON)
            else prefs[Keys.CLOUD_ACCOUNT_JSON] = json.encodeToString(account)
        }
    }
}
