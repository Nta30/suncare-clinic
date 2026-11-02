package com.suncare.clinic.modules.account.config;

import com.suncare.clinic.modules.account.service.AdminBootstrapService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "suncare.bootstrap.admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements CommandLineRunner {
    private final AdminBootstrapService service;

    public AdminBootstrapRunner(AdminBootstrapService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        service.bootstrap(
                System.getenv("SUNCARE_ADMIN_LOGIN"),
                System.getenv("SUNCARE_ADMIN_PASSWORD"),
                System.getenv("SUNCARE_ADMIN_NAME"));
    }
}
