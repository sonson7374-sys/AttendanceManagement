package com.attendance.commoncode.service;

import com.attendance.commoncode.domain.CommonCodeGroup;
import com.attendance.commoncode.dto.CommonCodeGroupCreateRequest;
import com.attendance.commoncode.dto.CommonCodeGroupResponse;
import com.attendance.commoncode.dto.CommonCodeGroupUpdateRequest;
import com.attendance.commoncode.repository.CommonCodeGroupRepository;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonCodeGroupService {

    private final CommonCodeGroupRepository commonCodeGroupRepository;

    @Transactional(readOnly = true)
    public List<CommonCodeGroupResponse> list() {
        return commonCodeGroupRepository.findAllByOrderByGroupCodeAsc().stream()
                .map(CommonCodeGroupResponse::from)
                .toList();
    }

    @Transactional
    public CommonCodeGroupResponse create(CommonCodeGroupCreateRequest request) {
        if (commonCodeGroupRepository.existsByGroupCode(request.getGroupCode())) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT,
                    "이미 존재하는 그룹코드입니다: " + request.getGroupCode());
        }
        CommonCodeGroup group = CommonCodeGroup.builder()
                .groupCode(request.getGroupCode())
                .groupName(request.getGroupName())
                .description(request.getDescription())
                .build();
        return CommonCodeGroupResponse.from(commonCodeGroupRepository.save(group));
    }

    @Transactional
    public CommonCodeGroupResponse update(Long id, CommonCodeGroupUpdateRequest request) {
        CommonCodeGroup group = commonCodeGroupRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND, "그룹코드를 찾을 수 없습니다."));
        group.update(request.getGroupName(), request.getDescription());
        return CommonCodeGroupResponse.from(group);
    }
}
