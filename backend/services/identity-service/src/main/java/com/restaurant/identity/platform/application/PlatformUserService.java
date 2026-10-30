package com.restaurant.identity.platform.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.foundation.web.PageResponse;
import com.restaurant.identity.platform.api.PlatformUserDtos.UpdateUserStatusRequest;
import com.restaurant.identity.platform.api.PlatformUserDtos.UserResponse;
import com.restaurant.identity.platform.infrastructure.PlatformUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserService {

    private static final List<String> STATUSES = List.of("ACTIVE", "BLOCKED", "ARCHIVED");
    private static final List<String> ROLES = List.of("SYSTEM_ADMIN", "DIRECTOR", "MANAGER", "EMPLOYEE");

    private final PlatformUserRepository repository;

    public PlatformUserService(PlatformUserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String query, String status, String role, int page, int size) {
        int safePage = requirePage(page);
        int safeSize = requireSize(size);
        String normalizedQuery = cleanOptional(query);
        String normalizedStatus = normalizeOptional(status, STATUSES, "USER_STATUS_INVALID", "Неизвестный статус пользователя.");
        String normalizedRole = normalizeOptional(role, ROLES, "USER_ROLE_INVALID", "Неизвестная роль пользователя.");
        long total = repository.countUsers(normalizedQuery, normalizedStatus, normalizedRole);
        return PageResponse.of(
                repository.findUsers(normalizedQuery, normalizedStatus, normalizedRole, safeSize, safePage * safeSize),
                safePage,
                safeSize,
                total);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return required(userId);
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UUID actorUserId, UpdateUserStatusRequest request) {
        UserResponse current = required(userId);
        String status = normalize(request.status(), STATUSES, "USER_STATUS_INVALID", "Неизвестный статус пользователя.");
        if (userId.equals(actorUserId) && !"ACTIVE".equals(status)) {
            throw conflict("SYSTEM_ADMIN_SELF_BLOCK_FORBIDDEN", "Нельзя заблокировать или архивировать собственный аккаунт.");
        }
        if (current.status().equals(status)) {
            return current;
        }
        repository.updateStatus(userId, status);
        if (!"ACTIVE".equals(status)) {
            repository.revokeSessions(userId, "PLATFORM_ADMIN_STATUS_CHANGE");
        }
        repository.insertStatusEvent(userId, actorUserId, current.status(), status, cleanOptional(request.reason()));
        return required(userId);
    }

    private UserResponse required(UUID userId) {
        return repository.findUser(userId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "PLATFORM_USER_NOT_FOUND",
                "Пользователь не найден."));
    }

    private static int requirePage(int page) {
        if (page < 0) {
            throw validation("PAGE_INVALID", "Номер страницы не может быть отрицательным.");
        }
        return page;
    }

    private static int requireSize(int size) {
        if (size < 1 || size > 100) {
            throw validation("PAGE_SIZE_INVALID", "Размер страницы должен быть от 1 до 100.");
        }
        return size;
    }

    private static String normalizeOptional(String value, List<String> allowed, String code, String message) {
        return value == null || value.isBlank() ? null : normalize(value, allowed, code, message);
    }

    private static String normalize(String value, List<String> allowed, String code, String message) {
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw validation(code, message);
        }
        return normalized;
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException validation(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
