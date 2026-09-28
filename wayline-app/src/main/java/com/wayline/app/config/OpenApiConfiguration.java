package com.wayline.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI waylineOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Wayline Payment Orchestration API")
                .version("1.0.0")
                .description("""
                    Wayline routes payments across multiple providers, records every movement of \
                    money in an immutable double-entry ledger, and reconciles that ledger against \
                    provider settlement files.

                    ## Authentication
                    All endpoints except `/api/v1/auth/login` and `/api/v1/webhooks/**` require a \
                    bearer token. Obtain one from `POST /api/v1/auth/login` and send it as \
                    `Authorization: Bearer <token>`.

                    ## Idempotency
                    `POST /api/v1/payments` requires an `Idempotency-Key` header. Replaying a key \
                    returns the original payment rather than creating a second one. Reusing a key \
                    with a different amount, currency or payment method is rejected with `400`.

                    ## Money
                    All amounts are integers in the minor unit of the given currency (paise for \
                    INR, cents for USD). `12500` with currency `INR` means 125.00 INR. Floating \
                    point is never used for monetary values.

                    ## Payment states
                    `CREATED` -> `PROCESSING` -> `SUCCESS` | `FAILED` | `UNKNOWN`.
                    `UNKNOWN` means the provider did not answer in time and the true outcome is \
                    not yet established; it is deliberately not treated as a failure. A later \
                    webhook resolves it to `SUCCESS` or `FAILED`. Terminal states never change.""")
                .contact(new Contact().name("Wayline").url("https://github.com/prathameshphalke/wayline"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .servers(List.of(new Server().url("http://localhost:8080").description("Local")))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT issued by POST /api/v1/auth/login")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
