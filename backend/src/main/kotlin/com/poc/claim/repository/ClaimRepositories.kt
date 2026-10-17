package com.poc.claim.repository

import com.poc.claim.model.ClaimEntity
import com.poc.claim.model.ClaimSnapshotEntity
import com.poc.claim.model.ReportAuditLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ClaimRepository : JpaRepository<ClaimEntity, UUID> {
    fun findAllByOrderByUpdatedAtDesc(): List<ClaimEntity>
    fun findByClaimNumber(claimNumber: String): ClaimEntity?
}

@Repository
interface ClaimSnapshotRepository : JpaRepository<ClaimSnapshotEntity, UUID> {
    fun findByClaimIdAndVersion(claimId: UUID, version: Int): ClaimSnapshotEntity?
    fun findAllByClaimIdOrderByVersionDesc(claimId: UUID): List<ClaimSnapshotEntity>
}

@Repository
interface ReportAuditLogRepository : JpaRepository<ReportAuditLogEntity, UUID> {
    fun findTop15ByClaimIdOrderByGeneratedAtDesc(claimId: UUID): List<ReportAuditLogEntity>
    fun findBySha256Checksum(sha256Checksum: String): List<ReportAuditLogEntity>
}
