package com.poc.claim

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.poc.claim.model.ClaimItemDto
import com.poc.claim.model.ClaimSnapshotPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.security.MessageDigest

class TestReport {

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    @Test
    fun testSnapshotSerialization() {
        val payload = ClaimSnapshotPayload(
            reportId = "TEST-UUID",
            claimNumber = "CLM-2026-0001",
            policyNumber = "POL-AXA-889120",
            claimantName = "สมชาย ใจดี",
            incidentDate = "2026-04-10",
            incidentDescription = "ทดสอบระบบ",
            hospitalOrGarage = "โรงพยาบาลกรุงเทพ",
            version = 1,
            snapshotCreatedAt = "2026-04-17 13:50:00 ICT",
            items = listOf(
                ClaimItemDto("ค่ารักษาพยาบาล", "Medical", BigDecimal("53700.00"))
            ),
            totalAmount = BigDecimal("53700.00")
        )

        val json = objectMapper.writeValueAsString(payload)
        assertNotNull(json)

        val deserialized = objectMapper.readValue(json, ClaimSnapshotPayload::class.java)
        assertEquals(payload.claimNumber, deserialized.claimNumber)
        assertEquals(payload.totalAmount, deserialized.totalAmount)

        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        assertEquals(64, hash.length)
        println("TestReport snapshot serialization passed, hash: $hash")
    }
}
