@echo off
REM =========================================================
REM  复制模型文件到 Android assets 目录
REM =========================================================
echo.
echo 正在复制模型资源文件到 Android assets...
echo.

set SRC_DIR=..\model
set ASSETS_DIR=app\src\main\assets
if not exist "%ASSETS_DIR%" mkdir "%ASSETS_DIR%"

copy /Y "%SRC_DIR%\model_quant.onnx"  "%ASSETS_DIR%\" || echo [警告] model_quant.onnx 未找到
copy /Y "%SRC_DIR%\tokens.json"       "%ASSETS_DIR%\" || echo [错误] tokens.json 未找到
copy /Y "%SRC_DIR%\am.mvn"            "%ASSETS_DIR%\" || echo [错误] am.mvn 未找到

echo.
echo 完成！请在 Android Studio 中打开本目录 (android_app)。
echo 如果 assets 目录下缺少 model_quant.onnx，请手动复制。
echo.
pause
