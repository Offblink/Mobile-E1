@echo off
rem Build the Keyou class-group desktop client (Kotlin + Compose Multiplatform).
rem Uses the repository Gradle wrapper, so no separate Gradle install is needed.
rem ASCII-only comments: cmd reads this file in the OEM code page.
setlocal
set "ROOT=%~dp0.."
echo === gradle -p desktop compileKotlin ===
call "%ROOT%\gradlew.bat" -p "%ROOT%\desktop" compileKotlin --console=plain
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)
echo.
echo BUILD OK
exit /b 0
