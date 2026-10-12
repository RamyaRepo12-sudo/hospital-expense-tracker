#!/usr/bin/env bash
# Builds the web app, copies it into the Spring Boot project and produces ONE runnable jar:
#   backend/target/hospital-expense-api.jar     (run with: java -jar ...  then open http://localhost:8080)
set -euo pipefail
cd "$(dirname "$0")/.."
(cd frontend && npm ci && npm run build)
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/* backend/src/main/resources/static/
(cd backend && mvn -B -q package -DskipTests)
echo "Done: backend/target/hospital-expense-api.jar"
