@echo off
REM Builds SchemToCreate and leaves the standalone jar in build\libs\SchemToCreate.jar.
setlocal

cd /d "%~dp0.."

echo ==^> Running tests and building the standalone jar
call gradlew.bat clean build --console=plain
if errorlevel 1 goto :failed

if not exist "build\libs\SchemToCreate.jar" (
    echo Build finished but build\libs\SchemToCreate.jar is missing
    exit /b 1
)

echo.
echo ==^> Built build\libs\SchemToCreate.jar
echo     Run it with: java -jar build\libs\SchemToCreate.jar ^<input.schem^>
exit /b 0

:failed
echo Build failed.
exit /b 1
