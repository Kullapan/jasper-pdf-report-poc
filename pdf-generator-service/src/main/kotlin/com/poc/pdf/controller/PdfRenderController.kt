package com.poc.pdf.controller

import com.poc.pdf.model.ClaimSnapshotPayload
import com.poc.pdf.model.RenderPdfRequest
import com.poc.pdf.service.JasperReportService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/pdf")
class PdfRenderController(
    private val jasperReportService: JasperReportService
) {

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "pdf-generator-service"))
    }

    @PostMapping("/render-claim-report", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun renderClaimReport(
        @RequestBody request: RenderPdfRequest
    ): ResponseEntity<ByteArray> {
        val pdfBytes = jasperReportService.generateUnsignedPdfBytes(request)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("X-Report-Pages", "2")
            .body(pdfBytes)
    }

    @PostMapping("/render-from-snapshot", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun renderFromSnapshot(
        @RequestBody snapshot: ClaimSnapshotPayload,
        @RequestParam(required = false) signerSubject: String?
    ): ResponseEntity<ByteArray> {
        val req = RenderPdfRequest(
            payload = snapshot,
            signerSubject = signerSubject ?: "CN=Claim Report PoC Signer,OU=Digital Claims Architecture,O=OpenSource Insurance PoC,L=Bangkok,C=TH"
        )
        val pdfBytes = jasperReportService.generateUnsignedPdfBytes(req)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("X-Report-Pages", "2")
            .body(pdfBytes)
    }
}
