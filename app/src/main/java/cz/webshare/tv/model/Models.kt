package cz.webshare.tv.model

import androidx.compose.runtime.Immutable

enum class MediaType(val labelCz: String) {
    MOVIE("Film"),
    SERIES("Seriál")
}

enum class VideoQuality(
    val badge: String,
    val labelCz: String,
    val rank: Int
) {
    UHD_4K("4K UHD", "4K Ultra HD (2160p)", 4),
    FHD_1080P("1080p", "Full HD (1080p)", 3),
    HD_720P("720p", "HD (720p)", 2),
    SD("SD", "Standardní (SD)", 1);

    companion object {
        fun fallbackOrderFor(preferred: VideoQuality): List<VideoQuality> = when (preferred) {
            UHD_4K -> listOf(UHD_4K, FHD_1080P, HD_720P, SD)
            FHD_1080P -> listOf(FHD_1080P, UHD_4K, HD_720P, SD)
            HD_720P -> listOf(HD_720P, FHD_1080P, SD, UHD_4K)
            SD -> listOf(SD, HD_720P, FHD_1080P, UHD_4K)
        }
    }
}

enum class AudioLanguage(
    val badge: String,
    val labelCz: String,
    val shortLabel: String,
    val rank: Int
) {
    CZ_SK("CZ / SK", "Český / Slovenský dabing (CZ/SK)", "CZ/SK Dabing", 3),
    CZ_SUB("CZ TIT", "Originál + České titulky", "CZ Titulky", 2),
    EN_ORIG("EN / ORIG", "Originální znění (EN)", "Originál", 1);

    companion object {
        fun fallbackOrderFor(preferred: AudioLanguage): List<AudioLanguage> = when (preferred) {
            CZ_SK -> listOf(CZ_SK, CZ_SUB, EN_ORIG)
            CZ_SUB -> listOf(CZ_SUB, CZ_SK, EN_ORIG)
            EN_ORIG -> listOf(EN_ORIG, CZ_SUB, CZ_SK)
        }
    }
}

enum class LaunchMode(val labelCz: String, val descriptionCz: String) {
    AUTO_PREFERRED_WITH_FALLBACK(
        "Automaticky podle výchozí kvality a jazyka (s fallbackem)",
        "Kliknutí na Přehrát rovnou spustí nejlepší stream podle tvého nastavení. Pokud není dostupný, vybere nejbližší alternativu."
    ),
    ALWAYS_ASK(
        "Vždy zobrazit výběr kvality a jazyka",
        "Před spuštěním vždy zobrazí seskupenou nabídku dostupných jazyků a kvalit."
    )
}

const val DEFAULT_TMDB_API_KEY = "e1ac986d9062e91abb6f6152289caf56"

@Immutable
data class UserPreferences(
    val preferredQuality: VideoQuality = VideoQuality.FHD_1080P,
    val preferredLanguage: AudioLanguage = AudioLanguage.CZ_SK,
    val launchMode: LaunchMode = LaunchMode.AUTO_PREFERRED_WITH_FALLBACK,
    val tmdbApiKey: String = DEFAULT_TMDB_API_KEY
)

@Immutable
data class WebshareUserProfile(
    val username: String,
    val isVip: Boolean,
    val vipDaysLeft: String,
    val vipUntil: String = ""
)

@Immutable
data class CatalogItem(
    val id: String,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val type: MediaType,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null
) {
    val displayYear: String get() = year?.toString() ?: ""
    val displayRating: String?
        get() = rating?.takeIf { it > 0.0 }?.let { String.format("%.1f", it) }
}

@Immutable
data class SeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int
)

@Immutable
data class EpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val stillUrl: String? = null,
    val airDate: String? = null
) {
    val code: String
        get() = "S%02dE%02d".format(seasonNumber, episodeNumber)
}

@Immutable
data class MediaDetails(
    val item: CatalogItem,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodesBySeason: Map<Int, List<EpisodeInfo>> = emptyMap(),
    val cast: List<String> = emptyList(),
    val director: String? = null
)

@Immutable
data class CatalogSection(
    val id: String,
    val title: String,
    val items: List<CatalogItem>
)

@Immutable
data class WebshareFile(
    val ident: String,
    val name: String,
    val sizeBytes: Long,
    val posVotes: Int,
    val negVotes: Int,
    val passwordProtected: Boolean,
    val stripeUrl: String? = null
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0L) return ""
            val gb = sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format("%.2f GB", gb)
            } else {
                val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
                String.format("%.0f MB", mb)
            }
        }
}

@Immutable
data class ParsedStream(
    val file: WebshareFile,
    val quality: VideoQuality,
    val language: AudioLanguage,
    val isHdr: Boolean,
    val isDolbyVision: Boolean,
    val isHevc: Boolean,
    val isSurround51: Boolean,
    val extension: String,
    val score: Int
) {
    val techBadges: List<String>
        get() = buildList {
            add(quality.badge)
            add(language.badge)
            if (isDolbyVision) add("Dolby Vision")
            else if (isHdr) add("HDR")
            if (isHevc) add("HEVC")
            if (isSurround51) add("5.1")
            if (file.sizeBytes > 0L) add(file.formattedSize)
        }
}

@Immutable
data class StreamGroup(
    val language: AudioLanguage,
    val quality: VideoQuality,
    val streams: List<ParsedStream>
) {
    val bestStream: ParsedStream get() = streams.first()
    val label: String get() = "${language.badge} • ${quality.badge}"
    val sizeRangeLabel: String
        get() = if (streams.size == 1) {
            streams.first().file.formattedSize
        } else {
            "${streams.first().file.formattedSize} (${streams.size}×)"
        }
}

@Immutable
data class StreamResolutionResult(
    val queryTitle: String,
    val allStreams: List<ParsedStream>,
    val groups: List<StreamGroup>,
    val recommendedStream: ParsedStream?,
    val fallbackApplied: Boolean,
    val selectionNotice: String?
)

@Immutable
data class WatchProgressItem(
    val mediaId: String,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val subtitle: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val ident: String,
    val streamFileName: String,
    val sizeBytes: Long = 0L,
    val positionMs: Long,
    val durationMs: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.99f)
        } else 0f

    val formattedPosition: String
        get() {
            val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
}
