#!/usr/bin/env bash
# 全树密钥模式扫描（CI 与本地自查共用）。
#
# 背景：本仓库历史版本曾内置两把默认 API key（DeepSeek 的 sk- 长串格式、
# 智谱的 hex32.alnum16 格式）。gitleaks 通用规则抓不全后者，这里双模式兜底。
# 命中即退出非零。
set -euo pipefail
cd "$(dirname "$0")/.."

PAT1="sk-[A-Za-z0-9]{20,}"                    # OpenAI 风格长串
PAT2="[0-9a-f]{32}\.[A-Za-z0-9]{16,}"         # 智谱风格 hex32.alnum16+

# 排除扫描器自身（内含模式字面量）；-I 跳过二进制
EXCLUDES=(--exclude-dir=.git --exclude-dir=.venv --exclude-dir=venv --exclude=scan_secrets.sh)

hits=0
for pat in "$PAT1" "$PAT2"; do
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    echo "SECRET HIT: $line"
    hits=$((hits + 1))
  done < <(grep -rnIE "$pat" . "${EXCLUDES[@]}" || true)
done

if [ "$hits" -gt 0 ]; then
  echo "FAIL: 发现 $hits 处疑似密钥，禁止提交。" >&2
  exit 1
fi
echo "OK: 未发现密钥模式命中（sk- 长串 / hex32.alnum16+）。"
