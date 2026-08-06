package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 근태조회(일별) 화면이 로그인 계정의 권한레벨에 따라 UI를 어떻게 보여줄지 판단하기 위한 정보.
 * - employeeLevel: 레벨(LEVEL_ROLL)이 파트장 미만(직원)인지. true면 본인 근태만 월별로 간단히 보여준다.
 * - hasSubordinates: 파트장 이상 레벨이면서 실제로 본인 외에 조회 가능한 대상이 있는지.
 *   false면(파트장 이상이지만 하위 직원이 없는 경우 포함) 근무지/부서/직원명 검색 필터는 의미가 없어 숨긴다.
 * - organizationIds / workplaceIds: 부서·근무지 검색 드롭다운에 보여줄 후보를 좁히기 위한 값.
 *   null이면 전체 조회 가능(SYSADMIN/HRADMIN/PRESIDENT)이라는 뜻이고, 그 외에는 본인이 조회 가능한
 *   범위(본인 조직 산하 조직들 / 그 범위 직원들이 실제 배정된 근무지들)로 채워진다.
 */
@Getter
@Builder
public class AttendanceScopeInfoResponse {
    private boolean employeeLevel;
    private boolean hasSubordinates;
    private List<Long> organizationIds;
    private List<Long> workplaceIds;
}
