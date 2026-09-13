@echo off
setlocal enabledelayedexpansion
echo ========================================
echo   LazoDiscs Fabric - Build All Versions
echo ========================================
echo.
set BUILD_OK=1
for /d %%v in (1.21.*) do (
    echo [BUILD] Fabric %%v
    cd "%%v"
    call gradlew.bat build --no-daemon
    if !ERRORLEVEL! neq 0 (
        echo [FAILED] Fabric %%v
        set BUILD_OK=0
    ) else (
        echo [SUCCESS] Fabric %%v
    )
    cd ..
    echo.
)
echo ========================================
if !BUILD_OK! equ 1 (
    echo   All versions built successfully!
) else (
    echo   Some versions FAILED - check log above.
)
echo ========================================
pause
