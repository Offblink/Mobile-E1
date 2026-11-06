@echo off
rem Build the Keyou class-group desktop client.
rem Pure javac, no Maven / Gradle / third-party jars.
rem ASCII-only comments: cmd reads this file in the OEM code page.
setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "HERE=%~dp0"
set "SRC=%HERE%src\main\java\com\offblink\keyou\desktop"
set "OUT=%HERE%out"

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] javac not found: "%JAVA_HOME%\bin\javac.exe"
    exit /b 1
)
if not exist "%OUT%" mkdir "%OUT%"

echo === javac ^(UTF-8^) ===
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -d "%OUT%" ^
    "%SRC%\Json.java" ^
    "%SRC%\Api.java" ^
    "%SRC%\ClassGroupClient.java"

if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)

echo.
echo BUILD OK -^> %OUT%
exit /b 0
