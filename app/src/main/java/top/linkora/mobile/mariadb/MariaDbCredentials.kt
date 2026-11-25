package top.linkora.mobile.mariadb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
    private const val ENCRYPTED_CREDENTIALS_KEY = "credentials_ciphertext"
    private const val CREDENTIALS_IV_KEY = "credentials_iv"
    private const val KEY_ALIAS = "linkora_mariadb_credentials"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun load(context: Context): MariaDbCredentials {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val stored = readEncrypted(preferences) ?: migrateOrCreate(preferences)
        return MariaDbCredentials(
            host = HOST,
            port = PORT,
            database = DATABASE,
            username = USERNAME,
            password = stored.password,
            rootPassword = stored.rootPassword,
        )
    }

    private fun migrateOrCreate(preferences: android.content.SharedPreferences): StoredCredentials {
        val credentials = StoredCredentials(
            password = preferences.getString(PASSWORD_KEY, null) ?: randomPassword(),
            rootPassword = preferences.getString(ROOT_PASSWORD_KEY, null) ?: randomPassword(),
        )
        saveEncrypted(preferences, credentials)
        return credentials
    }

    private fun readEncrypted(
        preferences: android.content.SharedPreferences,
    ): StoredCredentials? {
        val encodedIv = preferences.getString(CREDENTIALS_IV_KEY, null) ?: return null
        val encodedCredentials = preferences.getString(ENCRYPTED_CREDENTIALS_KEY, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val payload = cipher.doFinal(
                Base64.decode(encodedCredentials, Base64.NO_WRAP),
            ).toString(Charsets.UTF_8)
            val parts = payload.split('\n', limit = 2)
            check(parts.size == 2 && parts.all(String::isNotBlank)) {
                "Stored database credentials are invalid."
            }
            StoredCredentials(password = parts[0], rootPassword = parts[1])
        }.getOrElse {
            preferences.edit()
                .remove(CREDENTIALS_IV_KEY)
                .remove(ENCRYPTED_CREDENTIALS_KEY)
                .commit()
            null
        }
    }

    private fun saveEncrypted(
        preferences: android.content.SharedPreferences,
        credentials: StoredCredentials,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = "${credentials.password}\n${credentials.rootPassword}"
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        check(
            preferences.edit()
                .putString(CREDENTIALS_IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(
                    ENCRYPTED_CREDENTIALS_KEY,
                    Base64.encodeToString(encrypted, Base64.NO_WRAP),
                )
                .remove(PASSWORD_KEY)
                .remove(ROOT_PASSWORD_KEY)
                .commit(),
        ) { "Database credentials could not be stored securely." }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    private data class StoredCredentials(
        val password: String,
        val rootPassword: String,
    )
}
