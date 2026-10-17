package com.poc.claim.client

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient

data class SignedPdfResponseDto(
    val signedPdfBytes: ByteArray,
    val sha256Checksum: String,
    val signerSubject: String
)

data class CertificateInfoDto(
    val subjectDN: String,
    val issuerDN: String,
    val serialNumberHex: String,
    val validFrom: String,
    val validUntil: String
)

data class SignatureVerificationResponse(
    @get:JsonProperty("isValid")
    val isValid: Boolean,
    @get:JsonProperty("isTampered")
    val isTampered: Boolean,
    val signatureFound: Boolean,
    val filter: String?,
    val subFilter: String?,
    val digestAlgorithmOid: String?,
    val signerSubject: String?,
    val signerIssuer: String?,
    val serialNumberHex: String?,
    val signDate: String?,
    val reason: String?,
    val byteRange: List<Int>?,
    val coversWholeDocument: Boolean,
    val fileSha256: String,
    val matchedAuditLog: Boolean = false,
    val matchedReportVersion: Int? = null,
    val selfSignedNotice: String,
    val message: String
)

@Component
class SigningServiceClient(
    @Value("\${services.signing.url:http://localhost:8081}") private val baseUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    fun getCertificateInfo(): CertificateInfoDto? {
        return try {
            restClient.get()
                .uri("/api/v1/signatures/certificate-info")
                .retrieve()
                .body(CertificateInfoDto::class.java)
        } catch (ex: Exception) {
            println("Warning: could not fetch certificate info from signing-service: ${ex.message}")
            null
        }
    }

    fun signPdf(
        unsignedPdfBytes: ByteArray,
        claimNumber: String,
        version: Int,
        reason: String? = null
    ): SignedPdfResponseDto {
        val response = restClient.post()
            .uri("/api/v1/signatures/sign")
            .contentType(MediaType.APPLICATION_PDF)
            .header("X-Claim-Number", claimNumber)
            .header("X-Claim-Version", version.toString())
            .apply {
                if (reason != null) header("X-Sign-Reason", reason)
            }
            .body(unsignedPdfBytes)
            .retrieve()
            .toEntity(ByteArray::class.java)

        val signedBytes = response.body ?: throw IllegalStateException("Empty body from signing-service")
        val sha256 = response.headers.getFirst("X-Report-SHA256") ?: ""
        val signer = response.headers.getFirst("X-Signer-Subject") ?: ""

        return SignedPdfResponseDto(
            signedPdfBytes = signedBytes,
            sha256Checksum = sha256,
            signerSubject = signer
        )
    }

    fun verifyPdf(pdfBytes: ByteArray): SignatureVerificationResponse {
        return restClient.post()
            .uri("/api/v1/signatures/verify-direct")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
            .retrieve()
            .body(SignatureVerificationResponse::class.java)
            ?: throw IllegalStateException("Empty verification response from signing-service")
    }

    fun tamperPdf(signedPdfBytes: ByteArray): ByteArray {
        return restClient.post()
            .uri("/api/v1/signatures/tamper-demo-direct")
            .contentType(MediaType.APPLICATION_PDF)
            .body(signedPdfBytes)
            .retrieve()
            .body(ByteArray::class.java)
            ?: throw IllegalStateException("Empty tampered PDF response from signing-service")
    }
}
