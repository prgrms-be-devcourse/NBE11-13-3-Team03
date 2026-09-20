package com.team3.gudit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
class GuditApplication
fun main(args: Array<String>) {
    runApplication<GuditApplication>(*args)
}