#!/usr/bin/env bash
# LedgerPilot AI Service 启动脚本（开发用）
# 用法：先激活你的虚拟环境并 pip install -r requirements.txt，再运行本脚本；
# 生产部署建议直接 uvicorn app.main:app --host 0.0.0.0 --port 8000（多 worker 自行叠加）。

cd "$(dirname "$0")"

echo "=== LedgerPilot AI Service ==="
echo "Python: $(python --version)"
echo "Starting on http://localhost:8000"
echo "Docs: http://localhost:8000/docs"
echo ""

exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
