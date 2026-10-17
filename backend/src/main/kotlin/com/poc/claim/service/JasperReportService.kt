package com.poc.claim.service

import com.poc.claim.model.ClaimSnapshotPayload
import net.sf.jasperreports.engine.JasperCompileManager
import net.sf.jasperreports.engine.JasperExportManager
import net.sf.jasperreports.engine.JasperFillManager
import net.sf.jasperreports.engine.JasperReport
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat

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
            var cause: Throwable? = e.cause
            while (cause != null) {
                println("CAUSE: ${cause.javaClass.name}: ${cause.message}")
                cause = cause.cause
            }
            throw e
        }
    }

    private val currencyFormat = DecimalFormat("#,##0.00")

    fun generateUnsignedPdfBytes(snapshot: ClaimSnapshotPayload, signerSubject: String): ByteArray {
        val rows = snapshot.items.mapIndexed { index, item ->
            JasperClaimRow(
                itemNo = index + 1,
                description = item.description,
                category = item.category,
                amountFormatted = currencyFormat.format(item.amount)
            )
        }

        val parameters = hashMapOf<String, Any>(
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
            "signerSubject" to signerSubject
        )

        val dataSource = JRBeanCollectionDataSource(rows)
        val jasperPrint = JasperFillManager.fillReport(compiledReport, parameters, dataSource)

        return ByteArrayOutputStream().use { outputStream ->
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream)
            outputStream.toByteArray()
        }
    }
}
