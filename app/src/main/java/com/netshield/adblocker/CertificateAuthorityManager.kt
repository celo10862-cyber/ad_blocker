package com.netshield.adblocker

import android.content.Context
import android.content.Intent
import android.security.KeyChain
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** Creates a local CA identity. It does not intercept traffic or install trust silently. */
object CertificateAuthorityManager {
    private const val KEY_ALIAS = "netshield_local_ca_v1"
    private const val CERT_FILE = "netshield-local-ca.cer"

    @Synchronized
    fun ensureCreated(context: Context): File {
        val certFile = File(context.filesDir, CERT_FILE)
        if (certFile.exists() && loadPrivateKey() != null) return certFile

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=NetShield Local CA, OU=On-device, O=NetShield"))
                .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(Date(System.currentTimeMillis() - 60_000))
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000))
                .build()
        )
        val pair = generator.generateKeyPair()
        val subject = X500Name("CN=NetShield Local CA, OU=On-device, O=NetShield")
        val now = Date()
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject, BigInteger.valueOf(System.currentTimeMillis()), Date(now.time - 60_000),
            Date(now.time + 3650L * 24 * 60 * 60 * 1000), subject, pair.public
        )
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(pair.public))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(loadPrivateKey()!!)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        certificate.verify(pair.public)
        certFile.writeBytes(certificate.encoded)
        return certFile
    }

    private fun loadPrivateKey(): PrivateKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(KEY_ALIAS, null) as? PrivateKey
    }

    fun certificateExists(context: Context): Boolean =
        File(context.filesDir, CERT_FILE).exists() && loadPrivateKey() != null

    fun installIntent(context: Context): Intent {
        val file = ensureCreated(context)
        val certificate = file.inputStream().use { input ->
            java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(input) as X509Certificate
        }
        return KeyChain.createInstallIntent().apply {
            putExtra(KeyChain.EXTRA_CERTIFICATE, certificate.encoded)
            putExtra(KeyChain.EXTRA_NAME, "NetShield Local CA")
        }
    }
}
