package fr.gcu.jardsurmer.autoconnect.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import fr.gcu.jardsurmer.autoconnect.model.Credentials
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CredentialStore {
    private const val PREFS = "gcu_secure_credentials_v4"
    private const val DATA = "encrypted_data"
    private const val ALIAS = "fr.gcu.jardsurmer.autoconnect.credentials.v1"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    fun save(context: Context, credentials: Credentials) {
        if (!credentials.isComplete) {
            throw IllegalArgumentException("Identifiants incomplets")
        }
        val plain = serialize(credentials)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv

        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        output.writeInt(iv.size)
        output.write(iv)
        output.writeInt(encrypted.size)
        output.write(encrypted)
        output.close()

        val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        val committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(DATA, encoded).commit()
        if (!committed) throw IllegalStateException("Enregistrement impossible")
    }

    fun load(context: Context): Credentials? {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encoded = preferences.getString(DATA, null) ?: return null
        if (encoded.isEmpty()) return null

        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val input = DataInputStream(ByteArrayInputStream(packed))
        val ivLength = input.readInt()
        if (ivLength !in 8..32) throw IllegalStateException("Données chiffrées invalides")
        val iv = ByteArray(ivLength)
        input.readFully(iv)
        val dataLength = input.readInt()
        if (dataLength !in 1..(1024 * 1024)) throw IllegalStateException("Données chiffrées invalides")
        val encrypted = ByteArray(dataLength)
        input.readFully(encrypted)
        input.close()

        val store = KeyStore.getInstance(ANDROID_KEY_STORE)
        store.load(null)
        val key = store.getKey(ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Clé de chiffrement absente")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return deserialize(cipher.doFinal(encrypted))
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {}
        try {
            val store = KeyStore.getInstance(ANDROID_KEY_STORE)
            store.load(null)
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        } catch (_: Throwable) {}
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE)
        store.load(null)
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun serialize(credentials: Credentials): ByteArray {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        writeUtf8(output, credentials.username)
        writeUtf8(output, credentials.password)
        output.close()
        return bytes.toByteArray()
    }

    private fun deserialize(data: ByteArray): Credentials {
        val input = DataInputStream(ByteArrayInputStream(data))
        val username = readUtf8(input)
        val password = readUtf8(input)
        input.close()
        return Credentials(username, password)
    }

    private fun writeUtf8(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readUtf8(input: DataInputStream): String {
        val length = input.readInt()
        if (length !in 0..(1024 * 1024)) throw IllegalStateException("Données invalides")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}
