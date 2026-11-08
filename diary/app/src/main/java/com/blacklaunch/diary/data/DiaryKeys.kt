package com.blacklaunch.diary.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Ключ щоденника = HMAC(ключ у сховищі ключів телефона, PBKDF2(код, сіль)).
 *
 * Ключ у сховищі не можна витягти з телефона, тож навіть скопіювавши файли, код із 4 цифр не перебереш на
 * комп'ютері — лише на самому телефоні, а там спроби сповільнює програма. Без коду (замок не поставлено) щоденник
 * усе одно зашифрований — тим самим ключем телефона.
 */
object DiaryKeys {
    private const val STORE = "AndroidKeyStore"
    private const val ITERATIONS = 40_000

    fun passphrase(id: String, pin: String, salt: ByteArray): ByteArray {
        val stretched = pbkdf2(pin, salt, ITERATIONS)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(deviceKey(id))
        return mac.doFinal(stretched)
    }

    fun pbkdf2(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        // Порожній код теж має давати ключ (PBEKeySpec не любить порожній пароль на деяких версіях).
        val chars = ("diary:" + secret).toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun deviceKey(id: String): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(alias(id), null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, STORE)
        gen.init(KeyGenParameterSpec.Builder(alias(id), KeyProperties.PURPOSE_SIGN).build())
        return gen.generateKey()
    }

    fun deleteKey(id: String) {
        runCatching { KeyStore.getInstance(STORE).apply { load(null) }.deleteEntry(alias(id)) }
    }

    private fun alias(id: String) = "diary_$id"
}
