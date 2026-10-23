package com.leaguedesk.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("league_desk", Context.MODE_PRIVATE)

    fun loadSession(): StoredSession? {
        val encoded = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val json = JSONObject(decrypt(encoded))
            val credentials = Credentials(
                label = json.optString("label", "ESPN account"),
                swid = json.getString("swid"),
                s2 = json.getString("s2"),
            )
            val config = LeagueConfig(
                leagueId = json.getString("leagueId"),
                game = GameId.from(json.optString("game")),
                season = json.optString("season", LeagueConfig.DEFAULT_SEASON),
            )
            if (credentials.swid.isBlank() || credentials.s2.isBlank() || config.leagueId.isBlank()) {
                null
            } else {
                StoredSession(credentials, config, loadLm())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveSession(credentials: Credentials, config: LeagueConfig) {
        val json = JSONObject()
            .put("label", credentials.label)
            .put("swid", credentials.swid)
            .put("s2", credentials.s2)
            .put("leagueId", config.leagueId)
            .put("game", config.game.id)
            .put("season", config.season)
        prefs.edit().putString(KEY_SESSION, encrypt(json.toString())).apply()
    }

    fun loadLm(): Boolean = prefs.getBoolean(KEY_LM, false)

    fun saveLm(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LM, enabled).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = ByteArray(1 + iv.size + encrypted.size)
        packed[0] = iv.size.toByte()
        iv.copyInto(packed, 1)
        encrypted.copyInto(packed, 1 + iv.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLength = packed[0].toInt() and 0xFF
        val iv = packed.copyOfRange(1, 1 + ivLength)
        val ciphertext = packed.copyOfRange(1 + ivLength, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_SESSION = "session"
        private const val KEY_LM = "lm_enabled"
        private const val KEY_ALIAS = "league_desk_session_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
