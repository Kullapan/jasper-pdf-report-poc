package com.poc.claim.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "claims")
class ClaimEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "claim_number", nullable = false, unique = true)
    var claimNumber: String,

    @Column(name = "policy_number", nullable = false)
    var policyNumber: String,

    @Column(name = "claimant_name", nullable = false)
    var claimantName: String,

    @Column(name = "latest_version", nullable = false)
    var latestVersion: Int = 1,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "claim_snapshots",
    uniqueConstraints = [UniqueConstraint(columnNames = ["claim_id", "version"])]
)
class ClaimSnapshotEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "claim_id", nullable = false, updatable = false)
    val claimId: UUID,

    @Column(name = "version", nullable = false, updatable = false)
    val version: Int,

    @Column(name = "data_snapshot", columnDefinition = "TEXT", nullable = false, updatable = false)
    val dataSnapshot: String,

    @Column(name = "snapshot_hash", nullable = false, updatable = false)
    val snapshotHash: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "report_audit_logs")
class ReportAuditLogEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "claim_id", nullable = false, updatable = false)
    val claimId: UUID,

    @Column(name = "version", nullable = false, updatable = false)
    val version: Int,

    @Column(name = "sha256_checksum", nullable = false, updatable = false)
    val sha256Checksum: String,

    @Column(name = "signer_subject", nullable = false, updatable = false)
    val signerSubject: String,

    @Column(name = "generated_at", nullable = false, updatable = false)
    val generatedAt: Instant = Instant.now()
)

// DTOs for Request / Response / Snapshot Serialization

data class ClaimItemDto(
    val description: String,
    val category: String,
    val amount: BigDecimal
)

data class CreateOrUpdateClaimRequest(
    val claimNumber: String,
    val policyNumber: String,
    val claimantName: String,
    val incidentDate: String,
    val incidentDescription: String,
    val hospitalOrGarage: String,
    val items: List<ClaimItemDto>
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

data class ClaimSummaryResponse(
    val reportId: UUID,
    val claimNumber: String,
    val policyNumber: String,
    val claimantName: String,
    val latestVersion: Int,
    val totalAmount: BigDecimal,
    val updatedAt: Instant
)

data class VersionMetadataDto(
    val version: Int,
    val snapshotHash: String,
    val createdAt: Instant,
    val payload: ClaimSnapshotPayload
)

data class AuditLogDto(
    val id: UUID,
    val version: Int,
    val sha256Checksum: String,
    val signerSubject: String,
    val generatedAt: Instant
)

data class ClaimDetailResponse(
    val reportId: UUID,
    val claimNumber: String,
    val policyNumber: String,
    val claimantName: String,
    val latestVersion: Int,
    val versions: List<VersionMetadataDto>,
    val recentAuditLogs: List<AuditLogDto>
)
