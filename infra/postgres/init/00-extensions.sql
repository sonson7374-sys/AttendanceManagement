-- PostgreSQL 초기화 스크립트
-- Docker 컨테이너 최초 기동 시 1회만 실행됩니다.

-- 위치 거리 계산용 (earth_distance, ll_to_earth)
-- Haversine 공식의 서버사이드 검증에 활용 가능
CREATE EXTENSION IF NOT EXISTS earthdistance CASCADE;

-- UUID 생성 함수
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 암호화 (비밀번호 해시 등 DB 레벨 암호화가 필요할 경우)
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 전문 검색 (감사 로그 검색 고도화 시 활성화)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
