package com.poc.claim.controller

import com.poc.claim.model.ClaimDetailResponse
import com.poc.claim.model.ClaimSummaryResponse
import com.poc.claim.model.CreateOrUpdateClaimRequest
import com.poc.claim.service.ClaimReportService
import com.poc.claim.service.PdfVerificationService
import com.poc.claim.service.SignatureVerificationResponse
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/reports")
class ClaimReportController(
    private val claimReportService: ClaimReportService,
    private val pdfVerificationService: PdfVerificationService
) {

    @GetMapping
    fun listAllClaims(): ResponseEntity<List<ClaimSummaryResponse>> {
        return ResponseEntity.ok(claimReportService.listClaims())
    }

    @PostMapping("/claims")
    fun createClaim(@RequestBody request: CreateOrUpdateClaimRequest): ResponseEntity<ClaimDetailResponse> {
        val created = claimReportService.createClaim(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PostMapping("/{reportId}/versions")
    fun createNewVersion(
        @PathVariable reportId: UUID,
        @RequestBody request: CreateOrUpdateClaimRequest
    ): ResponseEntity<ClaimDetailResponse> {
        val updated = claimReportService.createNewVersion(reportId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(updated)
    }

    @GetMapping("/{reportId}")
    fun getReportDetail(@PathVariable reportId: UUID): ResponseEntity<ClaimDetailResponse> {
        return ResponseEntity.ok(claimReportService.getClaimDetail(reportId))
    }

    /**
     * Generates and digitally signs the PDF on-the-fly in memory from the immutable version snapshot.
     * Both Preview (PDF.js via single Blob fetch) and Download consume this exact signed byte stream.
     */
    @GetMapping("/{reportId}/content")
    fun streamSignedReportOnTheFly(
        @PathVariable reportId: UUID,
        @RequestParam(required = false) version: Int?,
        @RequestParam(defaultValue = "false") download: Boolean
    ): ResponseEntity<ByteArray> {
        val (signedOutcome, payload) = claimReportService.generateAndSignOnTheFly(reportId, version)

        val filename = "claim-${payload.claimNumber}-v${payload.version}-signed.pdf"
        val dispositionType = if (download) "attachment" else "inline"
        val contentDisposition = ContentDisposition.builder(dispositionType)
            .filename(filename, StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .header("X-Report-Version", payload.version.toString())
            .header("X-Report-SHA256", signedOutcome.sha256Checksum)
            .body(signedOutcome.signedPdfBytes)
    }

    /**
     * Independent verification endpoint: accepts any PDF file and verifies its detached CMS signature
     * and SHA-256 ByteRange integrity without needing the private key.
     */
    @PostMapping("/verify", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun verifyUploadedPdf(@RequestParam("file") file: MultipartFile): ResponseEntity<SignatureVerificationResponse> {
        val result = pdfVerificationService.verifyPdfBytes(file.bytes)
        return ResponseEntity.ok(result)
    }

    /**
     * Helper endpoint for PoC tamper testing: accepts a signed PDF file and returns a 1-byte tampered copy
     * so users can test how `/api/reports/verify`, Adobe Acrobat Reader, or pyHanko detect post-signing modification.
     */
    @PostMapping("/tamper-demo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createTamperedPdfForDemo(@RequestParam("file") file: MultipartFile): ResponseEntity<ByteArray> {
        val tamperedBytes = pdfVerificationService.createTamperedCopy(file.bytes)
        val originalName = file.originalFilename?.removeSuffix(".pdf") ?: "claim-report"
        val contentDisposition = ContentDisposition.builder("attachment")
            .filename("${originalName}-TAMPERED.pdf", StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .body(tamperedBytes)
    }
}
