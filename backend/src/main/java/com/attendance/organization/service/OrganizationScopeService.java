package com.attendance.organization.service;

import com.attendance.commoncode.domain.CommonCode;
import com.attendance.commoncode.repository.CommonCodeRepository;
import com.attendance.organization.domain.Organization;
import com.attendance.organization.repository.OrganizationRepository;
import com.attendance.user.domain.User;
import com.attendance.user.domain.UserRole;
import com.attendance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 역할·조직 계층에 따라 "이 사용자가 조회 가능한 대상 사용자" 범위를 계산한다.
 * - SYSTEM_ADMIN / HR_ADMIN: 전체 조회 가능 (null 반환)
 * - MANAGER: 본인이 배정된 조직과 그 하위 조직 전체에 속한 사용자 + 본인
 *   (팀장이면 자기 팀, 실장이면 실 산하 전체, 본부장이면 본부 산하 전체가 같은 방식으로 계산된다 —
 *   관리자가 어느 조직 "레벨"에 배정되어 있는지에 따라 범위가 자연스럽게 달라진다)
 * - EMPLOYEE: 본인만
 */
@Service
@RequiredArgsConstructor
public class OrganizationScopeService {

    // 그룹코드 LEVEL_ROLL 기준으로 전체 조회가 허용되는 권한레벨(승인함 등 level 기반 화면에서 사용).
    private static final Set<String> UNRESTRICTED_LEVELS = Set.of("SYSADMIN", "HRADMIN", "PRESIDENT");

    private static final String LEVEL_GROUP_CODE = "LEVEL_ROLL";
    private static final String PARTLEAD_CODE = "PARTLEAD";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CommonCodeRepository commonCodeRepository;

