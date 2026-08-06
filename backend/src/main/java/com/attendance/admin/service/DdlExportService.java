package com.attendance.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * DDL 내보내기 서비스
 * Flyway 마이그레이션 파일들을 읽어서 하나의 DDL 파일로 통합하여 다운로드할 수 있게 함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdlExportService {

    private static final String MIGRATION_PATH = "db/migration";

    /**
     * 모든 Flyway 마이그레이션 파일의 DDL을 하나의 문자열로 결합하여 반환
     *
     * @return 모든 마이그레이션의 DDL이 포함된 문자열
     */
    public String exportAllDDL() {
        StringBuilder ddlBuilder = new StringBuilder();
        
        // 헤더 추가
        ddlBuilder.append("-- ============================================\n");
        ddlBuilder.append("-- GPS 지오펜스 출퇴근 관리 시스템 DDL\n");
        ddlBuilder.append("-- 생성일시: ").append(java.time.LocalDateTime.now()).append("\n");
        ddlBuilder.append("-- ============================================\n\n");
        
        try {
            // 마이그레이션 파일들을 버전 순으로 정렬하여 읽기
            ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
            if (!resource.exists()) {
                log.warn("마이그레이션 경로를 찾을 수 없습니다: {}", MIGRATION_PATH);
                ddlBuilder.append("-- 마이그레이션 파일을 찾을 수 없습니다.\n");
                return ddlBuilder.toString();
            }

            Path migrationPath = Paths.get(resource.getURL().getPath());
            
            // V__*.sql 패턴의 파일만 필터링하여 정렬
            try (Stream<Path> paths = Files.walk(migrationPath)) {
                List<Path> migrationFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().matches("^V\\d+__.*\\.sql$"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();

                log.info("Found {} migration files", migrationFiles.size());

                for (Path file : migrationFiles) {
                    String fileName = file.getFileName().toString();
                    ddlBuilder.append("\n-- ============================================\n");
                    ddlBuilder.append("-- Migration: ").append(fileName).append("\n");
                    ddlBuilder.append("-- ============================================\n\n");
                    
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    ddlBuilder.append(content);
                    ddlBuilder.append("\n");
                }
            }
        } catch (IOException e) {
            log.error("마이그레이션 파일 읽기 오류: {}", e.getMessage(), e);
            ddlBuilder.append("\n-- 오류: ").append(e.getMessage()).append("\n");
        }

        return ddlBuilder.toString();
    }

    /**
     * 특정 버전 이후의 DDL만 내보내기
     *
     * @param fromVersion 시작 버전 (예: "005")
     * @return 해당 버전 이후의 DDL 문자열
     */
    public String exportDDLFromVersion(String fromVersion) {
        StringBuilder ddlBuilder = new StringBuilder();
        
        // 헤더 추가
        ddlBuilder.append("-- ============================================\n");
        ddlBuilder.append("-- GPS 지오펜스 출퇴근 관리 시스템 DDL\n");
        ddlBuilder.append("-- 시작 버전: ").append(fromVersion).append("\n");
        ddlBuilder.append("-- 생성일시: ").append(java.time.LocalDateTime.now()).append("\n");
        ddlBuilder.append("-- ============================================\n\n");
        
        try {
            ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
            if (!resource.exists()) {
                ddlBuilder.append("-- 마이그레이션 파일을 찾을 수 없습니다.\n");
                return ddlBuilder.toString();
            }

            Path migrationPath = Paths.get(resource.getURL().getPath());
            int versionNum = Integer.parseInt(fromVersion);
            
            try (Stream<Path> paths = Files.walk(migrationPath)) {
                List<Path> migrationFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().matches("^V\\d+__.*\\.sql$"))
                        .filter(p -> {
                            String fileName = p.getFileName().toString();
                            String version = fileName.substring(1, fileName.indexOf("__"));
                            return Integer.parseInt(version) >= versionNum;
                        })
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();

                for (Path file : migrationFiles) {
                    ddlBuilder.append("-- ============================================\n");
                    ddlBuilder.append("-- Migration: ").append(file.getFileName().toString()).append("\n");
                    ddlBuilder.append("-- ============================================\n\n");
                    
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    ddlBuilder.append(content);
                    ddlBuilder.append("\n");
                }
            }
        } catch (IOException | NumberFormatException e) {
            log.error("마이그레이션 파일 읽기 오류: {}", e.getMessage(), e);
            ddlBuilder.append("\n-- 오류: ").append(e.getMessage()).append("\n");
        }

        return ddlBuilder.toString();
    }

    /**
     * 사용 가능한 마이그레이션 버전 목록 반환
     *
     * @return 버전 번호 리스트
     */
    public List<String> getAvailableVersions() {
        try {
            ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
            if (!resource.exists()) {
                return List.of();
            }

            Path migrationPath = Paths.get(resource.getURL().getPath());
            
            try (Stream<Path> paths = Files.walk(migrationPath)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().matches("^V\\d+__.*\\.sql$"))
                        .map(p -> {
                            String fileName = p.getFileName().toString();
                            return fileName.substring(1, fileName.indexOf("__"));
                        })
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            log.error("마이그레이션 버전 목록 조회 오류: {}", e.getMessage(), e);
            return List.of();
        }
    }
}