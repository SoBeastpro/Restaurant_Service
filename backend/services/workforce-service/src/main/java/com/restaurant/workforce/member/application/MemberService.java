package com.restaurant.workforce.member.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.workforce.member.api.MemberDtos.EmployeeResponse;
import com.restaurant.workforce.member.api.MemberDtos.EmployeeAssignmentResponse;
import com.restaurant.workforce.member.api.MemberDtos.MemberReferenceResponse;
import com.restaurant.workforce.member.infrastructure.MemberRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository repository;

    public MemberService(MemberRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees(UUID restaurantId, UUID actorUserId) {
        MemberReferenceResponse actor = requireActiveMember(restaurantId, actorUserId);
        if (!List.of("DIRECTOR", "MANAGER").contains(actor.role())) {
            throw forbidden();
        }
        return repository.findActiveEmployees(restaurantId);
    }

    @Transactional(readOnly = true)
    public MemberReferenceResponse requireActiveMember(UUID restaurantId, UUID userId) {
        return repository.findActiveByUser(restaurantId, userId)
                .orElseThrow(MemberService::notFound);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAssignmentResponse> validateEmployees(UUID restaurantId, List<UUID> membershipIds) {
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(membershipIds));
        List<EmployeeAssignmentResponse> found = repository.findActiveEmployeesForAssignment(restaurantId, distinct);
        if (found.size() != distinct.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SHIFT_EMPLOYEE",
                    "В смену можно добавить только активных сотрудников этого ресторана.");
        }
        return found;
    }

    @Transactional(readOnly = true)
    public MemberReferenceResponse requireActiveMembership(UUID restaurantId, UUID membershipId) {
        return repository.findActiveById(restaurantId, membershipId)
                .orElseThrow(MemberService::notFound);
    }

    @Transactional(readOnly = true)
    public List<MemberReferenceResponse> activeManagers(UUID restaurantId) {
        return repository.findActiveManagers(restaurantId);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND", "Участник ресторана не найден.");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "WORKFORCE_ACCESS_REQUIRED", "Нет доступа к сотрудникам.");
    }
}
