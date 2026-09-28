# wayline-app

The deployable unit. It contains no business logic — only bootstrap, configuration and the
cross-cutting HTTP concerns.

| Class | Role |
|---|---|
| `WaylineApplication` | Entry point; component scan root for `com.wayline`. |
| `SecurityConfiguration` | Stateless JWT filter chain, role rules, CORS, security headers. |
| `OpenApiConfiguration` | OpenAPI document metadata and the bearer security scheme. |
| `MetricsConfiguration` | Micrometer / Prometheus wiring. |
| `AuthenticationController` | `POST /api/v1/auth/login`. |
| `ApiExceptionHandler` | Maps domain exceptions to a consistent `ApiError` body. |

## Configuration

`application.yml` holds the defaults. Everything environment-specific is an environment
variable. `JWT_SECRET` has **no default** — the application refuses to start without it, rather
than silently running with a shipped key.

Database migrations live in `src/main/resources/db/migration` and are applied by Flyway.
Hibernate runs with `ddl-auto: validate`, so a drift between entities and migrations fails
startup instead of silently altering a production schema.

## API documentation

With the application running:

- Swagger UI — http://localhost:8080/swagger-ui.html
- OpenAPI JSON — http://localhost:8080/v3/api-docs
