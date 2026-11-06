@echo off
rem Launch the Keyou class-group desktop client via Gradle (Compose Desktop run task).
rem ASCII-only comments: cmd reads this file in the OEM code page.
setlocal
set "ROOT=%~dp0.."
call "%ROOT%\gradlew.bat" -p "%ROOT%\desktop" run
