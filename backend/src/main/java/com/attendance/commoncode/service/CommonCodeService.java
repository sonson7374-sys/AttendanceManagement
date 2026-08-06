package com.attendance.commoncode.service;

import com.attendance.commoncode.domain.CommonCode;
import com.attendance.commoncode.dto.CommonCodeCreateRequest;
import com.attendance.commoncode.dto.CommonCodeResponse;
import com.attendance.commoncode.dto.CommonCodeUpdateRequest;
import com.attendance.commoncode.repository.CommonCodeRepository;
import com.attendance.common.exception.AttendanceException;
import com.attendance.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final CommonCodeRepository commonCodeRepository;

    @Transactional(readOnly = true)
    public List<CommonCodeResponse> list(String groupCode) {
        return commonCodeRepository.findByGroupCodeOrderByDisplayOrderAsc(groupCode).stream()
                .map(CommonCodeResponse::from)
                .toList();
    }

    @Transactional
    public CommonCodeResponse create(CommonCodeCreateRequest request) {
        if (commonCodeRepository.existsByGroupCodeAndCode(request.getGroupCode(), request.getCode())) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT,
                    "이미 존재하는 코드입니다: " + request.getGroupCode() + "/" + request.getCode());
        }
        CommonCode code = CommonCode.builder()
                .groupCode(request.getGroupCode())
                .code(request.getCode())
                .codeName(request.getCodeName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();
        return CommonCodeResponse.from(commonCodeRepository.save(code));
    }

    @Transactional
    public CommonCodeResponse update(Long id, CommonCodeUpdateRequest request) {
        CommonCode code = findById(id);
        code.update(request.getCodeName(), request.getDescription(), request.getDisplayOrder(), request.isActive());
        return CommonCodeResponse.from(code);
    }

    @Transactional
    public void delete(Long id) {
        CommonCode code = findById(id);
        if (code.isProtectedCode()) {
            throw new AttendanceException(ErrorCode.INVALID_INPUT, "기본 코드는 삭제할 수 없습니다.");
        }
        commonCodeRepository.delete(code);
    }

    private CommonCode findById(Long id) {
        return commonCodeRepository.findById(id)
                .orElseThrow(() -> new AttendanceException(ErrorCode.RESOURCE_NOT_FOUND, "공통코드를 찾을 수 없습니다."));
    }
}
