import React, { useEffect, useRef, useState } from 'react';
import * as pdfjsLib from 'pdfjs-dist';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

interface ClaimItem {
  description: string;
  category: string;
  amount: number;
}

interface ClaimSummary {
  reportId: string;
  claimNumber: string;
  policyNumber: string;
  claimantName: string;
  latestVersion: number;
  totalAmount: number;
  updatedAt: string;
}

interface ClaimSnapshotPayload {
  reportId: string;
  claimNumber: string;
  policyNumber: string;
  claimantName: string;
  incidentDate: string;
  incidentDescription: string;
  hospitalOrGarage: string;
  version: number;
  snapshotCreatedAt: string;
  items: ClaimItem[];
  totalAmount: number;
}

interface VersionMetadata {
  version: number;
  snapshotHash: string;
  createdAt: string;
  payload: ClaimSnapshotPayload;
}

interface AuditLog {
  id: string;
  version: number;
  sha256Checksum: string;
  signerSubject: string;
  generatedAt: string;
}

interface ClaimDetail {
  reportId: string;
  claimNumber: string;
  policyNumber: string;
  claimantName: string;
  latestVersion: number;
  versions: VersionMetadata[];
  recentAuditLogs: AuditLog[];
}

interface VerificationResult {
  isValid: boolean;
  isTampered: boolean;
  signatureFound: boolean;
  filter: string | null;
  subFilter: string | null;
  digestAlgorithmOid: string | null;
  signerSubject: string | null;
  signerIssuer: string | null;
  serialNumberHex: string | null;
  signDate: string | null;
  reason: string | null;
  byteRange: number[] | null;
  coversWholeDocument: boolean;
  fileSha256: string;
  matchedAuditLog: boolean;
  matchedReportVersion: number | null;
  selfSignedNotice: string;
  message: string;
}

