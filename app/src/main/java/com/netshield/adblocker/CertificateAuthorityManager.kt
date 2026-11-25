package com.netshield.adblocker

import android.content.Context
import android.content.Intent
import android.security.KeyChain
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Creates a user-consented local CA certificate. The RSA private key never leaves
 * Android Keystore. This certificate manager does not itself intercept TLS traffic.
 */
object CertificateAuthorityManager {
    private const val PREFS = "netshield"
    private const val CERT_KEY = "local_ca_certificate"
    private const val KEY_ALIAS = "netshield_local_ca_v1"

    fun createInstallIntent(context: Context): Intent {
        val certificateBytes = getOrCreateCertificate(context)
        return KeyChain.createInstallIntent().apply {
            putExtra(KeyChain.EXTRA_CERTIFICATE, certificateBytes)
        }
    }

    fun hasCertificate(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(CERT_KEY)

    private fun getOrCreateCertificate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(CERT_KEY, null)
        if (saved != null) return Base64.decode(saved, Base64.DEFAULT)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyPair = if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            java.security.KeyPair(publicKey, privateKey)
        } else {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build()
            )
            generator.generateKeyPair()
        }

        val subject = X500Name("CN=NetShield Local Filtering CA, OU=NetShield, O=NetShield")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(159, SecureRandom()).abs().add(BigInteger.ONE),
            Date(now - 60_000L),
            Date(now + 3650L * 24 * 60 * 60 * 1000),
            subject,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        certificate.checkValidity(Date())
        certificate.verify(keyPair.public)
        val encoded = certificate.encoded
        prefs.edit().putString(CERT_KEY, Base64.encodeToString(encoded, Base64.NO_WRAP)).apply()
        return encoded
    }
}
