#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────
# 개발 환경 시작 스크립트 (PostgreSQL + Redis)
# 사용법: ./scripts/dev-up.sh
# ────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# .env 파일 확인
if [ ! -f ".env" ]; then
  echo "⚠️  .env 파일이 없습니다. .env.example에서 복사합니다..."
  cp .env.example .env
  echo "✅ .env 파일이 생성됐습니다. 값을 확인하고 필요시 수정하세요."
  echo ""
fi

echo "🚀 개발 DB 환경 시작 (PostgreSQL + Redis)..."
docker compose up -d postgres redis

echo ""
echo "⏳ 서비스 상태 확인 중..."
sleep 5
docker compose ps postgres redis

echo ""
echo "✅ 완료!"
echo ""
echo "   PostgreSQL: localhost:${POSTGRES_PORT:-5432}  DB: ${POSTGRES_DB:-attendance}"
echo "   Redis:      localhost:${REDIS_PORT:-6379}"
echo ""
echo "   Backend 로컬 실행:"
echo "   cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'"
echo ""
echo "   Admin Web 로컬 실행:"
echo "   cd admin-web && npm run dev"
