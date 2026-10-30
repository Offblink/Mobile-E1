#!/bin/bash
# Rebuild WAR, redeploy as ROOT, restart the project Tomcat (8081), probe /api/ping.
# Usage: bash scripts/deploy.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/tools/tomcat"
TC_WIN=$(cygpath -w "$TC")

cd "$ROOT/server"
MSYS_NO_PATHCONV=1 cmd.exe /c "mvnw.cmd -q package -DskipTests" > /c/tmp/scratch/exp1/mvn.log 2>&1 || { tail -20 /c/tmp/scratch/exp1/mvn.log; exit 1; }

cp target/keyou-server.war "$TC/webapps/ROOT.war"
rm -rf "$TC/webapps/ROOT"

# stop old instance if running (SHUTDOWN port 8006), then start fresh
MSYS_NO_PATHCONV=1 cmd.exe /c "set CATALINA_HOME=$TC_WIN&& set CATALINA_BASE=$TC_WIN&& $TC_WIN\bin\catalina.bat stop" > /c/tmp/scratch/exp1/tc-stop.log 2>&1 || true
sleep 3

MSYS_NO_PATHCONV=1 cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& set CATALINA_HOME=$TC_WIN&& set CATALINA_BASE=$TC_WIN&& $TC_WIN\bin\catalina.bat start" > /c/tmp/scratch/exp1/tc-start.log 2>&1

for i in $(seq 1 20); do
  sleep 2
  R=$(curl -s -m 3 http://127.0.0.1:8081/api/ping || true)
  if echo "$R" | grep -q '"code"'; then
    echo "READY: $R"
    exit 0
  fi
done
echo "NOT READY. Last response: $R"
echo "--- catalina log tail ---"
tail -30 "$TC/logs/"catalina.$(date +%Y-%m-%d).log || true
exit 1
