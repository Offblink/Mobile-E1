@echo off
rem Launch the desktop client (javaw = no console window).
rem Builds first when out\ is missing.
setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "HERE=%~dp0"

if not exist "%HERE%out\com\offblink\keyou\desktop\ClassGroupClient.class" (
    echo [hint] classes not built yet, running build.cmd ...
    call "%HERE%build.cmd"
    if errorlevel 1 (
        echo.
        echo Build failed. See messages above.
        pause
        exit /b 1
    )
)

start "" "%JAVA_HOME%\bin\javaw.exe" -cp "%HERE%out" com.offblink.keyou.desktop.ClassGroupClient
exit /b 0
