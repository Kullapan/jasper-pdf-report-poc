import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
}

group = "com.poc.claim"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database: PostgreSQL (managed by Spring Boot 4.1.1 BOM -> 42.7.13)
    runtimeOnly("org.postgresql:postgresql")

    // Reporting Engine: JasperReports 7.1.0 (LGPLv3) + OpenPDF + JDT Compiler
    implementation("net.sf.jasperreports:jasperreports:7.1.0")
    implementation("net.sf.jasperreports:jasperreports-pdf:7.1.0")
    implementation("net.sf.jasperreports:jasperreports-jdt:7.1.0")
    implementation("commons-beanutils:commons-beanutils:1.11.0")

    // PDF Digital Signature & CMS: Apache PDFBox 3.0.3 + Bouncy Castle 1.85
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
