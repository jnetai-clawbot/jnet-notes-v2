package com.jnet.notes

/**
 * App version constants — auto-synced with build.gradle versionName.
 * When you bump versionName in app/build.gradle, update VERSION_NAME here too.
 * This ensures About section always matches the GitHub release tag.
 */
object BuildConfig {
    const val VERSION_NAME = "1.2.64"
    const val VERSION_CODE = 64
    const val GITHUB_REPO = "jnetai-clawbot/jnet-notes-v2"
    const val GITHUB_RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
}
