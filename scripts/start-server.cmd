@echo off
rem Keyou server launcher: build WAR -> deploy ROOT -> start Tomcat on 8081.
rem Double-click to run. ASCII-only comments (cmd reads this file as GBK).
setlocal
set ROOT=%~dp0..
set TC=%ROOT%\tools\tomcat
set LOG=%ROOT%\tools\tomcat-start.log

echo === build war === > "%LOG%"
rem mvnw resolves .mvn/wrapper by CWD, so cd into server first
pushd "%ROOT%\server"
call mvnw.cmd -q package -DskipTests >> "%LOG%" 2>&1
set MVN_RC=%errorlevel%
popd
if not "%MVN_RC%"=="0" (
    echo BUILD FAILED, see %LOG%
    pause
    exit /b 1
)
if not exist "%ROOT%\server\target\keyou-server.war" (
    echo WAR NOT PRODUCED, see %LOG%
    pause
    exit /b 1
)
copy /y "%ROOT%\server\target\keyou-server.war" "%TC%\webapps\ROOT.war" >> "%LOG%" 2>&1
if exist "%TC%\webapps\ROOT" rmdir /s /q "%TC%\webapps\ROOT"

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "CATALINA_HOME=%TC%"
set "CATALINA_BASE=%TC%"
call "%TC%\bin\catalina.bat" stop >nul 2>&1
ping -n 3 127.0.0.1 >nul
call "%TC%\bin\catalina.bat" start >> "%LOG%" 2>&1

rem Wait for /api/ping (MySQL connectivity included)
set /a n=0
:wait
ping -n 3 127.0.0.1 >nul
curl -s -m 3 http://127.0.0.1:8081/api/ping >nul 2>&1
if %errorlevel%==0 (
    echo.
    echo server ready: http://127.0.0.1:8081/api/ping
    curl -s -m 3 http://127.0.0.1:8081/api/ping
    echo.
    pause
    exit /b 0
)
set /a n+=1
if %n% lss 20 goto wait
echo server NOT ready after 60s, see %TC%\logs\catalina.*.log
pause
exit /b 1
