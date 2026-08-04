@echo off
REM Opens the SchemToCreate window.
REM
REM Uses javaw instead of java so no console window sits behind the app. Copy this file next
REM to SchemToCreate.jar (or edit the path below) and make a desktop shortcut to it.
setlocal

set "JAR=%~dp0..\build\libs\SchemToCreate.jar"
if not exist "%JAR%" set "JAR=%~dp0SchemToCreate.jar"

if not exist "%JAR%" (
    echo Could not find SchemToCreate.jar next to this script or in build\libs.
    echo Run scripts\build.bat first.
    pause
    exit /b 1
)

start "" javaw -jar "%JAR%" %*
