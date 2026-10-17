package com.poc.claim.client

import com.poc.claim.model.ClaimSnapshotPayload
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class RenderPdfRequestDto(
    val payload: ClaimSnapshotPayload,
    val signerSubject: String,
    val customClaimantSignatureBase64: String? = null,
    val customApproverSignatureBase64: String? = null
)

@Component
class PdfGeneratorClient(
    @Value("\${services.pdf-generator.url:http://localhost:8082}") private val baseUrl: String
) {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    fun renderClaimReport(
        payload: ClaimSnapshotPayload,
        signerSubject: String = "CN=Claim Report PoC Signer,OU=Digital Claims Architecture,O=OpenSource Insurance PoC,L=Bangkok,C=TH",
        claimantSigBase64: String? = null,
        approverSigBase64: String? = null
    ): ByteArray {
        val request = RenderPdfRequestDto(
            payload = payload,
            signerSubject = signerSubject,
            customClaimantSignatureBase64 = claimantSigBase64,
            customApproverSignatureBase64 = approverSigBase64
        )

        return restClient.post()
            .uri("/api/v1/pdf/render-claim-report")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ByteArray::class.java)
            ?: throw IllegalStateException("Received empty PDF response from pdf-generator-service")
    }
}
