#!/bin/bash
# =========================================================
#  复制模型文件到 Android assets 目录 (Mac/Linux)
# =========================================================
SRC_DIR="../model"
ASSETS_DIR="app/src/main/assets"

mkdir -p "$ASSETS_DIR"

cp -v "$SRC_DIR/model_quant.onnx" "$ASSETS_DIR/" 2>/dev/null || echo "[警告] model_quant.onnx 未找到"
cp -v "$SRC_DIR/tokens.json"      "$ASSETS_DIR/" 2>/dev/null || echo "[错误] tokens.json 未找到"
cp -v "$SRC_DIR/am.mvn"           "$ASSETS_DIR/" 2>/dev/null || echo "[错误] am.mvn 未找到"

echo ""
echo "完成！请在 Android Studio 中打开 android_app 目录。"