export const App: React.FC = () => {
  const [claims, setClaims] = useState<ClaimSummary[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null);
  const [claimDetail, setClaimDetail] = useState<ClaimDetail | null>(null);
  const [selectedVersion, setSelectedVersion] = useState<number>(1);

  // Single In-Memory Blob for both PDF.js Preview and Direct Download
  const [currentBlob, setCurrentBlob] = useState<Blob | null>(null);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [currentSha256, setCurrentSha256] = useState<string>('');
  const [pdfLoading, setPdfLoading] = useState<boolean>(false);

  // Verification State
  const [verifyResult, setVerifyResult] = useState<VerificationResult | null>(null);
  const [verifying, setVerifying] = useState<boolean>(false);

  // Editor Modal / Form State for Creating New Version or New Claim
  const [editMode, setEditMode] = useState<'NONE' | 'NEW_VERSION' | 'NEW_CLAIM'>('NONE');
  const [formClaimNumber, setFormClaimNumber] = useState('');
  const [formPolicyNumber, setFormPolicyNumber] = useState('');
  const [formClaimantName, setFormClaimantName] = useState('');
  const [formIncidentDate, setFormIncidentDate] = useState('');
  const [formIncidentDesc, setFormIncidentDesc] = useState('');
  const [formHospital, setFormHospital] = useState('');
  const [formItems, setFormItems] = useState<ClaimItem[]>([]);

  // Multi-Page PDF Viewer State
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [totalPages, setTotalPages] = useState<number>(1);
  const pdfDocRef = useRef<pdfjsLib.PDFDocumentProxy | null>(null);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const loadClaimsList = async (selectId?: string) => {
    const res = await fetch('/api/reports');
    const data: ClaimSummary[] = await res.json();
    setClaims(data);
    const targetId = selectId || selectedReportId || (data.length > 0 ? data[0].reportId : null);
    if (targetId) {
      setSelectedReportId(targetId);
      await loadClaimDetail(targetId);
    }
  };

  const loadClaimDetail = async (reportId: string, targetVer?: number) => {
    const res = await fetch(`/api/reports/${reportId}`);
    const detail: ClaimDetail = await res.json();
    setClaimDetail(detail);
    const ver = targetVer ?? detail.latestVersion;
    setSelectedVersion(ver);
    await fetchAndRenderSignedPdfOnTheFly(reportId, ver);
  };

  const renderPage = async (pageNumber: number, doc?: pdfjsLib.PDFDocumentProxy) => {
    const activeDoc = doc || pdfDocRef.current;
    if (!activeDoc) return;
    try {
      const page = await activeDoc.getPage(pageNumber);
      const canvas = canvasRef.current;
      if (canvas) {
        const viewport = page.getViewport({ scale: 1.45 });
        const context = canvas.getContext('2d');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        if (context) {
          await page.render({ canvasContext: context, viewport }).promise;
        }
      }
    } catch (err) {
      console.error(`Error rendering page ${pageNumber}:`, err);
    }
  };

  const handlePageChange = async (newPage: number) => {
    if (newPage < 1 || newPage > totalPages) return;
    setCurrentPage(newPage);
    await renderPage(newPage);
  };

  /**
   * Core Architectural Pattern: Fetch Once as Blob
   * Calls GET /api/reports/{id}/content?version={v} once, stores the signed binary in browser RAM,
   * renders it onto <canvas> via PDF.js, and binds the exact same Blob to the Download button.
   */
  const fetchAndRenderSignedPdfOnTheFly = async (reportId: string, version: number) => {
    setPdfLoading(true);
    setVerifyResult(null);
    try {
      const response = await fetch(`/api/reports/${reportId}/content?version=${version}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const shaHeader = response.headers.get('X-Report-SHA256') || '';
      const pdfBlob = await response.blob();

      if (blobUrl) {
        window.URL.revokeObjectURL(blobUrl);
      }
      const newObjectUrl = window.URL.createObjectURL(pdfBlob);
      setCurrentBlob(pdfBlob);
      setBlobUrl(newObjectUrl);
      setCurrentSha256(shaHeader);

      // Render on Canvas with PDF.js (Supports multi-page documents)
      const arrayBuffer = await pdfBlob.arrayBuffer();
      const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
      const pdfDoc = await loadingTask.promise;
      pdfDocRef.current = pdfDoc;
      setTotalPages(pdfDoc.numPages);
      setCurrentPage(1);
      await renderPage(1, pdfDoc);

      // Refresh audit log list in background
      const detailRes = await fetch(`/api/reports/${reportId}`);
      if (detailRes.ok) {
        setClaimDetail(await detailRes.json());
      }
    } catch (err) {
      console.error('Failed to generate or preview signed PDF:', err);
    } finally {
      setPdfLoading(false);
    }
  };

  useEffect(() => {
    loadClaimsList();
    return () => {
      if (blobUrl) window.URL.revokeObjectURL(blobUrl);
    };
  }, []);

  const handleVerifyCurrentBlob = async () => {
    if (!currentBlob || !claimDetail) return;
    setVerifying(true);
    try {
      const formData = new FormData();
      formData.append(
        'file',
        currentBlob,
        `claim-${claimDetail.claimNumber}-v${selectedVersion}-signed.pdf`
      );
      const res = await fetch('/api/reports/verify', {
        method: 'POST',
        body: formData
      });
      setVerifyResult(await res.json());
    } finally {
      setVerifying(false);
    }
  };

  const handleTamperAndVerifyDemo = async () => {
    if (!currentBlob || !claimDetail) return;
    setVerifying(true);
    try {
      // Step 1: Send the valid signed PDF to /api/reports/tamper-demo to modify 1 byte inside signed ByteRange
      const formData = new FormData();
      const baseFilename = `claim-${claimDetail.claimNumber}-v${selectedVersion}`;
      formData.append('file', currentBlob, `${baseFilename}.pdf`);

      const tamperRes = await fetch('/api/reports/tamper-demo', {
        method: 'POST',
        body: formData
      });
      const tamperedBlob = await tamperRes.blob();

      // Trigger automatic download of the tampered PDF so user can also inspect it in Adobe Reader / pyHanko
      const tempUrl = window.URL.createObjectURL(tamperedBlob);
      const link = document.createElement('a');
      link.href = tempUrl;
      link.download = `${baseFilename}-TAMPERED.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(tempUrl);

      // Step 2: Send the tampered PDF to /api/reports/verify to prove detection
      const verifyForm = new FormData();
      verifyForm.append('file', tamperedBlob, `${baseFilename}-TAMPERED.pdf`);
      const verifyRes = await fetch('/api/reports/verify', {
        method: 'POST',
        body: verifyForm
      });
      setVerifyResult(await verifyRes.json());
    } finally {
      setVerifying(false);
    }
  };

  const handleUploadVerifyFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setVerifying(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await fetch('/api/reports/verify', {
        method: 'POST',
        body: formData
      });
      setVerifyResult(await res.json());
    } finally {
      setVerifying(false);
      e.target.value = '';
    }
  };

  const openNewVersionModal = () => {
    if (!claimDetail) return;
    const currentSnap =
      claimDetail.versions.find((v) => v.version === selectedVersion)?.payload ||
      claimDetail.versions[0].payload;
    setFormClaimNumber(currentSnap.claimNumber);
    setFormPolicyNumber(currentSnap.policyNumber);
    setFormClaimantName(currentSnap.claimantName);
    setFormIncidentDate(currentSnap.incidentDate);
    setFormIncidentDesc(currentSnap.incidentDescription);
    setFormHospital(currentSnap.hospitalOrGarage);
    setFormItems(currentSnap.items.map((item) => ({ ...item })));
    setEditMode('NEW_VERSION');
  };

  const openNewClaimModal = () => {
    const randomSuffix = Math.floor(1000 + Math.random() * 9000);
    setFormClaimNumber(`CLM-2026-${randomSuffix}`);
    setFormPolicyNumber(`POL-TH-${Math.floor(100000 + Math.random() * 899999)}`);
    setFormClaimantName('นายวีรพล ทดสอบระบบ');
    setFormIncidentDate('17 ตุลาคม 2569 เวลา 10:00 น.');
    setFormIncidentDesc('กระจกบังลมหน้าแตกร้าวจากเศษหินกระเด็นใส่ขณะขับขี่บนทางหลวงพิเศษ');
    setFormHospital('ศูนย์บริการกระจกรถยนต์มาตรฐาน สาขาบางนา');
    setFormItems([
      { description: 'ค่ากระจกบังลมหน้าแท้พร้อมฟิล์มกรองแสง', category: 'ค่าอะไหล่แท้', amount: 14500 },
      { description: 'ค่ากาวซิลิโคนเกรดโรงงานและค่าแรงติดตั้ง', category: 'ค่าแรงช่าง', amount: 2500 }
    ]);
    setEditMode('NEW_CLAIM');
  };

  const handleSaveClaimOrVersion = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload = {
      claimNumber: formClaimNumber,
      policyNumber: formPolicyNumber,
      claimantName: formClaimantName,
      incidentDate: formIncidentDate,
      incidentDescription: formIncidentDesc,
      hospitalOrGarage: formHospital,
      items: formItems
    };

    if (editMode === 'NEW_CLAIM') {
      const res = await fetch('/api/reports/claims', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        const created: ClaimDetail = await res.json();
        setEditMode('NONE');
        await loadClaimsList(created.reportId);
      }
    } else if (editMode === 'NEW_VERSION' && selectedReportId) {
      const res = await fetch(`/api/reports/${selectedReportId}/versions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        const updated: ClaimDetail = await res.json();
        setEditMode('NONE');
        await loadClaimsList(updated.reportId);
        await loadClaimDetail(updated.reportId, updated.latestVersion);
      }
    }
  };

  return (
    <div>
      <header className="app-header">
        <div>
          <h1>ระบบสร้างและลงนามดิจิทัลรายงานเคลมประกันภัย (On-The-Fly Signed PDF PoC)</h1>
          <div className="subtitle">
            100% Open Source Stack — JasperReports (Sarabun Thai Font) + PDFBox 3 + Bouncy Castle (Detached CMS SHA-256)
          </div>
        </div>
        <div className="badge-group">
          <span className="tech-badge">React 18.3.1 + TS 5.5.4</span>
          <span className="tech-badge">Spring Boot 4.1.1 + Kotlin 2.3.21</span>
          <span className="tech-badge">JasperReports 6.21.3</span>
          <span className="tech-badge">PDFBox 3.0.3 + BC 1.78.1</span>
          <span className="tech-badge">PostgreSQL 16.4</span>
        </div>
      </header>

      <main className="main-grid">
        {/* Left Column: Claims, Versioning, and Verification Tools */}
        <div>
          <section className="card">
            <h2>
              <span>1. รายการเคลมประกันภัย (Claims)</span>
              <button className="btn btn-primary" onClick={openNewClaimModal}>
                + สร้างเคลมใหม่
              </button>
            </h2>
            <div className="claim-list">
              {claims.map((c) => (
                <div
                  key={c.reportId}
                  className={`claim-item ${c.reportId === selectedReportId ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedReportId(c.reportId);
                    loadClaimDetail(c.reportId);
                  }}
                >
                  <div className="claim-item-top">
                    <span>{c.claimNumber}</span>
                    <span style={{ color: '#0284c7' }}>
                      {c.totalAmount.toLocaleString('th-TH', { minimumFractionDigits: 2 })} บาท
                    </span>
                  </div>
                  <div className="claim-item-sub">
                    {c.claimantName} | กรมธรรม์: {c.policyNumber} | ล่าสุด: <strong>v{c.latestVersion}</strong>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {claimDetail && (
            <section className="card">
              <h2>
                <span>2. เลือกเวอร์ชัน Snapshot ({claimDetail.claimNumber})</span>
                <button className="btn btn-outline" onClick={openNewVersionModal}>
                  แก้ไขข้อมูล (สร้าง v{claimDetail.latestVersion + 1})
                </button>
              </h2>
              <div style={{ fontSize: '0.83rem', color: '#475569', marginBottom: '8px' }}>
                ข้อมูลแต่ละเวอร์ชันถูกบันทึกแบบ Immutable JSON Snapshot ใน PostgreSQL หากแก้ไขข้อมูล ระบบจะสร้าง Version ใหม่เสมอ:
              </div>
              <div className="version-pills">
                {claimDetail.versions.map((v) => (
                  <button
                    key={v.version}
                    className={`version-pill ${v.version === selectedVersion ? 'active' : ''}`}
                    onClick={() => {
                      setSelectedVersion(v.version);
                      fetchAndRenderSignedPdfOnTheFly(claimDetail.reportId, v.version);
                    }}
                  >
                    Version v{v.version} ({v.payload.totalAmount.toLocaleString()} บาท)
                  </button>
                ))}
              </div>

              {editMode !== 'NONE' && (
                <form
                  onSubmit={handleSaveClaimOrVersion}
                  style={{
                    background: '#f8fafc',
                    padding: '12px',
                    borderRadius: '8px',
                    border: '1px solid #94a3b8',
                    marginTop: '12px'
                  }}
                >
                  <h3 style={{ margin: '0 0 10px 0', fontSize: '0.95rem', color: '#0284c7' }}>
                    {editMode === 'NEW_CLAIM'
                      ? 'สร้างรายการเคลมใหม่ (Version 1)'
                      : `แก้ไขข้อมูลเพื่อสร้าง Version ใหม่ (v${claimDetail.latestVersion + 1})`}
                  </h3>
                  {editMode === 'NEW_CLAIM' && (
                    <div className="form-group">
                      <label>เลขที่เคลม (Claim Number)</label>
                      <input
                        value={formClaimNumber}
                        onChange={(e) => setFormClaimNumber(e.target.value)}
                        required
                      />
                    </div>
                  )}
                  <div className="form-group">
                    <label>ชื่อผู้เอาประกันภัย</label>
                    <input
                      value={formClaimantName}
                      onChange={(e) => setFormClaimantName(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>เลขที่กรมธรรม์</label>
                    <input
                      value={formPolicyNumber}
                      onChange={(e) => setFormPolicyNumber(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>วันที่เกิดเหตุ</label>
                    <input
                      value={formIncidentDate}
                      onChange={(e) => setFormIncidentDate(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>สถานพยาบาล / ศูนย์ซ่อม</label>
                    <input
                      value={formHospital}
                      onChange={(e) => setFormHospital(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>รายละเอียดเหตุการณ์</label>
                    <textarea
                      rows={2}
                      value={formIncidentDesc}
                      onChange={(e) => setFormIncidentDesc(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label>ยอดเงินรายการที่ 1 ({formItems[0]?.description})</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formItems[0]?.amount || 0}
                      onChange={(e) => {
                        const updated = [...formItems];
                        updated[0] = { ...updated[0], amount: parseFloat(e.target.value) || 0 };
                        setFormItems(updated);
                      }}
                      required
                    />
                  </div>
                  <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
                    <button type="submit" className="btn btn-primary">
                      บันทึก Snapshot
                    </button>
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => setEditMode('NONE')}
                    >
                      ยกเลิก
                    </button>
                  </div>
                </form>
              )}
            </section>
          )}

          {/* Section 3: Signature Verification & Tamper Testing */}
          <section className="card">
            <h2>3. ตรวจสอบลายเซ็นดิจิทัล & ทดสอบการแก้ไขไฟล์ (Verify & Tamper Test)</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <button
                className="btn btn-success"
                disabled={!currentBlob || verifying}
                onClick={handleVerifyCurrentBlob}
              >
                ✓ ตรวจสอบลายเซ็นของ PDF ที่แสดงผลอยู่ (Verify Current Blob)
              </button>

              <button
                className="btn btn-danger"
                disabled={!currentBlob || verifying}
                onClick={handleTamperAndVerifyDemo}
              >
                ⚡ จำลองแก้ไข 1 ไบต์หลังเซ็น ดาวน์โหลด & ตรวจจับ (Tamper Test)
              </button>

              <div style={{ marginTop: '6px' }}>
                <label
                  style={{
                    display: 'block',
                    fontSize: '0.82rem',
                    fontWeight: 600,
                    marginBottom: '4px',
                    color: '#334155'
                  }}
                >
                  หรืออัปโหลดไฟล์ PDF จากเครื่องเพื่อตรวจสอบ (`/api/reports/verify`):
                </label>
                <input type="file" accept="application/pdf" onChange={handleUploadVerifyFile} />
              </div>
            </div>

            {verifyResult && (
              <div className={`verify-box ${verifyResult.isValid ? 'valid' : 'invalid'}`}>
                <div style={{ fontWeight: 700, fontSize: '0.96rem', marginBottom: '6px' }}>
                  {verifyResult.isValid ? '✅ VALID SIGNATURE' : '❌ INVALID / TAMPERED SIGNATURE'} —{' '}
                  {verifyResult.message}
                </div>
                <div className="meta-grid">
                  <div className="meta-key">SubFilter:</div>
                  <div className="meta-val">{verifyResult.subFilter || '-'}</div>

                  <div className="meta-key">Digest OID:</div>
                  <div className="meta-val">
                    {verifyResult.digestAlgorithmOid} (SHA-256)
                  </div>

                  <div className="meta-key">Signer Subject:</div>
                  <div className="meta-val">{verifyResult.signerSubject || '-'}</div>

                  <div className="meta-key">Cert Serial:</div>
                  <div className="meta-val">{verifyResult.serialNumberHex || '-'}</div>

                  <div className="meta-key">Signed At:</div>
                  <div className="meta-val">{verifyResult.signDate || '-'}</div>

                  <div className="meta-key">ByteRange:</div>
                  <div className="meta-val">
                    {verifyResult.byteRange ? JSON.stringify(verifyResult.byteRange) : '-'} (Covers
                    Whole File: {String(verifyResult.coversWholeDocument)})
                  </div>

                  <div className="meta-key">File SHA-256:</div>
                  <div className="meta-val">{verifyResult.fileSha256}</div>

                  <div className="meta-key">Audit Log Match:</div>
                  <div className="meta-val">
                    {verifyResult.matchedAuditLog
                      ? `ตรงกับประวัติการออกเอกสาร (Version v${verifyResult.matchedReportVersion})`
                      : 'ไม่ตรงกับ Audit Log (ไฟล์อาจถูกแก้ไขหลังดาวน์โหลด)'}
                  </div>
                </div>
                <div
                  style={{
                    marginTop: '8px',
                    fontSize: '0.78rem',
                    borderTop: '1px solid rgba(0,0,0,0.1)',
                    paddingTop: '6px'
                  }}
                >
                  ℹ️ {verifyResult.selfSignedNotice}
                </div>
              </div>
            )}
          </section>

          {/* Section 4: Recent On-The-Fly Audit Logs */}
          {claimDetail && claimDetail.recentAuditLogs.length > 0 && (
            <section className="card">
              <h2>4. ประวัติการลงนาม On-The-Fly ล่าสุด (PostgreSQL Audit Log)</h2>
              <div style={{ maxHeight: '170px', overflowY: 'auto', fontSize: '0.78rem' }}>
                {claimDetail.recentAuditLogs.map((log) => (
                  <div
                    key={log.id}
                    style={{
                      padding: '6px 0',
                      borderBottom: '1px solid #e2e8f0',
                      fontFamily: 'monospace'
                    }}
                  >
                    <div>
                      <strong>v{log.version}</strong> | {new Date(log.generatedAt).toLocaleTimeString('th-TH')}
                    </div>
                    <div style={{ color: '#0284c7', wordBreak: 'break-all' }}>
                      SHA256: {log.sha256Checksum}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>

        {/* Right Column: PDF.js Preview & Identical Binary Download */}
        <div>
          <section className="card">
            <h2>
              <span>
                ตัวอย่างรายงาน PDF ฉบับลงนามแล้ว (
                {claimDetail ? `${claimDetail.claimNumber} - Version v${selectedVersion}` : '...'})
              </span>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  className="btn btn-outline"
                  disabled={!selectedReportId || pdfLoading}
                  onClick={() =>
                    selectedReportId &&
                    fetchAndRenderSignedPdfOnTheFly(selectedReportId, selectedVersion)
                  }
                >
                  ↻ สั่ง Gen & Sign On-The-Fly ใหม่
                </button>
                {blobUrl && claimDetail && (
                  <a
                    href={blobUrl}
                    download={`claim-${claimDetail.claimNumber}-v${selectedVersion}-signed.pdf`}
                    className="btn btn-primary"
                  >
                    ⬇ ดาวน์โหลด PDF ฉบับลงนามแล้ว (ไฟล์เดียวกับ Preview)
                  </a>
                )}
              </div>
            </h2>

            <div className="warning-banner">
              ⚠️ <strong>ข้อจำกัดทางสถาปัตยกรรม (PDF.js Preview Only):</strong> หน้าต่างด้านล่างนี้ใช้{' '}
              <code>pdfjs-dist@4.6.82</code> สำหรับแสดงผลกราฟิกของหน้าเอกสารเท่านั้น{' '}
              <strong>ไม่ได้ใช้ตรวจสอบความถูกต้องของลายเซ็นดิจิทัล</strong> — ปุ่มดาวน์โหลดด้านบนจะบันทึก{' '}
              <code>Blob</code> ก้อนเดียวกับที่แสดงผลอยู่นี้ลงเครื่อง เพื่อนำไปตรวจสอบด้วย Adobe Acrobat
              Reader, <code>pyHanko</code> หรือปุ่ม Verify ด้านซ้ายมือ
            </div>

            {currentSha256 && (
              <div className="hash-box">
                <strong>In-Memory Signed Blob SHA-256 (Preview === Download):</strong> {currentSha256}
              </div>
            )}

            {/* Multi-Page Navigation Bar */}
            <div className="pdf-page-toolbar">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <button
                  className="btn btn-outline"
                  style={{ padding: '4px 10px', fontSize: '0.85rem' }}
                  disabled={currentPage <= 1 || pdfLoading}
                  onClick={() => handlePageChange(currentPage - 1)}
                >
                  ◀ หน้าก่อนหน้า
                </button>
                <span className="page-counter-badge">
                  หน้า {currentPage} / {totalPages}
                </span>
                <button
                  className="btn btn-outline"
                  style={{ padding: '4px 10px', fontSize: '0.85rem' }}
                  disabled={currentPage >= totalPages || pdfLoading}
                  onClick={() => handlePageChange(currentPage + 1)}
                >
                  หน้าถัดไป ▶
                </button>
              </div>

              <div style={{ display: 'flex', gap: '6px' }}>
                <button
                  className={`btn ${currentPage === 1 ? 'btn-primary' : 'btn-outline'}`}
                  style={{ padding: '4px 10px', fontSize: '0.82rem' }}
                  onClick={() => handlePageChange(1)}
                >
                  หน้า 1: รายการเคลม
                </button>
                <button
                  className={`btn ${currentPage === 2 ? 'btn-primary' : 'btn-outline'}`}
                  style={{ padding: '4px 10px', fontSize: '0.82rem' }}
                  onClick={() => handlePageChange(2)}
                >
                  หน้า 2: ข้อตกลงและลายเซ็นชื่ออนุมัติ
                </button>
              </div>
            </div>

            <div className="canvas-wrapper">
              {pdfLoading && (
                <div style={{ color: '#ffffff', alignSelf: 'center', fontSize: '1.1rem' }}>
                  กำลังสร้าง PDF ภาษาไทยด้วย JasperReports และลงนาม Detached CMS (SHA-256) ในหน่วยความจำ...
                </div>
              )}
              <canvas
                ref={canvasRef}
                style={{ display: pdfLoading ? 'none' : 'block' }}
              />
            </div>
          </section>
        </div>
      </main>
    </div>
  );
};

export default App;
