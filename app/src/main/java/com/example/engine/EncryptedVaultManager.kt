package com.example.engine

import android.content.Context
import com.example.data.ExplorerDao
import com.example.data.VaultEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local AES-256-GCM encrypted file vault (`TFEV1` container format).
 * Shielded from Android MediaStore via `.nomedia` in app-private storage.
 */
class EncryptedVaultManager(
    private val context: Context,
    private val dao: ExplorerDao
) {
    companion object {
        private val MAGIC = "TFEV1".toByteArray(Charsets.US_ASCII)
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 65_536
    }

    private fun getVaultDir(): File {
        val dir = File(context.filesDir, "hidden_cabinet_vault")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
        return dir
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("TFE_VAULT_SALT_V1:$pin").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    suspend fun lockFileIntoVault(sourcePath: String, pin: String): Result<VaultEntryEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(sourcePath)
                require(src.exists() && src.isFile) { "Only regular files can be encrypted into the vault" }
                val vaultDir = getVaultDir()
                val vaultFileName = "v_${System.currentTimeMillis()}_${src.name.hashCode().toUInt()}.hcb"
                val dest = File(vaultDir, vaultFileName)

                val random = SecureRandom()
                val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
                val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
                val key = deriveKey(pin, salt)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

                FileOutputStream(dest).use { fos ->
                    fos.write(MAGIC)
                    fos.write(salt)
                    fos.write(iv)
                    CipherOutputStream(fos, cipher).use { cos ->
                        FileInputStream(src).use { fis ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (fis.read(buf).also { read = it } != -1) {
                                cos.write(buf, 0, read)
                            }
                        }
                    }
                }

                val size = src.length()
                // Remove plaintext original after verified encryption
                src.delete()

                val entity = VaultEntryEntity(
                    originalName = src.name,
                    originalPath = src.absolutePath,
                    vaultFileName = vaultFileName,
                    sizeBytes = size
                )
                val id = dao.insertVaultEntry(entity)
                entity.copy(id = id)
            }
        }

    suspend fun restoreFromVault(entry: VaultEntryEntity, pin: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val vaultFile = File(getVaultDir(), entry.vaultFileName)
                require(vaultFile.exists()) { "Encrypted vault container missing" }

                val originalFile = File(entry.originalPath)
                val parentDir = originalFile.parentFile?.takeIf { it.exists() || it.mkdirs() }
                    ?: context.getExternalFilesDir(null)
                    ?: context.filesDir
                val target = FileSystemEngine.resolveUniqueTarget(parentDir, entry.originalName)

                FileInputStream(vaultFile).use { fis ->
                    val magic = ByteArray(MAGIC.size)
                    require(fis.read(magic) == MAGIC.size && magic.contentEquals(MAGIC)) {
                        "Invalid vault container header"
                    }
                    val salt = ByteArray(SALT_LEN)
                    val iv = ByteArray(IV_LEN)
                    require(fis.read(salt) == SALT_LEN && fis.read(iv) == IV_LEN) {
                        "Corrupted vault metadata"
                    }
                    val key = deriveKey(pin, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

                    CipherInputStream(fis, cipher).use { cis ->
                        FileOutputStream(target).use { fos ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (cis.read(buf).also { read = it } != -1) {
                                fos.write(buf, 0, read)
                            }
                        }
                    }
                }

                vaultFile.delete()
                dao.deleteVaultEntry(entry.id)
                target
            }
        }

    suspend fun deleteVaultEntryPermanently(entry: VaultEntryEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                File(getVaultDir(), entry.vaultFileName).delete()
                dao.deleteVaultEntry(entry.id)
            }
        }
}
