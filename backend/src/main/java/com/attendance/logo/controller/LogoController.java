package com.attendance.logo.controller;

import com.attendance.common.dto.ApiResponse;
import com.attendance.logo.domain.LogoType;
import com.attendance.logo.service.LogoService;
import com.attendance.user.domain.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// GET은 로그인 화면(인증 전)에서도 로고를 보여줘야 하므로 클래스 레벨 권한을 두지 않는다.
// 회사 구분 없이 시스템 전체가 공유하는 로고다 — Company와 무관한 전역 설정.
// {type}은 로고가 쓰이는 위치(login/sidebar)를 가리키며 각각 독립된 파일로 관리된다.
@RestController
@RequestMapping("/api/v1/logo")
@RequiredArgsConstructor
public class LogoController {

    private final LogoService logoService;

    @GetMapping("/{type}")
    public ResponseEntity<byte[]> get(@PathVariable String type) {
        return logoService.loadCurrent(LogoType.fromSlug(type))
                .map(logo -> ResponseEntity.ok().contentType(logo.mediaType()).body(logo.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{type}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> upload(
            @PathVariable String type,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        logoService.upload(LogoType.fromSlug(type), file, principal);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
