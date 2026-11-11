package househunt.publish

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val PBKDF2_ITERATIONS = 600_000
private const val KEY_BITS = 256
private const val SALT_BYTES = 16
private const val IV_BYTES = 12
private const val GCM_TAG_BITS = 128

/** Everything the browser needs besides the password. `data` is ciphertext followed by the GCM tag. */
@Serializable
data class EncryptedPayload(
    val version: Int,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
)

fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
    val specification = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
    val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded
    return SecretKeySpec(keyBytes, "AES")
}

fun encryptText(
    plainText: String,
    password: String,
    random: SecureRandom = SecureRandom(),
    iterations: Int = PBKDF2_ITERATIONS,
): EncryptedPayload {
    val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
    val iv = ByteArray(IV_BYTES).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val encoder = Base64.getEncoder()
    return EncryptedPayload(1, iterations, encoder.encodeToString(salt), encoder.encodeToString(iv), encoder.encodeToString(encrypted))
}

fun decryptText(payload: EncryptedPayload, password: String): String {
    val decoder = Base64.getDecoder()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val key = deriveKey(password, decoder.decode(payload.salt), payload.iterations)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, decoder.decode(payload.iv)))
    return String(cipher.doFinal(decoder.decode(payload.data)), Charsets.UTF_8)
}

fun encodePayloadAsJson(payload: EncryptedPayload): String =
    Json.encodeToString(EncryptedPayload.serializer(), payload)
