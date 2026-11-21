package cz.webshare.tv.data

import android.content.Context
import android.content.SharedPreferences
import cz.webshare.tv.model.AudioLanguage
import cz.webshare.tv.model.CatalogItem
import cz.webshare.tv.model.LaunchMode
import cz.webshare.tv.model.MediaType
import cz.webshare.tv.model.UserPreferences
import cz.webshare.tv.model.VideoQuality
import cz.webshare.tv.model.WatchProgressItem
import org.json.JSONArray
import org.json.JSONObject

class PreferencesStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("webshare_tv_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WST_TOKEN = "wst_token"
        private const val KEY_WS_USERNAME = "ws_username"
        private const val KEY_PREF_QUALITY = "pref_quality"
        private const val KEY_PREF_LANGUAGE = "pref_language"
        private const val KEY_LAUNCH_MODE = "launch_mode"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_WATCH_HISTORY = "watch_history_json"
        private const val KEY_MY_LIST = "my_list_json"
    }

    fun getWebshareToken(): String? = prefs.getString(KEY_WST_TOKEN, null)?.takeIf { it.isNotEmpty() }

    fun getSavedUsername(): String = prefs.getString(KEY_WS_USERNAME, "") ?: ""

    fun saveWebshareSession(token: String, username: String) {
        prefs.edit()
            .putString(KEY_WST_TOKEN, token)
            .putString(KEY_WS_USERNAME, username)
            .apply()
    }

    fun clearWebshareSession() {
        prefs.edit()
            .remove(KEY_WST_TOKEN)
            .apply()
    }

    fun getUserPreferences(): UserPreferences {
        val qualName = prefs.getString(KEY_PREF_QUALITY, VideoQuality.FHD_1080P.name)
        val langName = prefs.getString(KEY_PREF_LANGUAGE, AudioLanguage.CZ_SK.name)
        val modeName = prefs.getString(KEY_LAUNCH_MODE, LaunchMode.AUTO_PREFERRED_WITH_FALLBACK.name)
        val tmdbKey = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""

        val quality = runCatching { VideoQuality.valueOf(qualName!!) }.getOrDefault(VideoQuality.FHD_1080P)
        val language = runCatching { AudioLanguage.valueOf(langName!!) }.getOrDefault(AudioLanguage.CZ_SK)
        val mode = runCatching { LaunchMode.valueOf(modeName!!) }.getOrDefault(LaunchMode.AUTO_PREFERRED_WITH_FALLBACK)

        return UserPreferences(
            preferredQuality = quality,
            preferredLanguage = language,
            launchMode = mode,
            tmdbApiKey = tmdbKey
        )
    }

    fun saveUserPreferences(userPrefs: UserPreferences) {
        prefs.edit()
            .putString(KEY_PREF_QUALITY, userPrefs.preferredQuality.name)
            .putString(KEY_PREF_LANGUAGE, userPrefs.preferredLanguage.name)
            .putString(KEY_LAUNCH_MODE, userPrefs.launchMode.name)
            .putString(KEY_TMDB_API_KEY, userPrefs.tmdbApiKey.trim())
            .apply()
    }

    fun getWatchHistory(): List<WatchProgressItem> {
        val raw = prefs.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<WatchProgressItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    WatchProgressItem(
                        mediaId = obj.optString("mediaId"),
                        mediaType = runCatching { MediaType.valueOf(obj.optString("mediaType")) }
                            .getOrDefault(MediaType.MOVIE),
                        title = obj.optString("title"),
                        originalTitle = obj.optString("originalTitle"),
                        year = obj.optInt("year", 0).takeIf { it > 0 },
                        subtitle = obj.optString("subtitle").takeIf { it.isNotEmpty() },
                        posterUrl = obj.optString("posterUrl").takeIf { it.isNotEmpty() },
                        backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotEmpty() },
                        ident = obj.optString("ident"),
                        streamFileName = obj.optString("streamFileName"),
                        positionMs = obj.optLong("positionMs", 0L),
                        durationMs = obj.optLong("durationMs", 0L),
                        seasonNumber = obj.optInt("seasonNumber", -1).takeIf { it > 0 },
                        episodeNumber = obj.optInt("episodeNumber", -1).takeIf { it > 0 },
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
                )
            }
            list.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun saveWatchProgress(item: WatchProgressItem) {
        val existing = getWatchHistory().toMutableList()
        existing.removeAll {
            it.mediaId == item.mediaId &&
                it.seasonNumber == item.seasonNumber &&
                it.episodeNumber == item.episodeNumber
        }
        // Only keep in Continue Watching if watched at least 10s and not >96% finished
        val isCompleted = item.durationMs > 0 && (item.positionMs.toDouble() / item.durationMs.toDouble()) > 0.96
        if (!isCompleted && item.positionMs >= 5_000L) {
            existing.add(0, item)
        }
        val trimmed = existing.take(25)
        writeWatchHistory(trimmed)
    }

    fun removeWatchHistoryItem(item: WatchProgressItem): List<WatchProgressItem> {
        val existing = getWatchHistory().toMutableList()
        existing.removeAll {
            it.mediaId == item.mediaId &&
                it.seasonNumber == item.seasonNumber &&
                it.episodeNumber == item.episodeNumber
        }
        writeWatchHistory(existing)
        return existing
    }

    private fun writeWatchHistory(items: List<WatchProgressItem>) {
        val arr = JSONArray()
        for (w in items) {
            arr.put(
                JSONObject().apply {
                    put("mediaId", w.mediaId)
                    put("mediaType", w.mediaType.name)
                    put("title", w.title)
                    put("originalTitle", w.originalTitle)
                    if (w.year != null) put("year", w.year)
                    if (w.subtitle != null) put("subtitle", w.subtitle)
                    if (w.posterUrl != null) put("posterUrl", w.posterUrl)
                    if (w.backdropUrl != null) put("backdropUrl", w.backdropUrl)
                    put("ident", w.ident)
                    put("streamFileName", w.streamFileName)
                    put("positionMs", w.positionMs)
                    put("durationMs", w.durationMs)
                    if (w.seasonNumber != null) put("seasonNumber", w.seasonNumber)
                    if (w.episodeNumber != null) put("episodeNumber", w.episodeNumber)
                    put("updatedAt", w.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_WATCH_HISTORY, arr.toString()).apply()
    }

    fun getMyList(): List<CatalogItem> {
        val raw = prefs.getString(KEY_MY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<CatalogItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val genresArr = obj.optJSONArray("genres")
                val genres = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        genresArr.optString(g)?.takeIf { it.isNotEmpty() }?.let { genres.add(it) }
                    }
                }
                list.add(
                    CatalogItem(
                        id = obj.optString("id"),
                        imdbId = obj.optString("imdbId").takeIf { it.isNotEmpty() },
                        tmdbId = obj.optInt("tmdbId", 0).takeIf { it > 0 },
                        type = runCatching { MediaType.valueOf(obj.optString("type")) }.getOrDefault(MediaType.MOVIE),
                        title = obj.optString("title"),
                        originalTitle = obj.optString("originalTitle"),
                        year = obj.optInt("year", 0).takeIf { it > 0 },
                        overview = obj.optString("overview"),
                        posterUrl = obj.optString("posterUrl").takeIf { it.isNotEmpty() },
                        backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotEmpty() },
                        rating = obj.optDouble("rating", 0.0).takeIf { !it.isNaN() && it > 0.0 },
                        genres = genres
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun toggleMyList(item: CatalogItem): List<CatalogItem> {
        val current = getMyList().toMutableList()
        val existingIdx = current.indexOfFirst { it.id == item.id }
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        } else {
            current.add(0, item)
        }
        val arr = JSONArray()
        for (c in current) {
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    if (c.imdbId != null) put("imdbId", c.imdbId)
                    if (c.tmdbId != null) put("tmdbId", c.tmdbId)
                    put("type", c.type.name)
                    put("title", c.title)
                    put("originalTitle", c.originalTitle)
                    if (c.year != null) put("year", c.year)
                    put("overview", c.overview)
                    if (c.posterUrl != null) put("posterUrl", c.posterUrl)
                    if (c.backdropUrl != null) put("backdropUrl", c.backdropUrl)
                    if (c.rating != null) put("rating", c.rating)
                    put("genres", JSONArray(c.genres))
                }
            )
        }
        prefs.edit().putString(KEY_MY_LIST, arr.toString()).apply()
        return current
    }
}
