# Operational Runbook

This document provides operational procedures for running and troubleshooting Wayline Payment Platform in production.

## Table of Contents
1. [Deployment & Startup](#deployment--startup)
2. [Monitoring & Alerting](#monitoring--alerting)
3. [Troubleshooting](#troubleshooting)
4. [Incident Response](#incident-response)
5. [Maintenance](#maintenance)

---

## Deployment & Startup

### Prerequisites
- Docker & Docker Compose installed
- PostgreSQL 15+
- Redis 7+
- Apache Kafka 7.5+
- Java 21 JDK
- Maven 3.8+

### Starting the Full Stack

```bash
# Clone repository
git clone <repo-url>
cd wayline

# Start infrastructure (PostgreSQL, Redis, Kafka, Prometheus, Grafana, Jaeger)
docker-compose up -d

# Wait for services to start (5-10 seconds)
sleep 10

# Build application
mvn clean package -DskipTests

# Run application
java -jar wayline-app/target/wayline-app-1.0.0.jar
```

Application will be available at `http://localhost:8080`.

### Health Checks

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check specific components
curl http://localhost:8080/actuator/health/db
curl http://localhost:8080/actuator/health/redis
curl http://localhost:8080/actuator/health/kafka

# Expected response
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "kafkaProducer": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

---

## Monitoring & Alerting

### Key Metrics to Monitor

1. **Payment Processing**
   - `payment_created_count` - New payments
   - `payment_succeeded_count` - Successful payments
   - `payment_failed_count` - Failed payments
   - `payment_unknown_count` - Unknown status payments
   - Alert if success rate drops below 95%

2. **Provider Health**
   - `provider_request_count` - Requests to providers
   - `provider_success_count` - Successful provider calls
   - `provider_failure_count` - Failed provider calls
   - Alert if any provider circuit breaker opens

3. **System Health**
   - `outbox_pending_count` - Unprocessed outbox events (should be < 100)
   - `reconciliation_mismatch_count` - Unresolved mismatches (should be 0)
   - `kafka_message_dead_letter_count` - Dead letter messages
   - Alert if any of these exceed thresholds

4. **Application Performance**
   - Payment creation latency (p95 < 1s, p99 < 2s)
   - Provider call latency (p95 < 5s, p99 < 10s)
   - Database query latency (p95 < 100ms)
   - JVM heap usage (alert > 80%)

### Grafana Dashboard
- Access: http://localhost:3000 (admin/admin)
- Dashboard: "Wayline Payment Platform - Observability Dashboard"
- Panels show real-time metrics and alerts

### Prometheus
- Access: http://localhost:9090
- Scrape target: localhost:8080/actuator/prometheus
- Query examples:
  ```promql
  rate(payment_succeeded_count_total[5m])
  histogram_quantile(0.95, payment_latency_seconds_bucket)
  outbox_pending_count
  ```

---

## Troubleshooting

### Issue: Application Won't Start

**Symptoms:** Exit code 1, connection errors in logs

**Diagnosis:**
```bash
# Check Docker containers running
docker-compose ps

# Check container logs
docker-compose logs postgres
docker-compose logs redis
docker-compose logs kafka

# Check port availability
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9092  # Kafka
```

**Solution:**
- Ensure all infrastructure containers are running
- Check `docker-compose logs` for error messages
- Restart containers: `docker-compose restart`
- Rebuild images if needed: `docker-compose down && docker-compose up -d`

---

### Issue: Payments Stuck in PROCESSING

**Symptoms:** Payments not transitioning to SUCCESS/FAILED/UNKNOWN, users report delays

**Root Causes:**
1. Provider timeout/circuit breaker open
2. Kafka not processing events
3. Database connection pool exhausted
4. Webhook from provider not received

**Diagnosis:**
```bash
# Check provider health
curl http://localhost:8080/api/v1/admin/providers/health

# Check outbox pending events
curl -X GET http://localhost:8080/api/v1/admin/outbox/pending

# Check Kafka consumer lag
docker exec wayline-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group wayline-consumers \
  --describe

# Check database connections
psql -h localhost -U wayline -d wayline -c "SELECT count(*) FROM pg_stat_activity;"
```

**Solutions:**
1. **Provider circuit breaker open:**
   - Check provider logs for errors
   - Wait 60 seconds for circuit to auto-reset
   - Or restart provider if issue resolved: `POST /api/v1/admin/providers/{id}/reset`

2. **Kafka lag high:**
   - Check consumer logs: `docker-compose logs | grep "consumer\|listener"`
   - Restart consumer: `docker-compose restart wayline-app`
   - Check Kafka disk space

3. **Database connections exhausted:**
   - Increase connection pool: `spring.datasource.hikari.maximum-pool-size` in application.yml
   - Restart application
   - Investigate slow queries: `SELECT * FROM pg_stat_statements ORDER BY total_time DESC LIMIT 10;`

4. **Webhook not received:**
   - Check webhook logs for failed deliveries
   - Retry webhook manually: `POST /api/v1/webhooks/{id}/retry`
   - Check provider logs for webhook send status

---

### Issue: High Latency (p95 > 1s)

**Symptoms:** Slow payment creation, timeouts, user complaints

**Root Causes:**
1. Database slow queries
2. Provider timeout
3. Kafka slowness
4. Redis connection issues
5. Network latency

**Diagnosis:**
```bash
# Check slow queries
psql -h localhost -U wayline -d wayline -c "
  SELECT query, calls, total_time, mean_time 
  FROM pg_stat_statements 
  ORDER BY mean_time DESC LIMIT 10;"

# Check provider latency
curl -X GET "http://localhost:8080/api/v1/admin/providers/metrics"

# Check application metrics
curl http://localhost:8080/actuator/metrics/http.server.requests

# Check Redis latency
redis-cli --latency
```

**Solutions:**
1. **Database slow queries:**
   - Analyze query plan: `EXPLAIN ANALYZE <query>`
   - Add index on frequently filtered columns
   - Increase work_mem in PostgreSQL config

2. **Provider timeout:**
   - Check provider API status
   - Increase timeout: `wayline.provider.timeout-seconds` (default 10s)
   - Switch to faster provider via ProviderSelectorService

3. **Kafka lag:**
   - Increase consumer threads: `spring.kafka.consumer.max-poll-records`
   - Check broker disk space
   - Increase `num.partitions` for topics

---

### Issue: Reconciliation Mismatches

**Symptoms:** Reconciliation mismatches not resolved, money tracking issues

**Root Causes:**
1. Settlement file parsing error
2. Amount mismatch between payment and settlement
3. Payment status mismatch
4. Duplicate payments in settlement

**Diagnosis:**
```bash
# Check mismatches
curl -X GET "http://localhost:8080/api/v1/reconciliation/mismatches"

# Check specific settlement reconciliation
curl -X GET "http://localhost:8080/api/v1/reconciliation/settlement/{settlementId}"

# Query database
psql -h localhost -U wayline -d wayline -c "
  SELECT * FROM reconciliation_records 
  WHERE status = 'MISMATCH' 
  LIMIT 10;"
```

**Solutions:**
1. **Amount mismatch:**
   - Check payment details: `GET /api/v1/payments/{paymentId}`
   - Check settlement record in provider system
   - Likely fee discrepancy - resolve with amount correction

2. **Status mismatch:**
   - Check payment state history: `GET /api/v1/payments/{paymentId}/timeline`
   - Reprocess webhook from provider if needed
   - Manually update payment status if provider confirmed

3. **Duplicates:**
   - Check settlement reference uniqueness
   - Remove duplicate from settlement file
   - Re-run reconciliation: `POST /api/v1/reconciliation/settlements/{settlementId}/run`

---

## Incident Response

### Critical Incident: Complete Payment Processing Failure

**Symptoms:** No payments processing, application errors, downstream services affected

**Immediate Actions (0-5 minutes):**
1. Declare incident in incident channel
2. Check application health: `GET /actuator/health`
3. Check infrastructure: `docker-compose ps`
4. Get logs: `docker-compose logs --tail=100`
5. Engage on-call database and Kafka engineers

**Investigation (5-15 minutes):**
1. Identify failure point:
   - Is application running? (Check process, logs)
   - Is database up? (Check connectivity, no locks)
   - Is Kafka up? (Check broker, topics)
   - Is Redis up? (Check connectivity)

2. Common causes:
   - Database: `SELECT pg_database.datname FROM pg_database WHERE datname='wayline';`
   - Kafka: `docker exec wayline-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list`
   - Redis: `redis-cli ping`

**Recovery (15-30 minutes):**
- **If database is down:** Failover to replica or restore from backup
- **If Kafka is down:** Restart broker, reassign partitions
- **If Redis is down:** Failover or use application-level cache as fallback
- **If application is deadlocked:** Kill and restart
- Verify recovery with smoke tests

**Post-Incident (30+ minutes):**
1. Stop incident work
2. Immediate fix for critical bugs
3. Schedule postmortem within 24 hours
4. Implement monitoring to prevent recurrence

---

## Maintenance

### Regular Maintenance Windows

**Daily (automated):**
- Outbox event cleanup (keep 7 days)
- Processed event cleanup (keep 30 days)
- Metrics export
- Backup database

**Weekly:**
- Verify all provider integrations
- Review reconciliation mismatches
- Check circuit breaker states
- Analyze slow query logs

**Monthly:**
- Index fragmentation analysis
- Kafka partition rebalancing
- Update security patches
- Capacity planning review

### Database Maintenance

```bash
# Connect to database
psql -h localhost -U wayline -d wayline

# Check database size
SELECT pg_database.datname, pg_size_pretty(pg_database_size(pg_database.datname)) AS size
FROM pg_database ORDER BY pg_database_size(pg_database.datname) DESC;

# Vacuum and analyze (recommended after large deletions)
VACUUM ANALYZE;

# Check index bloat
SELECT schemaname, tablename, indexname, round(100.0 * (OTTA - ROUND(cc*ma)) / (cc*ma)) AS pct_waste
FROM pgstatindex('payment_pkey')
ORDER BY pct_waste DESC;

# Reindex if needed
REINDEX TABLE payments;
```

### Scaling Considerations

**Horizontal Scaling:**
- Multiple application instances behind load balancer
- Share PostgreSQL (connection pooling with pgBouncer)
- Share Redis (cluster mode for HA)
- Share Kafka (automatic rebalancing)

**Vertical Scaling:**
- Increase JVM heap: `export JAVA_OPTS="-Xmx4g -Xms2g"`
- Increase database `shared_buffers`
- Increase connection pool size
- Increase Kafka broker memory

**Monitoring During Scaling:**
- CPU usage: Should stay < 70%
- Memory usage: Should stay < 80%
- Database connections: Should stay < 80% of pool
- Kafka lag: Should be < 1000 messages

---

## Emergency Contacts

- On-Call Engineer: Check PagerDuty
- Database Team: #database-oncall Slack
- Payments Team Lead: @payments-lead
- Platform Engineering: #platform-eng Slack
- Executive Escalation: VP Engineering

## Related Documentation

- [Architecture Decision Records](./ADR.md)
- [API Documentation](./API.md)
- [Database Schema](./DATABASE.md)
- [Performance Tuning](./PERFORMANCE.md)
