@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0启动服务.ps1"
if errorlevel 1 (
  echo.
  echo [启动失败] 请把上方错误信息截图反馈后再关闭本窗口。
) else (
  echo.
  echo ========================================
  echo   启动成功！系统已在后台运行
  echo   访问地址: http://localhost:8086
  echo ========================================
)
pause
