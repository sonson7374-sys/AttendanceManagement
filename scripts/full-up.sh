#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────
# 전체 스택 실행 스크립트 (backend/admin-web 코드 필요)
# 사용법: ./scripts/full-up.sh
# ────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

if [ ! -f ".env" ]; then
  echo "⚠️  .env 파일이 없습니다. .env.example에서 복사 후 값을 입력하세요."
  exit 1
fi

echo "🚀 전체 스택 빌드 및 시작 (profile: full)..."
docker compose --profile full up -d --build

echo ""
echo "⏳ 서비스 상태 확인 중..."
sleep 10
docker compose --profile full ps

echo ""
echo "✅ 완료!"
echo ""
echo "   Admin Web:  http://localhost:${NGINX_HTTP_PORT:-80}"
echo "   API:        http://localhost:${NGINX_HTTP_PORT:-80}/api/v1"
echo "   Backend:    http://localhost:${BACKEND_PORT:-8080}"
