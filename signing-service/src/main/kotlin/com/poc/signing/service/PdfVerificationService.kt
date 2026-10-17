package com.poc.signing.service

import com.poc.signing.model.SignatureVerificationResult
import org.apache.pdfbox.Loader
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class PdfVerificationService {

    fun verifyPdfBytes(pdfBytes: ByteArray): SignatureVerificationResult {
        val fileSha256 = MessageDigest.getInstance("SHA-256")
            .digest(pdfBytes)
            .joinToString("") { "%02x".format(it) }

        return try {
            Loader.loadPDF(pdfBytes).use { document ->
                val signatures = document.signatureDictionaries
                if (signatures.isEmpty()) {
                    return SignatureVerificationResult(
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

                SignatureVerificationResult(
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
            SignatureVerificationResult(
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
                selfSignedNotice = "Verification failed due to corrupted PDF or invalid CMS structure.",
                message = "ตรวจพบการแก้ไขหรือโครงสร้างลายเซ็นเสียหาย: ${ex.message}"
            )
        }
    }

    fun createTamperedCopy(signedPdfBytes: ByteArray): ByteArray {
        val copy = signedPdfBytes.clone()
        Loader.loadPDF(signedPdfBytes).use { doc ->
            val sig = doc.signatureDictionaries.firstOrNull()
                ?: throw IllegalArgumentException("Cannot tamper an unsigned PDF")
            val br = sig.byteRange
            val marker = "Bangkok".toByteArray(Charsets.US_ASCII)
            val replacement = "Tamperd".toByteArray(Charsets.US_ASCII)

            var tampered = false
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
