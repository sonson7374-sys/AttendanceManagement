#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────
# 개발 환경 종료 스크립트
# 사용법:
#   ./scripts/dev-down.sh          # 컨테이너만 종료 (데이터 유지)
#   ./scripts/dev-down.sh --clean  # 컨테이너 + 볼륨 삭제 (DB 초기화)
# ────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

CLEAN=false
for arg in "$@"; do
  case $arg in
    --clean) CLEAN=true ;;
  esac
done

if $CLEAN; then
  echo "🗑️  컨테이너와 볼륨(DB 데이터)을 모두 삭제합니다..."
  docker compose down -v --remove-orphans
  echo "✅ 모든 데이터가 삭제됐습니다."
else
  echo "🛑 컨테이너를 종료합니다 (데이터 유지)..."
  docker compose down --remove-orphans
  echo "✅ 종료 완료. 데이터는 보존됩니다."
  echo "   데이터까지 삭제하려면: ./scripts/dev-down.sh --clean"
fi
