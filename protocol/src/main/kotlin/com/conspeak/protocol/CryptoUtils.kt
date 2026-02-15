package com.conspeak.protocol

import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object CryptoUtils {

    const val DEFAULT_PORT = 29170

    /**
     * Generate a self-signed certificate and return an SSLContext configured with it.
     * Returns Pair(sslContext, certificateFingerprint).
     *
     * Uses the JDK's `keytool` CLI to generate the self-signed cert, avoiding
     * dependency on internal sun.security.x509 classes which are restricted in JDK 17+.
     */
    fun createSelfSignedSslContext(trustedPeerFingerprint: String? = null): Pair<SSLContext, String> {
        val password = "conspeak"
        val alias = "conspeak"
        val tempFile = File.createTempFile("conspeak-ks-", ".p12")
        tempFile.delete() // keytool needs the file to not exist
        tempFile.deleteOnExit()

        // Generate keystore with self-signed cert using keytool
        val keytoolProcess = ProcessBuilder(
            "keytool",
            "-genkeypair",
            "-alias", alias,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-sigalg", "SHA256withRSA",
            "-validity", "3650",
            "-storetype", "PKCS12",
            "-keystore", tempFile.absolutePath,
            "-storepass", password,
            "-keypass", password,
            "-dname", "CN=Conspeak,O=Conspeak,L=Local"
        ).redirectErrorStream(true).start()

        val keytoolOutput = keytoolProcess.inputStream.use { it.readBytes() }
        val exitCode = keytoolProcess.waitFor()
        keytoolProcess.destroyForcibly() // ensure process cleanup
        if (exitCode != 0) {
            tempFile.delete()
            throw RuntimeException("keytool failed with exit code $exitCode: ${String(keytoolOutput)}")
        }

        // Load the generated keystore
        val keyStore = KeyStore.getInstance("PKCS12")
        tempFile.inputStream().use { keyStore.load(it, password.toCharArray()) }

        // Clean up temp file
        tempFile.delete()

        val cert = keyStore.getCertificate(alias) as X509Certificate

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password.toCharArray())

        // Use pinning trust manager if we have a trusted fingerprint, otherwise accept all
        val trustManager: X509TrustManager = if (trustedPeerFingerprint != null) {
            PinningTrustManager(trustedPeerFingerprint)
        } else {
            AcceptAllTrustManager()
        }

        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(kmf.keyManagers, arrayOf(trustManager), SecureRandom())

        val fingerprint = getCertFingerprint(cert)
        return Pair(sslContext, fingerprint)
    }

    /**
     * Create an SSLContext that accepts all certificates (for initial pairing).
     * After pairing, certificate pinning should be enforced.
     */
    fun createAcceptAllSslContext(): SSLContext {
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(null, arrayOf(AcceptAllTrustManager()), SecureRandom())
        return sslContext
    }

    /**
     * Get SHA-256 fingerprint of a certificate as hex string.
     */
    fun getCertFingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Derive a 6-digit pairing code from two fingerprints.
     */
    fun derivePairingCode(fp1: String, fp2: String): String {
        val combined = if (fp1 < fp2) "$fp1$fp2" else "$fp2$fp1"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        val num = ((hash[0].toLong() and 0xFF) shl 16) or
                ((hash[1].toLong() and 0xFF) shl 8) or
                (hash[2].toLong() and 0xFF)
        return "%06d".format(num % 1_000_000)
    }

    /**
     * Create an SSLContext that pins a specific certificate fingerprint.
     * Used for reconnecting to a previously paired peer.
     */
    fun createPinnedSslContext(
        keyStore: KeyStore? = null,
        keyPassword: CharArray? = null,
        trustedFingerprint: String
    ): SSLContext {
        val sslContext = SSLContext.getInstance("TLSv1.3")
        val kmf = if (keyStore != null) {
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).also {
                it.init(keyStore, keyPassword)
            }
        } else null
        sslContext.init(
            kmf?.keyManagers,
            arrayOf(PinningTrustManager(trustedFingerprint)),
            SecureRandom()
        )
        return sslContext
    }

    /**
     * Trust manager that accepts all certificates.
     * Used during initial pairing; after pairing, pin the specific cert.
     */
    private class AcceptAllTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /**
     * Trust manager that only accepts certificates matching a pinned fingerprint.
     * Used after pairing to prevent MITM attacks on reconnection.
     */
    class PinningTrustManager(private val trustedFingerprint: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            checkPinned(chain)
        }
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            checkPinned(chain)
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

        private fun checkPinned(chain: Array<out X509Certificate>?) {
            if (chain.isNullOrEmpty()) {
                throw java.security.cert.CertificateException("No certificates provided")
            }
            val peerFingerprint = getCertFingerprint(chain[0])
            if (peerFingerprint != trustedFingerprint) {
                throw java.security.cert.CertificateException(
                    "Certificate fingerprint mismatch: expected ${trustedFingerprint.take(16)}..., got ${peerFingerprint.take(16)}..."
                )
            }
        }
    }
}
