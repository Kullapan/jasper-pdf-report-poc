package com.poc.pdf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PdfGeneratorApplication

fun main(args: Array<String>) {
    runApplication<PdfGeneratorApplication>(*args)
}
