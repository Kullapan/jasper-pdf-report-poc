package com.poc.signing

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.security.Security

@SpringBootApplication
class SigningApplication

fun main(args: Array<String>) {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
    }
    runApplication<SigningApplication>(*args)
}
