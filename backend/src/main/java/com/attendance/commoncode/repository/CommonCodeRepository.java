package com.attendance.commoncode.repository;

import com.attendance.commoncode.domain.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {
    List<CommonCode> findByGroupCodeOrderByDisplayOrderAsc(String groupCode);
    boolean existsByGroupCodeAndCode(String groupCode, String code);
    Optional<CommonCode> findByGroupCodeAndCode(String groupCode, String code);
}
