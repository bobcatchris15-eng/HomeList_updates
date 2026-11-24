package com.example.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.MeshPeerEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AvailableUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val sourceLabel: String
)

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    val name: String = "",
    @Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @Json(name = "html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

object AppUpdateChecker {
    const val DEFAULT_GITHUB_REPO = "bobcatchris15-eng/HomeList_updates"
    const val DEFAULT_REPO_URL = "https://github.com/$DEFAULT_GITHUB_REPO"
    const val DEFAULT_RELEASES_URL = "$DEFAULT_REPO_URL/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val releaseAdapter = moshi.adapter(GitHubReleaseDto::class.java)

    /**
     * Checks GitHub Releases API for a newer HomeList release.
     * Returns null when already up to date or no releases exist yet.
     */
    suspend fun checkGitHubLatestRelease(
        configuredUrl: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): Result<AvailableUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val repoSlug = extractGitHubRepoSlug(configuredUrl) ?: DEFAULT_GITHUB_REPO
            val apiUrl = "https://api.github.com/repos/$repoSlug/releases/latest"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HomeList-Android/$currentVersionName")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // Repo exists, no published releases yet
                    return@withContext Result.success(null)
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("GitHub check returned HTTP ${response.code}")
                    )
                }
                val body = response.body?.string().orEmpty()
                val dto = releaseAdapter.fromJson(body) ?: return@withContext Result.success(null)
                if (dto.draft) return@withContext Result.success(null)

                val remoteTag = dto.tagName.trim().ifBlank { dto.name.trim() }
                if (!isNewerVersion(remoteTag, currentVersionName)) {
                    return@withContext Result.success(null)
                }

                val apkAssetUrl = dto.assets
                    .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank() }
                    ?.browserDownloadUrl
                val targetUrl = apkAssetUrl
                    ?: dto.htmlUrl.ifBlank { configuredUrl.ifBlank { DEFAULT_RELEASES_URL } }

                Result.success(
                    AvailableUpdateInfo(
                        versionName = remoteTag.removePrefix("v").removePrefix("V"),
                        versionCode = currentVersionCode + 1,
                        downloadUrl = targetUrl,
                        releaseNotes = dto.body.trim().lines().take(3).joinToString(" ").take(160),
                        sourceLabel = "GitHub Release"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks whether any family phone on the local mesh is already running a newer HomeList version.
     */
    fun detectMeshPeerUpdate(
        peers: List<MeshPeerEntity>,
        currentVersionCode: Int,
        currentVersionName: String,
        configuredUrl: String
    ): AvailableUpdateInfo? {
        val newestPeer = peers
            .filter { peer ->
                peer.appVersionCode > currentVersionCode ||
                    isNewerVersion(peer.appVersionName, currentVersionName)
            }
            .maxWithOrNull(
                compareBy<MeshPeerEntity> { it.appVersionCode }
                    .thenBy { parseVersionParts(it.appVersionName).firstOrNull() ?: 0 }
            ) ?: return null

        val targetUrl = configuredUrl.trim().ifBlank { DEFAULT_RELEASES_URL }
        return AvailableUpdateInfo(
            versionName = newestPeer.appVersionName.ifBlank { "Build ${newestPeer.appVersionCode}" },
            versionCode = newestPeer.appVersionCode,
            downloadUrl = targetUrl,
            releaseNotes = "${newestPeer.displayName}'s device is already running this newer version.",
            sourceLabel = "Family Mesh"
        )
    }

    /**
     * Opens the release or APK download URL in the system browser (zero extra permissions).
     */
    fun openUpdateUrl(context: Context, url: String): Boolean {
        val clean = url.trim().ifBlank { DEFAULT_RELEASES_URL }
        if (!clean.startsWith("https://", ignoreCase = true) &&
            !clean.startsWith("http://", ignoreCase = true)
        ) {
            return false
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun extractGitHubRepoSlug(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return DEFAULT_GITHUB_REPO
        val cleaned = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        val parts = if (cleaned.startsWith("github.com/", ignoreCase = true)) {
            cleaned.substringAfter("github.com/").split("/").filter { it.isNotBlank() }
        } else if (!cleaned.contains("://") && cleaned.count { it == '/' } == 1) {
            cleaned.split("/").filter { it.isNotBlank() }
        } else {
            return null
        }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else null
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = parseVersionParts(remote)
        val cParts = parseVersionParts(current)
        if (rParts.isEmpty() || cParts.isEmpty()) return false
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val stripped = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
        return stripped.split(".").mapNotNull { it.trim().toIntOrNull() }
    }
}
