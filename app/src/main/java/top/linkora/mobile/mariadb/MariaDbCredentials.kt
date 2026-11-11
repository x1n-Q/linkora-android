package top.linkora.mobile.mariadb

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

data class MariaDbCredentials(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    internal val rootPassword: String,
)

object MariaDbCredentialStore {
    const val HOST = "127.0.0.1"
    const val PORT = 3307
    const val DATABASE = "linkora"
    const val USERNAME = "linkora"
    private const val PREFERENCES = "mariadb_credentials"
    private const val PASSWORD_KEY = "database_password"
    private const val ROOT_PASSWORD_KEY = "root_password"

    fun load(context: Context): MariaDbCredentials {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val password = preferences.getString(PASSWORD_KEY, null) ?: randomPassword().also {
            preferences.edit().putString(PASSWORD_KEY, it).apply()
        }
        val rootPassword = preferences.getString(ROOT_PASSWORD_KEY, null) ?: randomPassword().also {
            preferences.edit().putString(ROOT_PASSWORD_KEY, it).apply()
        }
        return MariaDbCredentials(
            host = HOST,
            port = PORT,
            database = DATABASE,
            username = USERNAME,
            password = password,
            rootPassword = rootPassword,
        )
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }
}
