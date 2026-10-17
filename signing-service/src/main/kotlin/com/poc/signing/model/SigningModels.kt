package com.poc.signing.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SignedPdfOutcome(
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

data class SignatureVerificationResult(
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
    val selfSignedNotice: String,
    val message: String
)
