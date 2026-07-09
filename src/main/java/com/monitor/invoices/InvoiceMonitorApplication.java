package com.monitor.invoices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InvoiceMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceMonitorApplication.class, args);
    }
}
