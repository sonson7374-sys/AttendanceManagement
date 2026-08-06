package com.attendance.commoncode.repository;

import com.attendance.commoncode.domain.CommonCodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeGroupRepository extends JpaRepository<CommonCodeGroup, Long> {
    List<CommonCodeGroup> findAllByOrderByGroupCodeAsc();
    boolean existsByGroupCode(String groupCode);
}
