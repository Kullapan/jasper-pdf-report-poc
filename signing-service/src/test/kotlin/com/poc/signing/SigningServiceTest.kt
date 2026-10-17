package com.poc.signing

import com.poc.signing.service.PdfSignerService
import com.poc.signing.service.PdfVerificationService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.security.Security

class SigningServiceTest {

    @Test
    fun testSignAndVerify() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val signer = PdfSignerService(
            keystorePath = "../certs/keystore.p12",
            keystorePassword = "changeit_local_poc",
            keystoreAlias = "claimsigner"
        )
        val verifier = PdfVerificationService()

        val doc = PDDocument()
        doc.addPage(PDPage())
        val baos = ByteArrayOutputStream()
        doc.save(baos)
        doc.close()

        val rawPdf = baos.toByteArray()
        val signedOutcome = signer.signPdfInMemory(rawPdf, "CLM-TEST-001", 1)

        val verifyResult = verifier.verifyPdfBytes(signedOutcome.signedPdfBytes)
        assertTrue(verifyResult.isValid)
        assertFalse(verifyResult.isTampered)
        assertTrue(verifyResult.coversWholeDocument)
        println("SigningServiceTest PASSED. Signer Subject: ${signedOutcome.signerSubject}")
    }
}
