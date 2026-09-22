# Wayline Quick Start Guide

## Prerequisites
- Java 21 (or later)
- Maven 3.8+
- Docker-compatible runtime: Colima + Docker Compose, Docker Desktop, or another OCI runtime
- PostgreSQL client (optional, for manual queries)

## First Time Setup

### One-Command Startup
After installing Java 21, Maven 3.8+, and a Docker-compatible runtime, start the complete application with:

```bash
./scripts/start-wayline.sh
```

The script starts PostgreSQL, Redis, Kafka, Prometheus, Grafana, and Jaeger, builds the Maven modules, and launches the API on `http://localhost:8080`. It uses local development credentials unless you provide `WAYLINE_AUTH_USERNAME`, `WAYLINE_AUTH_PASSWORD`, `JWT_SECRET`, and `WAYLINE_WEBHOOK_SECRET` in the environment.

On macOS, Colima can replace Docker Desktop:

```bash
brew install colima docker-compose
colima start --runtime docker --cpu 4 --memory 8 --disk 60
./scripts/start-wayline.sh
```

### 1. Start Infrastructure
```bash
cd /Users/prathameshphalke/pp_github/wayline

# Start all services (PostgreSQL, Kafka, Redis, Prometheus, Grafana, Jaeger)
docker-compose up -d

# Verify services are running
docker-compose ps
```

Expected output: All services should be in "Up" state.

### 2. Verify Database Connection
```bash
# Test PostgreSQL connection
psql -h localhost -U wayline -d wayline -c "SELECT 1"
# When prompted for password, enter: wayline_password
```

### 3. Build Project
```bash
cd /Users/prathameshphalke/pp_github/wayline

# Build all modules (downloads dependencies first time)
mvn clean install

# This will:
# - Download all Maven dependencies
# - Compile all modules
# - Run basic checks
# - Create JAR files in each module's target/ directory
```

Time estimate: 5-10 minutes for first build (dependency download)

### 4. Configure Application Credentials
The application requires configured credentials for `/api/v1/auth/login`; it does not accept arbitrary credentials.

```bash
export WAYLINE_AUTH_USERNAME=merchant-demo
export WAYLINE_AUTH_PASSWORD='change-this-password'
export JWT_SECRET='replace-with-a-long-random-secret'
export WAYLINE_WEBHOOK_SECRET='replace-with-webhook-secret'
```

Keep these variables in the same terminal used to start the application.

### 5. Start Application
```bash
cd wayline-app

# Start Spring Boot application
mvn spring-boot:run

# Application will start on http://localhost:8080
# Flyway will automatically run database migrations
```

Expected output (at end of logs):
```
Started WaylineApplication in X.XXX seconds (process running for X.XXX)
```

### 6. Verify Application is Running

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Should return: {"status":"UP"}

# Check available metrics
curl http://localhost:8080/actuator/metrics

# View Prometheus metrics (for Grafana)
curl http://localhost:8080/actuator/prometheus
```

### 7. Authenticate and Create a Payment
```bash
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"merchant-demo","password":"change-this-password"}' \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

curl -i http://localhost:8080/api/v1/payments \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Idempotency-Key: demo-payment-1' \
    -H 'Content-Type: application/json' \
    -d '{"amount":1000,"currency":"INR","paymentMethod":"CARD","description":"Demo payment"}'
```

### 6. Access External Tools

| Tool | URL | Credentials |
|------|-----|-------------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Jaeger | http://localhost:16686 | - |

## Development Workflow

### Running Tests
```bash
cd /Users/prathameshphalke/pp_github/wayline

# Run all unit tests
mvn test

# Run unit + integration tests
mvn verify

# Run tests for specific module
mvn -pl wayline-payment test
```

### Building Specific Module
```bash
# Build and test wayline-payment only
mvn -pl wayline-payment clean install

# Build without tests
mvn -pl wayline-payment clean install -DskipTests
```

### Database Migrations
```bash
# View migration history
psql -h localhost -U wayline -d wayline -c "SELECT * FROM flyway_schema_history;"

# Migrations are auto-applied on application startup
# They're located in: wayline-app/src/main/resources/db/migration/
```

### Debugging
```bash
# Enable debug logging
export LOGGING_LEVEL_ROOT=DEBUG
export LOGGING_LEVEL_COM_WAYLINE=TRACE

mvn -pl wayline-app spring-boot:run
```

### Stopping Services
```bash
# Stop application
# Ctrl+C in the terminal

# Stop all Docker services
cd /Users/prathameshphalke/pp_github/wayline
docker compose down

