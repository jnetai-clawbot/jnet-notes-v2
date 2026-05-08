import android.content.Context
import android.content.pm.PackageManager

/**
 * App version constants — auto-reads from Android manifest at runtime.
 * The ONLY place you bump the version is app/build.gradle → versionName.
 * Everything else reads from there automatically.
 */
object BuildConfig {
    const val GITHUB_REPO = "jnetai-clawbot/jnet-notes-v2"
    const val GITHUB_RELEASES_URL = "https://github.com/$GITHUB_REPO/releases"
    const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private var _versionName: String? = null
    private var _versionCode: Int? = null

    fun VERSION_NAME(context: Context): String {
        if (_versionName == null) {
            _versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            } catch (e: PackageManager.NameNotFoundException) {
                "0.0.0"
            }
        }
        return _versionName!!
    }

    fun VERSION_CODE(context: Context): Int {
        if (_versionCode == null) {
            _versionCode = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                0
            }
        }
        return _versionCode!!
    }
}
