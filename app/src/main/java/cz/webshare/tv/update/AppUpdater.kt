package cz.webshare.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cz.webshare.tv.api.WebshareApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class GitVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

class AppUpdater(
    private val client: OkHttpClient = WebshareApi.defaultHttpClient()
) {
    companion object {
        const val GITHUB_REPO_DISPLAY = "github.com/pagerek/mojetelka"
        private const val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/pagerek/mojetelka/main/version.json"
        private const val DEFAULT_APK_URL =
            "https://raw.githubusercontent.com/pagerek/mojetelka/main/WebshareTV.apk"

        fun getCurrentVersionCode(context: Context): Int = runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        }.getOrDefault(1)

        fun getCurrentVersionName(context: Context): String = runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    suspend fun fetchRemoteVersion(): Result<GitVersionInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val urlWithCacheBuster = "$VERSION_JSON_URL?t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(urlWithCacheBuster)
                .header("Cache-Control", "no-cache, no-store")
                .header("User-Agent", "WebshareTV-Updater/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Git repozitář vrátil HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw IllegalStateException("Prázdná odpověď z version.json")
                val json = JSONObject(body)
                GitVersionInfo(
                    versionCode = json.optInt("versionCode", 1),
                    versionName = json.optString("versionName", "1.0.0"),
                    apkUrl = json.optString("apkUrl", DEFAULT_APK_URL).ifEmpty { DEFAULT_APK_URL },
                    changelog = json.optString("changelog", "")
                )
            }
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        apkUrl: String,
        onProgressPercent: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val urlWithCacheBuster = if (apkUrl.contains("?")) {
                "$apkUrl&t=${System.currentTimeMillis()}"
            } else {
                "$apkUrl?t=${System.currentTimeMillis()}"
            }
            val request = Request.Builder()
                .url(urlWithCacheBuster)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "WebshareTV-Updater/1.0")
                .get()
                .build()

            val targetFile = File(context.cacheDir, "WebshareTV-update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Stažení APK selhalo (HTTP ${response.code})")
                }
                val body = response.body
                    ?: throw IllegalStateException("Chybí obsah APK souboru.")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                var lastReportedPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgressPercent(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (!targetFile.exists() || targetFile.length() < 1_000_000L) {
                throw IllegalStateException("Stažený APK soubor je neúplný nebo poškozený.")
            }
            targetFile
        }
    }

    /**
     * Launches the Android system package installer for the downloaded APK.
     * Returns true if the installer intent was launched, or false if the user first needs
     * to grant "Install unknown apps" permission in the opened settings screen.
     */
    fun triggerInstallApk(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = runCatching {
                context.packageManager.canRequestPackageInstalls()
            }.getOrDefault(true)

            if (!canInstall) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = runCatching {
                    context.startActivity(permIntent)
                    true
                }.getOrDefault(false)

                if (!launched) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
                return false
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }
}
