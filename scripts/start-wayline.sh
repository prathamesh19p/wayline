#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  if [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  fi
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "Java 21 is required. Set JAVA_HOME to a JDK 21 installation." >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required. Install Maven 3.8+ and run this script again." >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

java_major="$($JAVA_HOME/bin/java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)"
if [[ "$java_major" != "21" ]]; then
  echo "Java 21 is required; found Java $java_major." >&2
  exit 1
fi

if command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
elif docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
else
  echo "Docker Compose is required. Install Docker Desktop or docker-compose." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  if command -v colima >/dev/null 2>&1; then
    echo "Docker daemon is unavailable; starting Colima..."
    colima start \
      --runtime docker \
      --cpu "${COLIMA_CPU:-4}" \
      --memory "${COLIMA_MEMORY:-8}" \
      --disk "${COLIMA_DISK:-60}"
  else
    echo "Docker is installed but the Docker daemon is not running." >&2
    echo "Start Docker Desktop or install Colima, then run this script again." >&2
    exit 1
  fi
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon did not become available after starting the configured runtime." >&2
  exit 1
fi

: "${WAYLINE_AUTH_USERNAME:=merchant-demo}"
: "${WAYLINE_AUTH_PASSWORD:=change-this-password}"
: "${JWT_SECRET:=wayline-local-jwt-secret-change-for-shared-environments}"
: "${WAYLINE_WEBHOOK_SECRET:=wayline-local-webhook-secret}"
export WAYLINE_AUTH_USERNAME WAYLINE_AUTH_PASSWORD JWT_SECRET WAYLINE_WEBHOOK_SECRET

printf 'Starting Wayline infrastructure...\n'
"${COMPOSE[@]}" up -d

printf 'Building Wayline modules...\n'
mvn -pl wayline-app -am clean package -DskipTests

printf '\nWayline is starting at http://localhost:8080\n'
printf 'Demo username: %s\n' "$WAYLINE_AUTH_USERNAME"
printf 'Run Ctrl+C to stop the application. Docker services remain running.\n\n'

exec mvn -pl wayline-app spring-boot:run
