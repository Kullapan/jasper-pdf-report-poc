package com.poc.pdf.service

import com.poc.pdf.model.ClaimSnapshotPayload
import com.poc.pdf.model.RenderPdfRequest
import net.sf.jasperreports.engine.*
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.util.Base64
import javax.imageio.ImageIO

data class JasperClaimRow(
    val itemNo: Int,
    val description: String,
    val category: String,
    val amountFormatted: String
)

@Service
class JasperReportService {

    private val compiledReport: JasperReport by lazy {
        try {
            ClassPathResource("reports/claim_report.jrxml").inputStream.use { inputStream ->
                JasperCompileManager.compileReport(inputStream)
            }
        } catch (e: Throwable) {
            println("=== JASPER COMPILATION ERROR ===")
            e.printStackTrace()
            throw e
        }
    }

    private val defaultClaimantSignature: BufferedImage? by lazy {
        loadImageSafe("images/claimant_signature.png")
    }

    private val defaultApproverSignature: BufferedImage? by lazy {
        loadImageSafe("images/approver_signature.png")
    }

    private val defaultOfficialStamp: BufferedImage? by lazy {
        loadImageSafe("images/official_stamp.png")
    }

    private val currencyFormat = DecimalFormat("#,##0.00")

    private fun loadImageSafe(resourcePath: String): BufferedImage? {
        return try {
            ClassPathResource(resourcePath).inputStream.use { stream ->
                ImageIO.read(stream)
            }
        } catch (ex: Exception) {
            println("Warning: could not load image $resourcePath: ${ex.message}")
            null
        }
    }

    private fun decodeBase64Image(base64Str: String?): BufferedImage? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val cleaned = base64Str.substringAfterLast("base64,")
            val bytes = Base64.getDecoder().decode(cleaned)
            ByteArrayInputStream(bytes).use { ImageIO.read(it) }
        } catch (ex: Exception) {
            println("Warning: failed to decode custom base64 image: ${ex.message}")
            null
        }
    }

    fun generateUnsignedPdfBytes(request: RenderPdfRequest): ByteArray {
        val snapshot = request.payload
        val rows = snapshot.items.mapIndexed { index, item ->
            JasperClaimRow(
                itemNo = index + 1,
                description = item.description,
                category = item.category,
                amountFormatted = currencyFormat.format(item.amount)
            )
        }

        val claimantImage = decodeBase64Image(request.customClaimantSignatureBase64) ?: defaultClaimantSignature
        val approverImage = decodeBase64Image(request.customApproverSignatureBase64) ?: defaultApproverSignature
        val stampImage = defaultOfficialStamp

        val parameters = hashMapOf<String, Any?>(
            "reportId" to snapshot.reportId,
            "claimNumber" to snapshot.claimNumber,
            "policyNumber" to snapshot.policyNumber,
            "claimantName" to snapshot.claimantName,
            "incidentDate" to snapshot.incidentDate,
            "incidentDescription" to snapshot.incidentDescription,
            "hospitalOrGarage" to snapshot.hospitalOrGarage,
            "version" to snapshot.version,
            "snapshotCreatedAt" to snapshot.snapshotCreatedAt,
            "totalAmount" to currencyFormat.format(snapshot.totalAmount),
            "signerSubject" to request.signerSubject,
            "claimantSignatureImage" to claimantImage,
            "approverSignatureImage" to approverImage,
            "officialStampImage" to stampImage
        )

        val dataSource = JRBeanCollectionDataSource(rows)
        val jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, dataSource)

        return ByteArrayOutputStream().use { outputStream ->
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream)
            outputStream.toByteArray()
        }
    }
}
