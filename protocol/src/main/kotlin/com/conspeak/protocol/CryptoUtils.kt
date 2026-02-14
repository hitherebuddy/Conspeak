package com.conspeak.protocol

import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object CryptoUtils {

    const val DEFAULT_PORT = 29170

    /**
     * Generate a self-signed certificate and return an SSLContext configured with it.
     * Returns Pair(sslContext, certificateFingerprint).
     */
    fun createSelfSignedSslContext(): Pair<SSLContext, String> {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        // Create a self-signed X509 certificate using the JDK internal API
        // In production, use Bouncy Castle for cleaner cert generation.
        // For now, we use a KeyStore-based approach.
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)

        // We'll use a helper to generate a self-signed cert
        val cert = generateSelfSignedCert(keyPair)
        keyStore.setKeyEntry("conspeak", keyPair.private, "conspeak".toCharArray(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "conspeak".toCharArray())

        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(kmf.keyManagers, arrayOf(AcceptAllTrustManager()), SecureRandom())

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
     * Generate a simple self-signed X509 certificate.
     * Uses sun.security.x509 internals available in JDK 17+.
     * For broader compatibility, replace with Bouncy Castle.
     */
    private fun generateSelfSignedCert(keyPair: java.security.KeyPair): X509Certificate {
        // Use the keytool-like approach via CertificateFactory
        // Simplified: generate via sun.security.* (available in Oracle/OpenJDK)
        val dn = "CN=Conspeak,O=Conspeak,L=Local"
        val validDays = 3650L

        @Suppress("UNCHECKED_CAST")
        val certGenClass = Class.forName("sun.security.x509.X509CertImpl")
        val certInfoClass = Class.forName("sun.security.x509.X509CertInfo")
        val x500NameClass = Class.forName("sun.security.x509.X500Name")
        val algIdClass = Class.forName("sun.security.x509.AlgorithmId")
        val certValidityClass = Class.forName("sun.security.x509.CertificateValidity")
        val certSerialClass = Class.forName("sun.security.x509.CertificateSerialNumber")
        val certSubjClass = Class.forName("sun.security.x509.CertificateSubjectName")
        val certIssuerClass = Class.forName("sun.security.x509.CertificateIssuerName")
        val certKeyClass = Class.forName("sun.security.x509.CertificateX509Key")
        val certAlgClass = Class.forName("sun.security.x509.CertificateAlgorithmId")
        val certVersionClass = Class.forName("sun.security.x509.CertificateVersion")

        val from = Date()
        val to = Date(from.time + validDays * 86400_000L)

        val sn = java.math.BigInteger(64, SecureRandom())

        val owner = x500NameClass.getConstructor(String::class.java).newInstance(dn)
        val validity = certValidityClass.getConstructor(Date::class.java, Date::class.java)
            .newInstance(from, to)

        val info = certInfoClass.getConstructor().newInstance()
        val setMethod = certInfoClass.getMethod("set", String::class.java, Any::class.java)

        setMethod.invoke(info, "validity", validity)
        setMethod.invoke(
            info, "serialNumber",
            certSerialClass.getConstructor(java.math.BigInteger::class.java).newInstance(sn)
        )
        setMethod.invoke(
            info, "subject",
            certSubjClass.getConstructor(x500NameClass).newInstance(owner)
        )
        setMethod.invoke(
            info, "issuer",
            certIssuerClass.getConstructor(x500NameClass).newInstance(owner)
        )
        setMethod.invoke(
            info, "key",
            certKeyClass.getConstructor(java.security.PublicKey::class.java)
                .newInstance(keyPair.public)
        )
        setMethod.invoke(
            info, "version",
            certVersionClass.getConstructor(Int::class.java)
                .newInstance(2) // v3
        )

        val algo = algIdClass.getMethod("get", String::class.java)
            .invoke(null, "SHA256withRSA")
        setMethod.invoke(
            info, "algorithmID",
            certAlgClass.getConstructor(algIdClass).newInstance(algo)
        )

        val cert = certGenClass.getConstructor(certInfoClass).newInstance(info)
        val signMethod = certGenClass.getMethod(
            "sign",
            java.security.PrivateKey::class.java,
            String::class.java
        )
        signMethod.invoke(cert, keyPair.private, "SHA256withRSA")

        return cert as X509Certificate
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
}
