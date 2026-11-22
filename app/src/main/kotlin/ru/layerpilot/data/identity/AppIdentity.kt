package ru.layerpilot.data.identity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Идентичность оригинальной сборки.
 *
 * Это не «шифрование» и не абсолютная защита: APK можно декомпилировать и
 * пропатчить. Но подпись и водяные знаки не позволяют незаметно выдать
 * пересобранное приложение за официальное.
 */
object AppIdentity {
    const val TELEGRAM_CHANNEL = "https://t.me/BambukHome"
    const val TELEGRAM_AUTHOR = "https://t.me/mikhail_qqq4"
    const val COPYRIGHT = "© 2026 BambukHome"

    /** SHA-256 сертификата оригинального release-ключа. */
    private const val OFFICIAL_CERT_SHA256 =
        "725e5912677170550ea2c5b9cb915b087bb83d14ca252dbfbf133fbac99ca14b"

    fun isOfficial(context: Context): Boolean {
        val signatures = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                ).signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES,
                ).signatures
            }
        } catch (_: Throwable) {
            null
        }
        val certificate = signatures?.firstOrNull()?.toByteArray() ?: return false
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return hash.equals(OFFICIAL_CERT_SHA256, ignoreCase = true)
    }
}
