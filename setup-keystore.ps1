$ErrorActionPreference = "Stop"

$CertDir = Join-Path $PSScriptRoot "certs"
if (-not (Test-Path $CertDir)) {
    New-Item -ItemType Directory -Path $CertDir | Out-Null
}

$KeystoreFile = Join-Path $CertDir "keystore.p12"
$PublicCertFile = Join-Path $CertDir "claim-signer-public.crt"
$Password = if ($env:KEYSTORE_PASSWORD) { $env:KEYSTORE_PASSWORD } else { "changeit_local_poc" }
$Alias = if ($env:KEYSTORE_ALIAS) { $env:KEYSTORE_ALIAS } else { "claimsigner" }

if (Test-Path $KeystoreFile) {
    Write-Host "Keystore already exists at $KeystoreFile. Removing old keystore to regenerate..."
    Remove-Item $KeystoreFile -Force
}

Write-Host "Generating self-signed PKCS#12 keystore (RSA 2048, SHA256withRSA)..."
keytool -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -sigalg SHA256withRSA `
    -validity 365 `
    -keystore $KeystoreFile `
    -storetype PKCS12 `
    -storepass $Password `
    -keypass $Password `
    -dname "CN=Claim Report PoC Signer, OU=Digital Claims Architecture, O=OpenSource Insurance PoC, L=Bangkok, C=TH"

Write-Host "Exporting public certificate (for independent verification only)..."
keytool -exportcert `
    -alias $Alias `
    -keystore $KeystoreFile `
    -storetype PKCS12 `
    -storepass $Password `
    -rfc `
    -file $PublicCertFile

Write-Host "Done! PKCS#12 keystore generated at: $KeystoreFile"
Write-Host "Public certificate exported at: $PublicCertFile"
