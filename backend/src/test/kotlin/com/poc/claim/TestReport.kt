package com.poc.claim

import com.poc.claim.repository.ReportAuditLogRepository
import com.poc.claim.service.PdfSignerService
import com.poc.claim.service.PdfVerificationService
import net.sf.jasperreports.engine.JasperCompileManager
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.Security

class TestReport {
    @Test
    fun testCompileReport() {
        val file = File("src/main/resources/reports/claim_report.jrxml")
        file.inputStream().use {
            val report = JasperCompileManager.compileReport(it)
            println("Report compiled successfully with JR 7.1.0: ${report.name}")
            val params = mutableMapOf<String, Any?>(
                "reportId" to "TEST-UUID",
                "claimNumber" to "CLM-2026-0001",
                "policyNumber" to "POL-AXA-889120",
                "claimantName" to "สมชาย ใจดี",
                "incidentDate" to "2026-04-10",
                "incidentDescription" to "ทดสอบระบบ",
                "hospitalOrGarage" to "โรงพยาบาลกรุงเทพ",
                "version" to 1,
                "snapshotCreatedAt" to "2026-04-17 13:50:00",
                "totalAmount" to "53,700.00",
                "signerSubject" to "CN=OpenSource Insurance Claim Signer"
            )
            val dataSource = net.sf.jasperreports.engine.data.JRBeanCollectionDataSource(
                listOf(
                    mapOf(
                        "itemNo" to 1,
                        "description" to "ค่ารักษาพยาบาล",
                        "category" to "Medical",
                        "amountFormatted" to "53,700.00"
                    )
                )
            )
            val print = net.sf.jasperreports.engine.JasperFillManager.fillReport(report, params, dataSource)
            val pdfBytes = net.sf.jasperreports.engine.JasperExportManager.exportReportToPdf(print)
            println("Exported PDF successfully, bytes: ${pdfBytes.size}, pages: ${print.pages.size}")
            org.junit.jupiter.api.Assertions.assertEquals(2, print.pages.size)
            org.junit.jupiter.api.Assertions.assertTrue(pdfBytes.size > 1000)
        }
    }

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
        val repo = mock(ReportAuditLogRepository::class.java)
        val verifier = PdfVerificationService(repo)

        val doc = PDDocument()
        doc.addPage(PDPage())
        val baos = ByteArrayOutputStream()
        doc.save(baos)
        doc.close()

        val signedResult = signer.signPdfInMemory(baos.toByteArray(), "CLM-TEST", 1)
        val result = verifier.verifyPdfBytes(signedResult.signedPdfBytes)
        println("Result isValid: ${result.isValid}, message: ${result.message}")
    }
}