# Stop and remove all data
docker compose down -v
```

## Project Structure Quick Reference

```
wayline/
├── pom.xml                    # Root POM (dependency management)
├── docker-compose.yml         # Infrastructure setup
├── README.md                  # Full documentation
├── IMPLEMENTATION_ROADMAP.md  # Phased implementation plan
│
├── wayline-common/            # Shared utilities & events
├── wayline-payment/           # Payment lifecycle (current focus)
├── wayline-routing/           # Provider routing
├── wayline-provider/          # Provider abstractions
├── wayline-ledger/            # Double-entry ledger
├── wayline-settlement/        # Settlement processing
├── wayline-reconciliation/    # Reconciliation engine
├── wayline-notification/      # Notifications & callbacks
│
└── wayline-app/               # Main application
    ├── src/main/
    │   ├── java/com/wayline/WaylineApplication.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/  # Flyway migrations
    └── pom.xml
```

## Key Ports
| Service | Port | Purpose |
|---------|------|---------|
| Application | 8080 | REST API & Actuator |
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache & Coordination |
| Kafka | 9092 | Event Streaming |
| Prometheus | 9090 | Metrics Collection |
| Grafana | 3000 | Metrics Visualization |
| Jaeger | 16686 | Distributed Tracing |

## Common Issues & Solutions

### Issue: `Connection refused to PostgreSQL`
**Solution**: Ensure Docker is running and postgres service is up
```bash
docker-compose ps  # Check postgres status
docker-compose logs postgres  # View postgres logs
```

### Issue: `Port 5432 already in use`
**Solution**: Stop existing PostgreSQL or change port in docker-compose.yml
```bash
lsof -i :5432  # Find process using port
kill -9 <PID>  # Kill the process
```

### Issue: `Maven build fails with dependency errors`
**Solution**: Clear Maven cache and retry
```bash
rm -rf ~/.m2/repository
mvn clean install
```

### Issue: `Application fails to start - migration error`
**Solution**: Check database is accessible and clean
```bash
docker-compose logs postgres  # Check postgres logs
psql -h localhost -U wayline -d wayline -c "DROP TABLE IF EXISTS flyway_schema_history CASCADE;"
```

### Issue: `Kafka not starting`
**Solution**: Zookeeper must start first
```bash
docker-compose down
docker-compose up -d zookeeper
sleep 5
docker-compose up -d  # Start all services
```

## Next Development Tasks

See [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) for detailed phases.

**Current Status**: Core implementation complete. `mvn clean test` passes on Java 21.

Integration tests use Testcontainers and require Docker to be running.

## Useful Commands Reference

```bash
# Build and run
cd /Users/prathameshphalke/pp_github/wayline
mvn clean install && mvn -pl wayline-app spring-boot:run

# Build specific module
mvn -pl wayline-payment clean install

# Run tests for module
mvn -pl wayline-payment test

# View application logs in real-time
tail -f wayline-app/target/application.log

# Query database directly
psql -h localhost -U wayline -d wayline

# Check Kafka topics
docker exec wayline-kafka kafka-topics --list --bootstrap-server localhost:9092

# View Kafka consumer groups
docker exec wayline-kafka kafka-consumer-groups --list --bootstrap-server localhost:9092

# Clear all Docker volumes (WARNING: loses all data)
docker-compose down -v

# View docker-compose logs
docker-compose logs -f  # All services
docker-compose logs -f postgres  # Specific service
```

## Git Workflow

```bash
cd /Users/prathameshphalke/pp_github/wayline

# Initialize git (if not already done)
git init
git add .
git commit -m "Initial Wayline payment platform setup"

# Create a feature branch for future changes when working in a Git repository
# git checkout -b feature/<name>
```

## Documentation

- **README.md**: Full architecture and features guide
- **IMPLEMENTATION_ROADMAP.md**: Detailed implementation phases
- **This file**: Quick start and common commands

## Performance Tips

1. **First build is slow**: Download happens once, subsequent builds are faster
2. **Database queries**: Use indexes already configured in schema
3. **Kafka throughput**: Adjust `max-poll-records` in application.yml
4. **Redis caching**: Configured for idempotency optimization
5. **Connection pools**: Set in application.yml (hikari for PostgreSQL)

## Support & Debugging

Enable detailed logging:
```bash
# In application.yml or via environment
LOGGING_LEVEL_COM_WAYLINE=DEBUG
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG
LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG
```

Check module dependencies:
```bash
mvn dependency:tree -pl wayline-payment
```

## Getting Help

1. Check README.md for architecture details
2. Check IMPLEMENTATION_ROADMAP.md for development plan
3. Review existing code in wayline-payment module
4. Check application logs: `docker-compose logs -f`
5. View database with: `psql -h localhost -U wayline -d wayline`
