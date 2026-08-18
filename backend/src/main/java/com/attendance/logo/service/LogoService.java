package com.attendance.logo.service;

import com.attendance.audit.service.AuditLogService;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import com.attendance.logo.domain.LogoType;
import com.attendance.user.domain.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 시스템 전체가 공유하는 로고 이미지를 서버 디스크에 보관한다. 회사(테넌트)와 무관한 전역
 * 설정이다 — 로그인 화면은 인증 전이라 어느 회사 소속인지 알 수 없어 회사별 로고가 불가능하다.
 * {@link LogoType}별로 독립된 파일 슬롯을 두어 로그인 화면용 로고와 관리자웹 사이드바용 로고를
 * 따로 관리한다. 저장 폴더 안에는 타입별로 "logo-&lt;type&gt;.&lt;확장자&gt;" 파일이 최대 1개만
 * 존재한다(그 존재 자체가 상태이므로 별도로 "현재 로고가 무엇인지" 기록하는 DB 컬럼이 필요 없다).
 */
@Service
public class LogoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    private final Path storageDir;
    private final AuditLogService auditLogService;

    public LogoService(@Value("${app.logo.storage-dir}") String storageDir, AuditLogService auditLogService) {
        this.storageDir = Path.of(storageDir);
        this.auditLogService = auditLogService;
    }

    public void upload(LogoType type, MultipartFile file, UserPrincipal actor) {
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "PNG 또는 JPEG 파일만 업로드할 수 있습니다.");
        }

        try {
            Files.createDirectories(storageDir);
            deleteExistingLogoFiles(type);
            file.transferTo(storageDir.resolve(fileStem(type) + "." + extension));
        } catch (IOException e) {
            throw new UncheckedIOException("로고 파일을 저장하는 중 오류가 발생했습니다.", e);
        }

        auditLogService.record(actor.getId(), actor.getUsername(), "LOGO_UPLOADED",
                "LOGO", null, Map.of("type", type.slug(), "filename", String.valueOf(file.getOriginalFilename())));
    }

    public Optional<LogoFile> loadCurrent(LogoType type) {
        if (!Files.isDirectory(storageDir)) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, fileStem(type) + ".*")) {
            for (Path candidate : stream) {
                String extension = extensionOf(candidate.getFileName().toString());
                return Optional.of(new LogoFile(Files.readAllBytes(candidate), mediaTypeFor(extension)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("로고 파일을 읽는 중 오류가 발생했습니다.", e);
        }
        return Optional.empty();
    }

    private void deleteExistingLogoFiles(LogoType type) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, fileStem(type) + ".*")) {
            for (Path existing : stream) {
                Files.delete(existing);
            }
        }
    }

    private String fileStem(LogoType type) {
        return "logo-" + type.slug();
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private MediaType mediaTypeFor(String extension) {
        return "png".equals(extension) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }

    public record LogoFile(byte[] bytes, MediaType mediaType) {
    }
}
