package com.aura.mcp.lan

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Self-signed identity for the LAN server. Generated once, kept in memory for the server's
 * lifetime and exposed to the pairing QR only as a SHA-256 fingerprint.
 */
class ServerIdentity private constructor(
    val keyStore: KeyStore,
    val certificate: X509Certificate,
    val fingerprint: String,
) {
    companion object {
        const val ALIAS = "aura"
        const val PASSWORD = "aura-local-only"

        fun generate(): ServerIdentity {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val subject = X500Name("CN=AURA Local, O=AURA, C=US")
            val serial = BigInteger(64, SecureRandom()).abs()
            val builder = JcaX509v3CertificateBuilder(
                subject,
                serial,
                Date(now - 86_400_000L),
                Date(now + 3650L * 86_400_000L),
                subject,
                keyPair.publicKey,
            )
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(
                    arrayOf(
                        GeneralName(GeneralName.dNSName, "localhost"),
                        GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                    ),
                ),
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            cert.verify(keyPair.public)
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(ALIAS, keyPair.private, PASSWORD.toCharArray(), arrayOf(cert))
            }
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }
            return ServerIdentity(keyStore, cert, fingerprint)
        }
    }
}
