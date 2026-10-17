package com.poc.claim.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.poc.claim.client.PdfGeneratorClient
import com.poc.claim.client.SignatureVerificationResponse
import com.poc.claim.client.SignedPdfResponseDto
import com.poc.claim.client.SigningServiceClient
import com.poc.claim.model.*
import com.poc.claim.repository.ClaimRepository
import com.poc.claim.repository.ClaimSnapshotRepository
import com.poc.claim.repository.ReportAuditLogRepository
import jakarta.annotation.PostConstruct
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ClaimReportService(
    private val claimRepository: ClaimRepository,
    private val snapshotRepository: ClaimSnapshotRepository,
    private val auditLogRepository: ReportAuditLogRepository,
    private val pdfGeneratorClient: PdfGeneratorClient,
    private val signingServiceClient: SigningServiceClient,
    private val objectMapper: ObjectMapper
) {

    private val thaiDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'ICT'")
        .withZone(ZoneId.of("Asia/Bangkok"))

    @PostConstruct
    @Transactional
    fun seedInitialSyntheticClaims() {
        if (claimRepository.count() > 0L) return

        createClaim(
            CreateOrUpdateClaimRequest(
                claimNumber = "CLM-2026-0001",
                policyNumber = "POL-TH-884920",
                claimantName = "นายสมชาย ใจดี (ผู้เอาประกันภัยชั้น 1)",
                incidentDate = "15 ตุลาคม 2569 เวลา 09:30 น.",
                incidentDescription = "รถยนต์เฉี่ยวชนแผงกั้นจราจรบริเวณถนนพระราม 9 แขวงห้วยขวาง กรุงเทพมหานคร กันชนหน้าและไฟหน้าขวาได้รับความเสียหาย",
                hospitalOrGarage = "ศูนย์ซ่อมมาตรฐาน บริษัท โอเพ่นซอร์ส ออโต้เซอร์วิส จำกัด (สาขาพระราม 9)",
                items = listOf(
                    ClaimItemDto("ค่าชุดกันชนหน้าแท้พร้อมทำสีระบบ 2K", "ค่าอะไหล่และทำสี", BigDecimal("18500.00")),
                    ClaimItemDto("ค่าโคมไฟหน้าแบบ LED ข้างขวา", "ค่าอะไหล่แท้", BigDecimal("22000.00")),
                    ClaimItemDto("ค่าแรงถอดประกอบและตั้งศูนย์ไฟหน้า", "ค่าแรงช่าง", BigDecimal("4500.00"))
                )
            )
        )

        createClaim(
            CreateOrUpdateClaimRequest(
                claimNumber = "CLM-2026-0002",
                policyNumber = "POL-TH-551039",
                claimantName = "นางสาวกานดา รักสุขภาพ (ประกันสุขภาพกลุ่ม)",
                incidentDate = "16 ตุลาคม 2569 เวลา 14:15 น.",
                incidentDescription = "เข้ารับการรักษาอาการอาหารเป็นพิษเฉียบพลันและภาวะขาดน้ำ แพทย์วินิจฉัยให้นอนพักรักษาตัวในโรงพยาบาล (IPD) 2 คืน",
                hospitalOrGarage = "โรงพยาบาลไทยเมดิคอลเซ็นเตอร์ (กรุงเทพฯ)",
                items = listOf(
                    ClaimItemDto("ค่าห้องพักผู้ป่วยเดี่ยวมาตรฐานและค่าอาหาร (2 คืน)", "ค่าห้องและอาหาร", BigDecimal("9600.00")),
                    ClaimItemDto("ค่ายาและสารน้ำทางหลอดเลือดดำ (IV Fluid & Medication)", "ค่ารักษาพยาบาล", BigDecimal("12400.00")),
                    ClaimItemDto("ค่าตรวจทางห้องปฏิบัติการและค่าธรรมเนียมแพทย์เยี่ยมไข้", "ค่าแพทย์และแล็บ", BigDecimal("6500.00"))
                )
            )
        )
    }

    @Transactional
    fun createClaim(req: CreateOrUpdateClaimRequest): ClaimDetailResponse {
        val existing = claimRepository.findByClaimNumber(req.claimNumber.trim())
        if (existing != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "หมายเลขเคลม ${req.claimNumber} มีอยู่ในระบบแล้ว กรุณาใช้ฟีเจอร์สร้าง Version ใหม่"
            )
        }

        val now = Instant.now()
        val claim = ClaimEntity(
            claimNumber = req.claimNumber.trim(),
            policyNumber = req.policyNumber.trim(),
            claimantName = req.claimantName.trim(),
            latestVersion = 1,
            createdAt = now,
            updatedAt = now
        )
        claimRepository.save(claim)

        saveVersionSnapshot(claim, 1, req, now)
        return getClaimDetail(claim.id)
    }

    @Transactional
    fun createNewVersion(reportId: UUID, req: CreateOrUpdateClaimRequest): ClaimDetailResponse {
        val claim = claimRepository.findById(reportId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบรายงานเคลมรหัส $reportId")
        }

        val now = Instant.now()
        val nextVersion = claim.latestVersion + 1
        claim.policyNumber = req.policyNumber.trim()
        claim.claimantName = req.claimantName.trim()
        claim.latestVersion = nextVersion
        claim.updatedAt = now
        claimRepository.save(claim)

        saveVersionSnapshot(claim, nextVersion, req, now)
        return getClaimDetail(claim.id)
    }

    private fun saveVersionSnapshot(
        claim: ClaimEntity,
        version: Int,
        req: CreateOrUpdateClaimRequest,
        timestamp: Instant
    ) {
        val total = req.items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }
        val payload = ClaimSnapshotPayload(
            reportId = claim.id.toString(),
            claimNumber = claim.claimNumber,
            policyNumber = req.policyNumber.trim(),
            claimantName = req.claimantName.trim(),
            incidentDate = req.incidentDate.trim(),
            incidentDescription = req.incidentDescription.trim(),
            hospitalOrGarage = req.hospitalOrGarage.trim(),
            version = version,
            snapshotCreatedAt = thaiDateTimeFormatter.format(timestamp),
            items = req.items,
            totalAmount = total
        )

        val jsonString = objectMapper.writeValueAsString(payload)
        val snapshotHash = sha256Hex(jsonString.toByteArray(Charsets.UTF_8))

        val snapshotEntity = ClaimSnapshotEntity(
            claimId = claim.id,
            version = version,
            dataSnapshot = jsonString,
            snapshotHash = snapshotHash,
            createdAt = timestamp
        )
        snapshotRepository.save(snapshotEntity)
    }

    @Transactional(readOnly = true)
    fun listClaims(): List<ClaimSummaryResponse> {
        return claimRepository.findAllByOrderByUpdatedAtDesc().map { claim ->
            val latestSnap = snapshotRepository.findByClaimIdAndVersion(claim.id, claim.latestVersion)
            val totalAmount = if (latestSnap != null) {
                objectMapper.readValue(latestSnap.dataSnapshot, ClaimSnapshotPayload::class.java).totalAmount
            } else {
                BigDecimal.ZERO
            }
            ClaimSummaryResponse(
                reportId = claim.id,
                claimNumber = claim.claimNumber,
                policyNumber = claim.policyNumber,
                claimantName = claim.claimantName,
                latestVersion = claim.latestVersion,
                totalAmount = totalAmount,
                updatedAt = claim.updatedAt
            )
        }
    }

    @Transactional(readOnly = true)
    fun getClaimDetail(reportId: UUID): ClaimDetailResponse {
        val claim = claimRepository.findById(reportId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบรายงานเคลมรหัส $reportId")
        }

        val snapshots = snapshotRepository.findAllByClaimIdOrderByVersionDesc(reportId).map { snap ->
            VersionMetadataDto(
                version = snap.version,
                snapshotHash = snap.snapshotHash,
                createdAt = snap.createdAt,
                payload = objectMapper.readValue(snap.dataSnapshot, ClaimSnapshotPayload::class.java)
            )
        }

        val audits = auditLogRepository.findTop15ByClaimIdOrderByGeneratedAtDesc(reportId).map { audit ->
            AuditLogDto(
                id = audit.id,
                version = audit.version,
                sha256Checksum = audit.sha256Checksum,
                signerSubject = audit.signerSubject,
                generatedAt = audit.generatedAt
            )
        }

        return ClaimDetailResponse(
            reportId = claim.id,
            claimNumber = claim.claimNumber,
            policyNumber = claim.policyNumber,
            claimantName = claim.claimantName,
            latestVersion = claim.latestVersion,
            versions = snapshots,
            recentAuditLogs = audits
        )
    }

    @Transactional
    fun generateAndSignOnTheFly(reportId: UUID, requestedVersion: Int?): Pair<SignedPdfResponseDto, ClaimSnapshotPayload> {
        val claim = claimRepository.findById(reportId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบรายงานเคลมรหัส $reportId")
        }

        val targetVersion = requestedVersion ?: claim.latestVersion
        val snapshotEntity = snapshotRepository.findByClaimIdAndVersion(reportId, targetVersion)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "ไม่พบ Snapshot ข้อมูลสำหรับรายงานรหัส $reportId เวอร์ชัน $targetVersion"
            )

        val payload = objectMapper.readValue(snapshotEntity.dataSnapshot, ClaimSnapshotPayload::class.java)

        // Step 1: Call pdf-generator-service (:8082) to render PDF with fonts and signature images
        val unsignedPdfBytes = pdfGeneratorClient.renderClaimReport(payload)

        // Step 2: Call signing-service (:8081) to apply CMS detached digital signature
        val signedOutcome = signingServiceClient.signPdf(
            unsignedPdfBytes = unsignedPdfBytes,
            claimNumber = payload.claimNumber,
            version = payload.version
        )

        // Step 3: Record Audit Log of the generated & signed PDF SHA-256 checksum in PostgreSQL
        auditLogRepository.save(
            ReportAuditLogEntity(
                claimId = claim.id,
                version = payload.version,
                sha256Checksum = signedOutcome.sha256Checksum,
                signerSubject = signedOutcome.signerSubject,
                generatedAt = Instant.now()
            )
        )

        return Pair(signedOutcome, payload)
    }

    fun verifyPdf(pdfBytes: ByteArray): SignatureVerificationResponse {
        val rawVerify = signingServiceClient.verifyPdf(pdfBytes)
        val auditMatches = auditLogRepository.findBySha256Checksum(rawVerify.fileSha256)
        val matchedAudit = auditMatches.firstOrNull()

        return rawVerify.copy(
            matchedAuditLog = matchedAudit != null,
            matchedReportVersion = matchedAudit?.version
        )
    }

    fun tamperPdf(pdfBytes: ByteArray): ByteArray {
        return signingServiceClient.tamperPdf(pdfBytes)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
