package com.poc.signing.controller

import com.poc.signing.model.CertificateInfoDto
import com.poc.signing.model.SignatureVerificationResult
import com.poc.signing.service.PdfSignerService
import com.poc.signing.service.PdfVerificationService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/v1/signatures")
class PdfSignerController(
    private val pdfSignerService: PdfSignerService,
    private val pdfVerificationService: PdfVerificationService
) {

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "digital-signing-service"))
    }

    @GetMapping("/certificate-info")
    fun getCertificateInfo(): ResponseEntity<CertificateInfoDto> {
        return ResponseEntity.ok(pdfSignerService.getCertificateInfo())
    }

    @PostMapping("/sign", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_PDF_VALUE])
    fun signPdfDirect(
        @RequestBody rawPdfBytes: ByteArray,
        @RequestHeader(value = "X-Claim-Number", defaultValue = "CLAIM-DOC") claimNumber: String,
        @RequestHeader(value = "X-Claim-Version", defaultValue = "1") version: Int,
        @RequestHeader(value = "X-Sign-Reason", required = false) reason: String?
    ): ResponseEntity<ByteArray> {
        val outcome = pdfSignerService.signPdfInMemory(
            unsignedPdfBytes = rawPdfBytes,
            claimNumber = claimNumber,
            version = version,
            customReason = reason
        )

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("X-Report-SHA256", outcome.sha256Checksum)
            .header("X-Signer-Subject", outcome.signerSubject)
            .body(outcome.signedPdfBytes)
    }

    @PostMapping("/sign-multipart", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun signPdfMultipart(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "claimNumber", defaultValue = "CLAIM-DOC") claimNumber: String,
        @RequestParam(value = "version", defaultValue = "1") version: Int,
        @RequestParam(value = "reason", required = false) reason: String?
    ): ResponseEntity<ByteArray> {
        val outcome = pdfSignerService.signPdfInMemory(
            unsignedPdfBytes = file.bytes,
            claimNumber = claimNumber,
            version = version,
            customReason = reason
        )

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("X-Report-SHA256", outcome.sha256Checksum)
            .header("X-Signer-Subject", outcome.signerSubject)
            .body(outcome.signedPdfBytes)
    }

    @PostMapping("/verify", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun verifyPdfMultipart(@RequestParam("file") file: MultipartFile): ResponseEntity<SignatureVerificationResult> {
        val result = pdfVerificationService.verifyPdfBytes(file.bytes)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/verify-direct", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_PDF_VALUE])
    fun verifyPdfDirect(@RequestBody pdfBytes: ByteArray): ResponseEntity<SignatureVerificationResult> {
        val result = pdfVerificationService.verifyPdfBytes(pdfBytes)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/tamper-demo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun tamperDemoMultipart(@RequestParam("file") file: MultipartFile): ResponseEntity<ByteArray> {
        val tamperedBytes = pdfVerificationService.createTamperedCopy(file.bytes)
        val originalName = file.originalFilename?.removeSuffix(".pdf") ?: "signed-doc"
        val contentDisposition = ContentDisposition.builder("attachment")
            .filename("${originalName}-TAMPERED.pdf", StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .body(tamperedBytes)
    }

    @PostMapping("/tamper-demo-direct", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_PDF_VALUE])
    fun tamperDemoDirect(@RequestBody pdfBytes: ByteArray): ResponseEntity<ByteArray> {
        val tamperedBytes = pdfVerificationService.createTamperedCopy(pdfBytes)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .body(tamperedBytes)
    }
}
