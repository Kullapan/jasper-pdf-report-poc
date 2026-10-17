# Claim Report PoC — On-The-Fly Thai PDF Generation & Detached CMS Digital Signature

ระบบต้นแบบ (Proof of Concept) สำหรับสร้างรายงานการเรียกร้องค่าสินไหมทดแทน (Claim Report) ภาษาไทยแบบ **Generate & Sign On-The-Fly (In-Memory)** ด้วยเครื่องมือ **Open Source 100%**

---

## 1. สถาปัตยกรรมและหลักการทำงาน (On-The-Fly Pipeline)

1. **Immutable Snapshot Versioning (PostgreSQL 16.4):**
   * ข้อมูลรายงานเคลมแต่ละฉบับจะถูกบันทึกเป็น JSON Snapshot ผูกกับ `version` (`v1`, `v2`, ...) ในฐานข้อมูล
   * หากมีการแก้ไขข้อมูลเคลม ระบบจะสร้าง `version = n + 1` เสมอ โดยไม่แก้ไขทับ Snapshot ของเวอร์ชันเดิม
2. **In-Memory Thai PDF Generation (JasperReports 6.21.3):**
   * ใช้ฟอนต์ภาษาไทย **Sarabun** (`SIL Open Font License 1.1`) ฝังลงใน PDF (`Identity-H`, `pdfEmbedded=true`)
3. **In-Memory Detached CMS Digital Signature (PDFBox 3.0.3 + Bouncy Castle 1.78.1):**
   * ลงนามดิจิทัลแบบ **Invisible Signature** (`Filter: /Adobe.PPKLite`, `SubFilter: /adbe.pkcs7.detached`, Digest: `SHA-256`, Signature: `SHA256withRSA`)
   * โหลด Private Key และ X.509 Certificate จากไฟล์ `PKCS#12` (`certs/keystore.p12`) ที่สร้างตอน Setup โดยไม่ฝังหรือเปิดเผย Private Key ลงในโค้ด อิมเมจ หรือ Log
   * คำนวณค่า `SHA-256` ของไฟล์ที่ลงนามเสร็จแล้วบันทึกลงตาราง `report_audit_logs` ก่อนสตรีมไบนารีส่งกลับทาง HTTP Response โดยไม่เขียนไฟล์ PDF ค้างไว้บนดิสก์
4. **Single-Blob Fetch Pattern (React 18.3.1 + TypeScript 5.5.4 + PDF.js 4.6.82):**
   * Frontend เรียก `GET /api/reports/{reportId}/content?version={v}` เพียงครั้งเดียว เก็บเป็น `Blob` ในหน่วยความจำเบราว์เซอร์
   * นำ `Blob` ไปแสดงตัวอย่างบน `<canvas>` ด้วย `pdfjs-dist` (สำหรับการแสดงผลเท่านั้น ไม่ใช้ตรวจลายเซ็น) และผูก `Blob` ก้อนเดียวกันเข้ากับปุ่มดาวน์โหลด ทำให้ไฟล์ที่ Preview และ Download เป็นไบนารีเดียวกัน 100%

---

## 2. ตารางเทคโนโลยี เวอร์ชัน และ License

| Component | Package / Image | Version | License |
| :--- | :--- | :--- | :--- |
| Frontend UI | `react`, `react-dom` | `18.3.1` | MIT |
| Language (UI) | `typescript` | `5.5.4` | Apache-2.0 |
| PDF Preview | `pdfjs-dist` | `4.6.82` | Apache-2.0 |
| Backend Framework | `org.springframework.boot` | `4.1.1` | Apache-2.0 |
| Language (Backend) | `org.jetbrains.kotlin.jvm` | `2.3.21` | Apache-2.0 |
| Reporting Engine | `net.sf.jasperreports:jasperreports` | `6.21.3` | LGPL-3.0 |
| PDF Export Core | `com.github.librepdf:openpdf` | `1.3.30` | LGPL-3.0 / MPL-2.0 |
| PDF Signing | `org.apache.pdfbox:pdfbox` | `3.0.3` | Apache-2.0 |
| Cryptography (CMS) | `org.bouncycastle:bcpkix-jdk18on` | `1.78.1` | Bouncy Castle (MIT-like) |
| Database | `postgres` / `org.postgresql:postgresql` | `16.4` / `42.7.13` | PostgreSQL License |
| Thai Font | Google Fonts `Sarabun` | `2.000` | SIL Open Font License 1.1 |

---

## 3. วิธีเริ่มต้นใช้งาน (Quick Start)

### ขั้นตอนที่ 1: สร้าง PKCS#12 Keystore สำหรับทดสอบ
* **Windows (PowerShell):**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\setup-keystore.ps1
  ```
* **Linux / macOS (Bash):**
  ```bash
  chmod +x ./setup-keystore.sh && ./setup-keystore.sh
  ```

### ขั้นตอนที่ 2: รันระบบด้วย Docker Compose
```bash
docker compose up --build -d
```
* **Frontend UI:** `http://localhost:5173`
* **Backend API:** `http://localhost:8080/api/reports`

---

## 4. วิธีทดสอบ Digital Signature และการแก้ไขไฟล์หลังเซ็น (Tamper Test)

1. **ทดสอบผ่านหน้าเว็บ PoC (`http://localhost:5173`):**
   * คลิกปุ่ม **"✓ ตรวจสอบลายเซ็นของ PDF ที่แสดงผลอยู่"** ระบบจะส่งไฟล์เข้า `POST /api/reports/verify` เพื่อตรวจสอบโครงสร้าง CMS `/adbe.pkcs7.detached`, ค่า Digest `SHA-256`, `/ByteRange` และเทียบกับ Audit Log
   * คลิกปุ่ม **"⚡ จำลองแก้ไข 1 ไบต์หลังเซ็น ดาวน์โหลด & ตรวจจับ"** ระบบจะแก้ไขข้อมูล 1 จุดในช่วง `/ByteRange` ดาวน์โหลดไฟล์ `-TAMPERED.pdf` ลงเครื่อง และตรวจสอบผ่าน `/api/reports/verify` ซึ่งจะรายงานผล `INVALID / TAMPERED SIGNATURE` ทันที
2. **ทดสอบด้วยเครื่องมืออิสระ (`pyHanko` CLI หรือ Adobe Acrobat Reader):**
   ```bash
   pip install pyhanko
   pyhanko check inspect claim-CLM-2026-0001-v1-signed.pdf
   ```
   * เมื่อเปิดไฟล์ปกติใน **Adobe Acrobat Reader**: แถบ Signature Panel จะระบุว่าเอกสารไม่ถูกแก้ไขหลังลงนาม (`Document has not been modified since this signature was applied`) แต่จะแจ้งเตือนเรื่องความน่าเชื่อถือของผู้ออกใบรับรอง เนื่องจากใช้ Self-Signed Certificate สำหรับ PoC
   * เมื่อเปิดไฟล์ `-TAMPERED.pdf` ใน **Adobe Acrobat Reader**: จะแจ้งเตือนสีแดงทันทีว่า `Signature is INVALID — Document has been altered or corrupted since the signature was applied`
