package com.cineflix.tv.data.remote

import com.cineflix.tv.data.model.CloudSyncAccount
import com.cineflix.tv.data.model.WatchHistoryEntry
import com.cineflix.tv.data.model.WatchlistEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Connects to CineFlix's Firebase Auth & Cloud Firestore REST API so users can sync their
 * My List and Viewing History between https://cineflixbymj.vercel.app and Google TV,
 * including on AOSP/Google TV emulators without Google Play Services.
 */
class CloudSyncRepository {
    companion object {
        private const val PROJECT_ID = "notional-tribute-z53bd"
        private const val DATABASE_ID = "ai-studio-cineflixmovietvd-6a3ad367-76a6-462e-8f9f-0bd6c86f8c4b"
        private const val FIREBASE_API_KEY = "AIzaSyAPFIJGg_NXmtBh4wW5oe67A5oOMjx_eUs"
        private const val AUTH_BASE = "https://identitytoolkit.googleapis.com/v1"
        private const val FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signInWithEmail(email: String, password: String): Result<CloudSyncAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("email", email.trim())
                    put("password", password)
                    put("returnSecureToken", true)
                }
                val req = Request.Builder()
                    .url("$AUTH_BASE/accounts:signInWithPassword?key=$FIREBASE_API_KEY")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val obj = JSONObject(bodyStr)
                    if (!resp.isSuccessful) {
                        val msg = obj.optJSONObject("error")?.optString("message") ?: "Sign-in failed"
                        throw IllegalStateException(msg)
                    }
                    CloudSyncAccount(
                        uid = obj.getString("localId"),
                        email = obj.optString("email", email),
                        displayName = obj.optString("displayName", email.substringBefore("@")),
                        idToken = obj.getString("idToken"),
                        refreshToken = obj.optString("refreshToken")
                    )
                }
            }
        }

    suspend fun signUpWithEmail(email: String, password: String): Result<CloudSyncAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("email", email.trim())
                    put("password", password)
                    put("returnSecureToken", true)
                }
                val req = Request.Builder()
                    .url("$AUTH_BASE/accounts:signUp?key=$FIREBASE_API_KEY")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val obj = JSONObject(bodyStr)
                    if (!resp.isSuccessful) {
                        val msg = obj.optJSONObject("error")?.optString("message") ?: "Registration failed"
                        throw IllegalStateException(msg)
                    }
                    CloudSyncAccount(
                        uid = obj.getString("localId"),
                        email = obj.optString("email", email),
                        displayName = email.substringBefore("@"),
                        idToken = obj.getString("idToken"),
                        refreshToken = obj.optString("refreshToken")
                    )
                }
            }
        }

    suspend fun fetchWatchlist(account: CloudSyncAccount): Result<List<WatchlistEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$FIRESTORE_BASE/users/${account.uid}/watchlist?pageSize=100")
                    .header("Authorization", "Bearer ${account.idToken}")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Firestore HTTP ${resp.code}")
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    val docs = obj.optJSONArray("documents") ?: JSONArray()
                    val list = mutableListOf<WatchlistEntry>()
                    for (i in 0 until docs.length()) {
                        val fields = docs.getJSONObject(i).optJSONObject("fields") ?: continue
                        val id = fields.intVal("tmdbId") ?: continue
                        val mediaType = fields.strVal("mediaType") ?: "movie"
                        val title = fields.strVal("title") ?: "Untitled"
                        list += WatchlistEntry(
                            id = id,
                            mediaType = mediaType,
                            title = title,
                            overview = fields.strVal("overview").orEmpty(),
                            posterPath = fields.strVal("posterPath")?.takeIf { it.isNotBlank() },
                            backdropPath = fields.strVal("backdropPath")?.takeIf { it.isNotBlank() },
                            releaseDate = fields.strVal("releaseDate").orEmpty(),
                            voteAverage = fields.doubleVal("voteAverage") ?: 0.0,
                            genreIds = fields.intListVal("genreIds")
                        )
                    }
                    list
                }
            }
        }

    suspend fun upsertWatchlistItem(account: CloudSyncAccount, entry: WatchlistEntry) =
        withContext(Dispatchers.IO) {
            runCatching {
                val docId = "${entry.mediaType}_${entry.id}"
                val fields = JSONObject().apply {
                    put("userId", strField(account.uid))
                    put("tmdbId", intField(entry.id))
                    put("mediaType", strField(entry.mediaType))
                    put("title", strField(entry.title.take(200)))
                    put("overview", strField(entry.overview.take(1000)))
                    put("posterPath", strField(entry.posterPath.orEmpty()))
                    put("backdropPath", strField(entry.backdropPath.orEmpty()))
                    put("releaseDate", strField(entry.releaseDate))
                    put("voteAverage", doubleField(entry.voteAverage))
                    put("genreIds", intArrayField(entry.genreIds))
                }
                val body = JSONObject().put("fields", fields)
                val req = Request.Builder()
                    .url("$FIRESTORE_BASE/users/${account.uid}/watchlist/$docId")
                    .header("Authorization", "Bearer ${account.idToken}")
                    .patch(body.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().close()
            }
        }

    suspend fun deleteWatchlistItem(account: CloudSyncAccount, mediaType: String, tmdbId: Int) =
        withContext(Dispatchers.IO) {
            runCatching {
                val docId = "${mediaType}_$tmdbId"
                val req = Request.Builder()
                    .url("$FIRESTORE_BASE/users/${account.uid}/watchlist/$docId")
                    .header("Authorization", "Bearer ${account.idToken}")
                    .delete()
                    .build()
                client.newCall(req).execute().close()
            }
        }

    suspend fun upsertHistoryItem(account: CloudSyncAccount, entry: WatchHistoryEntry) =
        withContext(Dispatchers.IO) {
            runCatching {
                val docId = "${entry.mediaType}_${entry.id}"
                val fields = JSONObject().apply {
                    put("userId", strField(account.uid))
                    put("tmdbId", intField(entry.id))
                    put("mediaType", strField(entry.mediaType))
                    put("title", strField(entry.title.take(200)))
                    put("overview", strField(entry.overview.take(1000)))
                    put("posterPath", strField(entry.posterPath.orEmpty()))
                    put("backdropPath", strField(entry.backdropPath.orEmpty()))
                    put("releaseDate", strField(entry.releaseDate))
                    put("voteAverage", doubleField(entry.voteAverage))
                    put("genreIds", intArrayField(entry.genreIds))
                    put("progressSeconds", intField(entry.progressSeconds.toInt()))
                    put("durationSeconds", intField(entry.durationSeconds.toInt()))
                    put("seasonNumber", intField(entry.seasonNumber))
                    put("episodeNumber", intField(entry.episodeNumber))
                }
                val body = JSONObject().put("fields", fields)
                val req = Request.Builder()
                    .url("$FIRESTORE_BASE/users/${account.uid}/history/$docId")
                    .header("Authorization", "Bearer ${account.idToken}")
                    .patch(body.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().close()
            }
        }

    private fun strField(v: String) = JSONObject().put("stringValue", v)
    private fun intField(v: Int) = JSONObject().put("integerValue", v.toString())
    private fun doubleField(v: Double) = JSONObject().put("doubleValue", v)
    private fun intArrayField(list: List<Int>): JSONObject {
        val arr = JSONArray()
        list.take(10).forEach { arr.put(intField(it)) }
        return JSONObject().put("arrayValue", JSONObject().put("values", arr))
    }

    private fun JSONObject.strVal(key: String): String? =
        optJSONObject(key)?.optString("stringValue")

    private fun JSONObject.intVal(key: String): Int? =
        optJSONObject(key)?.optString("integerValue")?.toIntOrNull()

    private fun JSONObject.doubleVal(key: String): Double? {
        val obj = optJSONObject(key) ?: return null
        return if (obj.has("doubleValue")) obj.optDouble("doubleValue")
        else obj.optString("integerValue").toDoubleOrNull()
    }

    private fun JSONObject.intListVal(key: String): List<Int> {
        val arr = optJSONObject(key)?.optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            arr.getJSONObject(i).optString("integerValue").toIntOrNull()?.let { out += it }
        }
        return out
    }
}
