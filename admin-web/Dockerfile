# ════════════════════════════════════════
# Stage 1: Build (Node)
# ════════════════════════════════════════
FROM node:20-alpine AS builder

WORKDIR /app

# 의존성 캐시 레이어 분리
COPY package.json package-lock.json* yarn.lock* pnpm-lock.yaml* ./
RUN \
  if [ -f package-lock.json ]; then npm ci --prefer-offline; \
  elif [ -f yarn.lock ]; then yarn install --frozen-lockfile; \
  elif [ -f pnpm-lock.yaml ]; then corepack enable pnpm && pnpm install --frozen-lockfile; \
  else npm install; \
  fi

# 소스 복사 및 빌드
COPY . .
# 빌드 시 환경변수 주입 (.env 파일이 아닌 ARG/ENV로 처리)
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_MAP_PROVIDER=kakao
ARG VITE_MAP_API_KEY

ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_MAP_PROVIDER=$VITE_MAP_PROVIDER
ENV VITE_MAP_API_KEY=$VITE_MAP_API_KEY

RUN npm run build

# ════════════════════════════════════════
# Stage 2: Runtime (Nginx)
# ════════════════════════════════════════
FROM nginx:1.27-alpine AS runtime

# 기본 nginx 설정 제거 후 SPA 설정 복사
RUN rm -rf /etc/nginx/conf.d/default.conf
COPY infra/nginx/conf.d/spa.conf /etc/nginx/conf.d/default.conf

# 빌드 결과물 복사
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
