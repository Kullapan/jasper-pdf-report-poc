package com.poc.pdf.model

import java.math.BigDecimal

data class ClaimItemDto(
    val description: String,
    val category: String,
    val amount: BigDecimal
)

data class ClaimSnapshotPayload(
    val reportId: String,
    val claimNumber: String,
    val policyNumber: String,
    val claimantName: String,
    val incidentDate: String,
    val incidentDescription: String,
    val hospitalOrGarage: String,
    val version: Int,
    val snapshotCreatedAt: String,
    val items: List<ClaimItemDto>,
    val totalAmount: BigDecimal
)

data class RenderPdfRequest(
    val payload: ClaimSnapshotPayload,
    val signerSubject: String = "CN=Claim Report PoC Signer,OU=Digital Claims Architecture,O=OpenSource Insurance PoC,L=Bangkok,C=TH",
    val customClaimantSignatureBase64: String? = null,
    val customApproverSignatureBase64: String? = null
)
