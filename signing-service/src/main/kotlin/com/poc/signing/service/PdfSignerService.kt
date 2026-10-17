package com.poc.signing.service

import com.poc.signing.model.CertificateInfoDto
import com.poc.signing.model.SignedPdfOutcome
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar

@Service
class PdfSignerService(
    @Value("\${keystore.path}") private val keystorePath: String,
    @Value("\${keystore.password}") private val keystorePassword: String,
    @Value("\${keystore.alias}") private val keystoreAlias: String
) {
    private val privateKey: PrivateKey
    private val certificateChain: List<X509Certificate>
    final val signerSubjectDN: String

    init {
        val resolvedFile = resolveKeystoreFile(keystorePath)
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(resolvedFile).use { fis ->
            keyStore.load(fis, keystorePassword.toCharArray())
        }

        val key = keyStore.getKey(keystoreAlias, keystorePassword.toCharArray())
            ?: throw IllegalStateException("Alias '$keystoreAlias' not found in PKCS#12 keystore")
        this.privateKey = key as PrivateKey

        val rawChain = keyStore.getCertificateChain(keystoreAlias)
            ?: throw IllegalStateException("Certificate chain not found for alias '$keystoreAlias'")
        this.certificateChain = rawChain.map { it as X509Certificate }
        this.signerSubjectDN = certificateChain.first().subjectX500Principal.name
    }

    private fun resolveKeystoreFile(configuredPath: String): File {
        val candidates = listOf(
            File(configuredPath),
            File("../certs/keystore.p12"),
            File("./certs/keystore.p12"),
            File("/certs/keystore.p12")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: throw IllegalStateException(
                "PKCS#12 keystore file not found at '$configuredPath'. Run setup-keystore.ps1 or setup-keystore.sh first."
            )
    }

    fun getCertificateInfo(): CertificateInfoDto {
        val cert = certificateChain.first()
        return CertificateInfoDto(
            subjectDN = cert.subjectX500Principal.name,
            issuerDN = cert.issuerX500Principal.name,
            serialNumberHex = cert.serialNumber.toString(16).uppercase(),
            validFrom = cert.notBefore.toInstant().toString(),
            validUntil = cert.notAfter.toInstant().toString()
        )
    }

    fun signPdfInMemory(
        unsignedPdfBytes: ByteArray,
        claimNumber: String,
        version: Int,
        customReason: String? = null
    ): SignedPdfOutcome {
        Loader.loadPDF(unsignedPdfBytes).use { document ->
            val signature = PDSignature().apply {
                setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                name = signerSubjectDN
                location = "Bangkok, Thailand (Isolated Signing Service)"
                reason = customReason ?: "Official Claim Report ($claimNumber v$version) - Detached CMS SHA-256"
                signDate = Calendar.getInstance()
            }

            // Invisible signature: register PDSignature + SignatureInterface without visual widget
            val signatureInterface = SignatureInterface { contentStream ->
                createDetachedCmsSignature(contentStream)
            }

            document.addSignature(signature, signatureInterface)

            val signedBytes = ByteArrayOutputStream().use { output ->
                document.saveIncremental(output)
                output.toByteArray()
            }

            return SignedPdfOutcome(
                signedPdfBytes = signedBytes,
                sha256Checksum = sha256Hex(signedBytes),
                signerSubject = signerSubjectDN
            )
        }
    }

    private fun createDetachedCmsSignature(content: InputStream): ByteArray {
        val contentBytes = content.readAllBytes()
        val certStore = JcaCertStore(certificateChain)
        val signerCert = certificateChain.first()

        val signerInfoGenerator = JcaSimpleSignerInfoGeneratorBuilder()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build("SHA256withRSA", privateKey, signerCert)

        val cmsGenerator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(signerInfoGenerator)
            addCertificates(certStore)
        }

        // encapsulate = false -> detached CMS signature (/adbe.pkcs7.detached)
        val cmsSignedData = cmsGenerator.generate(CMSProcessableByteArray(contentBytes), false)
        return cmsSignedData.encoded
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
