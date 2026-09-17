package com.sharif.sink.networking.update

import android.content.Context
import android.content.pm.PackageManager
import com.sharif.sink.logging.SinkLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

private const val TAG = "UpdateChecker"
private const val GITHUB_API_URL = "https://api.github.com/repos/Sharifwa123/Sink/releases/latest"

/**
 * The one network call Sink makes on its own, and it's optional and
 * disclosed (see docs/SECURITY.md, Settings' "Check for updates" toggle):
 * a GET against GitHub's public releases API to see if a newer build
 * exists, since Sink isn't distributed through Play Store auto-updates.
 * No account, device identifier, or telemetry is sent — this call carries
 * no more than any browser visit to the same public URL would.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: SinkLogger,
) {
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val currentVersion = currentVersionName() ?: return@withContext null
        try {
            val connection = (URL(GITHUB_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val json = JSONObject(body)
            val latestVersion = json.getString("tag_name").removePrefix("v")
            if (!isNewer(latestVersion, currentVersion)) return@withContext null

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }
            val releaseUrl = json.getString("html_url")
            UpdateInfo(latestVersion = latestVersion, downloadUrl = apkUrl ?: releaseUrl, releaseUrl = releaseUrl)
        } catch (e: Exception) {
            // No connectivity, GitHub unreachable, rate-limited, malformed response — an
            // update check failing is never worth surfacing as an error to the user.
            logger.w(TAG, "Update check failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun currentVersionName(): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
