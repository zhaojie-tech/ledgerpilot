#!/usr/bin/env bash
# 全树密钥模式扫描（覆盖 apps/ 与 deploy/；CI 与本地共用）。
# 模式：OpenAI 风格 sk- 长串 + 智谱风格 hex32.alnum16+（gitleaks 通用规则抓不全后者）。
set -euo pipefail
cd "$(dirname "$0")/.."
PAT1="sk-[A-Za-z0-9]{20,}"
PAT2="[0-9a-f]{32}\.[A-Za-z0-9]{16,}"
EXCLUDES=(--exclude-dir=.git --exclude-dir=node_modules --exclude-dir=.venv --exclude-dir=venv --exclude-dir=dist --exclude-dir=target --exclude=scan_secrets.sh)
hits=0
for pat in "$PAT1" "$PAT2"; do
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    echo "SECRET HIT: $line"; hits=$((hits + 1))
  done < <(grep -rnIE "$pat" apps/ deploy/ scripts/ "${EXCLUDES[@]}" || true)
done
[ "$hits" -gt 0 ] && { echo "FAIL: $hits 处疑似密钥"; exit 1; }
echo "OK: 未发现密钥模式命中。"