    @Transactional(readOnly = true)
    public Set<Long> resolveVisibleUserIds(Long actingUserId) {
        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + actingUserId));

        if (actor.getRole() == UserRole.SYSTEM_ADMIN || actor.getRole() == UserRole.HR_ADMIN) {
            return null;
        }
        if (actor.getRole() != UserRole.MANAGER || actor.getOrganizationId() == null) {
            return Set.of(actingUserId);
        }

        Set<Long> orgIds = collectDescendantOrgIds(actor.getOrganizationId());
        Set<Long> userIds = new HashSet<>(userRepository.findByOrganizationIdIn(orgIds).stream()
                .map(User::getId)
                .toList());
        userIds.add(actingUserId);
        return userIds;
    }

    /**
     * role이 아니라 권한레벨(level, 그룹코드 LEVEL_ROLL)을 기준으로 조회 범위를 계산한다.
     * - SYSADMIN/HRADMIN/PRESIDENT: 전체 조회 가능 (null 반환)
     * - 파트장 이상 레벨(팀장/실장/본부장/부문장/파트장 등)이며 조직이 배정된 경우: 본인 조직 산하 전체 + 본인
     * - 그 외(직원 레벨이거나 조직 미배정): 본인만
     */
    @Transactional(readOnly = true)
    public Set<Long> resolveVisibleUserIdsByLevel(Long actingUserId) {
        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + actingUserId));

        if (UNRESTRICTED_LEVELS.contains(actor.getLevel())) {
            return null;
        }
        if (!isPartLeadOrAbove(actor.getLevel()) || actor.getOrganizationId() == null) {
            return Set.of(actingUserId);
        }

        Set<Long> orgIds = collectDescendantOrgIds(actor.getOrganizationId());
        Set<Long> userIds = new HashSet<>(userRepository.findByOrganizationIdIn(orgIds).stream()
                .map(User::getId)
                .toList());
        userIds.add(actingUserId);
        return userIds;
    }

    /**
     * resolveVisibleUserIdsByLevel과 같은 기준(권한레벨)으로, 조회 범위에 해당하는 "조직 id" 집합을 계산한다
     * (근태조회 일별탭의 부서 검색 드롭다운처럼 조직 자체를 좁혀야 하는 화면에서 사용).
     * - SYSADMIN/HRADMIN/PRESIDENT: 전체 조회 가능 (null 반환)
     * - 파트장 이상 레벨이며 조직이 배정된 경우: 본인 조직 + 그 하위 조직 전체
     * - 그 외: 본인 조직만(있으면), 없으면 빈 집합
     */
    @Transactional(readOnly = true)
    public Set<Long> resolveVisibleOrganizationIdsByLevel(Long actingUserId) {
        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + actingUserId));

        if (UNRESTRICTED_LEVELS.contains(actor.getLevel())) {
            return null;
        }
        if (actor.getOrganizationId() == null) {
            return Set.of();
        }
        if (!isPartLeadOrAbove(actor.getLevel())) {
            return Set.of(actor.getOrganizationId());
        }
        return collectDescendantOrgIds(actor.getOrganizationId());
    }

    /**
     * 그룹코드 LEVEL_ROLL의 display_order를 기준으로 "파트장 이상" 레벨인지 판단한다
     * (display_order 값이 작을수록 상위 직급이며, 파트장의 display_order 이하이면 파트장 이상으로 본다).
     * LEVEL_ROLL에 등록되지 않은 레벨 코드는 파트장 이상으로 취급하지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean isPartLeadOrAbove(String levelCode) {
        if (levelCode == null) {
            return false;
        }
        if (UNRESTRICTED_LEVELS.contains(levelCode)) {
            return true;
        }
        Integer actorOrder = commonCodeRepository.findByGroupCodeAndCode(LEVEL_GROUP_CODE, levelCode)
                .map(CommonCode::getDisplayOrder).orElse(null);
        Integer partLeadOrder = commonCodeRepository.findByGroupCodeAndCode(LEVEL_GROUP_CODE, PARTLEAD_CODE)
                .map(CommonCode::getDisplayOrder).orElse(null);
        return actorOrder != null && partLeadOrder != null && actorOrder <= partLeadOrder;
    }

    /**
     * "출근부(지정일)" 화면처럼 파트장 이상 권한이 하위 직원의 근태를 직접 보정하는 기능을 위한 범위 계산.
     * - SYSTEM_ADMIN / HR_ADMIN: 전체 (null 반환)
     * - 파트장 이상 레벨(팀장/실장/본부장/부문장/파트장 등)이며 조직이 배정된 경우: 본인 조직 산하 전체 + 본인
     * - 그 외(일반 직원 레벨이거나 조직 미배정): 본인만
     */
    @Transactional(readOnly = true)
    public Set<Long> resolveManagedUserIds(Long actingUserId) {
        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + actingUserId));

        if (actor.getRole() == UserRole.SYSTEM_ADMIN || actor.getRole() == UserRole.HR_ADMIN) {
            return null;
        }
        if (!isPartLeadOrAbove(actor.getLevel()) || actor.getOrganizationId() == null) {
            return Set.of(actingUserId);
        }

        Set<Long> orgIds = collectDescendantOrgIds(actor.getOrganizationId());
        Set<Long> userIds = new HashSet<>(userRepository.findByOrganizationIdIn(orgIds).stream()
                .map(User::getId)
                .toList());
        userIds.add(actingUserId);
        return userIds;
    }

    /**
     * resolveManagedUserIds와 동일하지만, 호출부가 이미 전체 조직 목록을 조회해 둔 경우(예: 출근부 화면에서
     * 부서 정렬용으로 조직 트리를 이미 펼쳐 둔 경우) 그 목록을 재사용해 조직 트리를 순회한다 — 그렇지 않으면
     * collectDescendantOrgIds가 하위 부서 수만큼 organizationRepository.findByParentId를 반복 호출하게 된다.
     */
    @Transactional(readOnly = true)
    public Set<Long> resolveManagedUserIds(Long actingUserId, List<Organization> allOrganizations) {
        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + actingUserId));

        if (actor.getRole() == UserRole.SYSTEM_ADMIN || actor.getRole() == UserRole.HR_ADMIN) {
            return null;
        }
        if (!isPartLeadOrAbove(actor.getLevel()) || actor.getOrganizationId() == null) {
            return Set.of(actingUserId);
        }

        Map<Long, List<Organization>> childrenByParent = new HashMap<>();
        for (Organization org : allOrganizations) {
            if (org.getParentId() != null) {
                childrenByParent.computeIfAbsent(org.getParentId(), k -> new ArrayList<>()).add(org);
            }
        }
        Set<Long> orgIds = collectDescendantOrgIds(actor.getOrganizationId(), childrenByParent);
        Set<Long> userIds = new HashSet<>(userRepository.findByOrganizationIdIn(orgIds).stream()
                .map(User::getId)
                .toList());
        userIds.add(actingUserId);
        return userIds;
    }

    private Set<Long> collectDescendantOrgIds(Long rootOrgId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootOrgId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            organizationRepository.findByParentId(current)
                    .forEach(child -> queue.add(child.getId()));
        }
        return visited;
    }

    private Set<Long> collectDescendantOrgIds(Long rootOrgId, Map<Long, List<Organization>> childrenByParent) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootOrgId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            for (Organization child : childrenByParent.getOrDefault(current, List.of())) {
                queue.add(child.getId());
            }
        }
        return visited;
    }
}
