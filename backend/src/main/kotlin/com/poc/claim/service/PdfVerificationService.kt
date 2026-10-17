package com.poc.claim.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.poc.claim.repository.ReportAuditLogRepository
import org.apache.pdfbox.Loader
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.HexFormat

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
    val matchedAuditLog: Boolean,
    val matchedReportVersion: Int?,
    val selfSignedNotice: String,
    val message: String
)

@Service
class PdfVerificationService(
    private val auditLogRepository: ReportAuditLogRepository
) {

    fun verifyPdfBytes(pdfBytes: ByteArray): SignatureVerificationResponse {
        val fileSha256 = MessageDigest.getInstance("SHA-256")
            .digest(pdfBytes)
            .joinToString("") { "%02x".format(it) }

        val auditMatches = auditLogRepository.findBySha256Checksum(fileSha256)
        val matchedAudit = auditMatches.firstOrNull()

        return try {
            Loader.loadPDF(pdfBytes).use { document ->
                val signatures = document.signatureDictionaries
                if (signatures.isEmpty()) {
                    return SignatureVerificationResponse(
                        isValid = false,
                        isTampered = false,
                        signatureFound = false,
                        filter = null,
                        subFilter = null,
                        digestAlgorithmOid = null,
                        signerSubject = null,
                        signerIssuer = null,
                        serialNumberHex = null,
                        signDate = null,
                        reason = null,
                        byteRange = null,
                        coversWholeDocument = false,
                        fileSha256 = fileSha256,
                        matchedAuditLog = false,
                        matchedReportVersion = null,
                        selfSignedNotice = "No signature present in PDF.",
                        message = "ไม่พบลายเซ็นดิจิทัล (PDSignature) ในไฟล์ PDF นี้"
                    )
                }

                val sig = signatures.first()
                val byteRangeArray = sig.byteRange
                val byteRangeList = byteRangeArray?.toList() ?: emptyList()

                // Check if ByteRange covers from offset 0 to exact end of file
                val coversWholeDoc = if (byteRangeList.size == 4) {
                    val start1 = byteRangeList[0]
                    val end2 = byteRangeList[2] + byteRangeList[3]
                    start1 == 0 && end2 == pdfBytes.size
                } else {
                    false
                }

                val cmsContents = sig.getContents(pdfBytes)
                val signedRangeBytes = sig.getSignedContent(pdfBytes)

                val contentInfo = org.bouncycastle.asn1.ASN1InputStream(cmsContents).use { asn1In ->
                    org.bouncycastle.asn1.cms.ContentInfo.getInstance(asn1In.readObject())
                }
                val cms = CMSSignedData(CMSProcessableByteArray(signedRangeBytes), contentInfo)
                val signerInfo = cms.signerInfos.signers.first()

                @Suppress("UNCHECKED_CAST")
                val selector = signerInfo.sid as org.bouncycastle.util.Selector<X509CertificateHolder>
                val certMatches = cms.certificates.getMatches(selector)
                val certHolder = certMatches.first()
                val x509Cert = JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(certHolder)

                val verifier = JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(x509Cert)

                val cryptoValid = signerInfo.verify(verifier)
                val overallValid = cryptoValid && coversWholeDoc

                SignatureVerificationResponse(
                    isValid = overallValid,
                    isTampered = !overallValid,
                    signatureFound = true,
                    filter = sig.filter,
                    subFilter = sig.subFilter,
                    digestAlgorithmOid = signerInfo.digestAlgOID,
                    signerSubject = x509Cert.subjectX500Principal.name,
                    signerIssuer = x509Cert.issuerX500Principal.name,
                    serialNumberHex = x509Cert.serialNumber.toString(16).uppercase(),
                    signDate = sig.signDate?.toInstant()?.toString(),
                    reason = sig.reason,
                    byteRange = byteRangeList,
                    coversWholeDocument = coversWholeDoc,
                    fileSha256 = fileSha256,
                    matchedAuditLog = matchedAudit != null,
                    matchedReportVersion = matchedAudit?.version,
                    selfSignedNotice = "Self-Signed Certificate (PoC): ลายเซ็นตรวจสอบความสมบูรณ์ (Integrity) ผ่านตามมาตรฐาน CMS/SHA-256 แต่โปรแกรมอ่าน PDF ภายนอกจะแจ้งเตือนว่ายังไม่รู้จักผู้ออกใบรับรอง (Untrusted Root CA) จนกว่าจะติดตั้งใบรับรองใน Trust Store",
                    message = if (overallValid) {
                        "ตรวจสอบสำเร็จ: ลายเซ็นดิจิทัลถูกต้อง (VALID) และเอกสารไม่ถูกแก้ไขหลังการลงนาม"
                    } else if (!cryptoValid) {
                        "ตรวจพบการปลอมแปลง (INVALID): เนื้อหาในไฟล์ถูกแก้ไขหลังการลงนาม (Cryptographic Hash Mismatch)"
                    } else {
                        "ตรวจพบความผิดปกติ (INVALID): ByteRange ไม่ครอบคลุมทั้งเอกสาร (พบข้อมูลต่อท้ายไฟล์หลังลงนาม)"
                    }
                )
            }
        } catch (ex: Exception) {
            println("=== PDF VERIFICATION EXCEPTION ===")
            ex.printStackTrace()
            SignatureVerificationResponse(
                isValid = false,
                isTampered = true,
                signatureFound = true,
                filter = null,
                subFilter = null,
                digestAlgorithmOid = null,
                signerSubject = null,
                signerIssuer = null,
                serialNumberHex = null,
                signDate = null,
                reason = null,
                byteRange = null,
                coversWholeDocument = false,
                fileSha256 = fileSha256,
                matchedAuditLog = false,
                matchedReportVersion = null,
                selfSignedNotice = "Verification failed due to corrupted PDF or invalid CMS structure.",
                message = "ตรวจพบการแก้ไขหรือโครงสร้างลายเซ็นเสียหาย: ${ex.message}"
            )
        }
    }

    /**
     * Helper to simulate byte-level tampering on a signed PDF for PoC demonstration.
     * Alters 1 ASCII byte inside the signed byte range (keeping exact byte offsets intact)
     * so the PDF still opens cleanly in Adobe Reader / pyHanko while failing SHA-256 CMS verification.
     */
    fun createTamperedCopy(signedPdfBytes: ByteArray): ByteArray {
        val copy = signedPdfBytes.clone()
        Loader.loadPDF(signedPdfBytes).use { doc ->
            val sig = doc.signatureDictionaries.firstOrNull()
                ?: throw IllegalArgumentException("Cannot tamper an unsigned PDF")
            val br = sig.byteRange
            val marker = "Bangkok".toByteArray(Charsets.US_ASCII)
            val replacement = "Tamperd".toByteArray(Charsets.US_ASCII)

            var tampered = false
            // Search within the signed byte ranges [0..br[1]] and [br[2]..br[2]+br[3]]
            val ranges = listOf(0 until br[1], br[2] until (br[2] + br[3]))
            for (range in ranges) {
                for (i in range.first..(range.last - marker.size)) {
                    var match = true
                    for (j in marker.indices) {
                        if (copy[i + j] != marker[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        for (j in replacement.indices) {
                            copy[i + j] = replacement[j]
                        }
                        tampered = true
                        break
                    }
                }
                if (tampered) break
            }

            if (!tampered) {
                val targetIndex = (br[1] - 32).coerceAtLeast(16)
                copy[targetIndex] = (copy[targetIndex].toInt() xor 0x01).toByte()
            }
        }
        return copy
    }
}
