@echo off
chcp 65001 >nul
if "%~1"=="" (
    echo 用法: 将有道导出的 xml/txt/csv/db 文件拖到此脚本上，或运行:
    echo   adb_import_youdao.bat path\to\youdao_wordbook.xml
    echo   adb_import_youdao.bat --scan-phone
    pause
    exit /b 1
)
if "%~1"=="--scan-phone" (
    python "%~dp0adb_import_youdao.py" --scan-phone
) else (
    python "%~dp0adb_import_youdao.py" --file "%~1"
)
pause
