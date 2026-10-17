package com.poc.pdf

import com.poc.pdf.model.ClaimItemDto
import com.poc.pdf.model.ClaimSnapshotPayload
import com.poc.pdf.model.RenderPdfRequest
import com.poc.pdf.service.JasperReportService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PdfGeneratorTest {

    @Test
    fun testGeneratePdfWithImages() {
        val service = JasperReportService()
        val payload = ClaimSnapshotPayload(
            reportId = "TEST-REPORT-UUID",
            claimNumber = "CLM-2026-TEST",
            policyNumber = "POL-AXA-99999",
            claimantName = "นายสมชาย ใจดี",
            incidentDate = "17 ตุลาคม 2569",
            incidentDescription = "ทดสอบการเรนเดอร์ PDF พร้อมรูปภาพลายเซ็นและตราประทับ",
            hospitalOrGarage = "ศูนย์บริการมาตรฐาน",
            version = 1,
            snapshotCreatedAt = "2026-10-17 14:00:00 ICT",
            items = listOf(
                ClaimItemDto("ค่าชุดกันชนหน้าแท้พร้อมทำสี", "ค่าอะไหล่", BigDecimal("18500.00")),
                ClaimItemDto("ค่าโคมไฟหน้า LED", "ค่าอะไหล่", BigDecimal("22000.00"))
            ),
            totalAmount = BigDecimal("40500.00")
        )

        val request = RenderPdfRequest(payload = payload)
        val pdfBytes = service.generateUnsignedPdfBytes(request)

        assertNotNull(pdfBytes)
        assertTrue(pdfBytes.size > 5000, "PDF bytes should be > 5KB")
        println("PdfGeneratorTest PASSED. Generated PDF size: ${pdfBytes.size} bytes")
    }
}
