package com.restaurant.restaurant.status.application;

import static com.restaurant.restaurant.status.api.TableStatusDtos.ChangeTableStatusRequest;
import static com.restaurant.restaurant.status.api.TableStatusDtos.TableStatusHistoryResponse;
import static com.restaurant.restaurant.status.api.TableStatusDtos.TableStatusResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.status.infrastructure.NotificationClient;
import com.restaurant.restaurant.status.infrastructure.OperationsTableClient;
import com.restaurant.restaurant.status.infrastructure.OperationsTableClient.TableAssignments;
import com.restaurant.restaurant.status.infrastructure.TableStatusRepository;
import com.restaurant.restaurant.status.infrastructure.TableStatusRepository.StatusHistoryRecord;
import com.restaurant.restaurant.status.infrastructure.TableStatusRepository.TableStateRecord;
import com.restaurant.restaurant.status.infrastructure.WorkforceMemberClient;
import com.restaurant.restaurant.status.infrastructure.WorkforceMemberClient.MemberReference;
import com.restaurant.restaurant.status.realtime.HallRealtimePublisher;
import com.restaurant.restaurant.status.realtime.TableStatusChangedRealtimeEvent;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class TableStatusService {

    private static final List<String> STATUSES = List.of(
            "READY", "NEEDS_CLEANING", "NEEDS_SERVING", "PROBLEM");

    private final TableStatusRepository repository;
    private final WorkforceMemberClient workforceClient;
    private final OperationsTableClient operationsClient;
    private final NotificationClient notificationClient;
    private final HallRealtimePublisher realtimePublisher;

    public TableStatusService(
            TableStatusRepository repository,
            WorkforceMemberClient workforceClient,
            OperationsTableClient operationsClient,
            NotificationClient notificationClient,
            HallRealtimePublisher realtimePublisher) {
        this.repository = repository;
        this.workforceClient = workforceClient;
        this.operationsClient = operationsClient;
        this.notificationClient = notificationClient;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public TableStatusResponse change(
            UUID restaurantId,
            UUID tableId,
            UUID actorUserId,
            ChangeTableStatusRequest request) {
        MemberReference actor = requireMember(restaurantId, actorUserId);
        TableAssignments assignments = requireAssignments(restaurantId, tableId);
        TableStateRecord table = repository.findForUpdate(restaurantId, tableId)
                .orElseThrow(TableStatusService::notFound);
        String target = request.status().strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(target)) {
            throw validation("Статус стола должен быть READY, NEEDS_CLEANING, NEEDS_SERVING или PROBLEM.");
        }
        String comment = cleanOptional(request.comment());
        if ("PROBLEM".equals(target) && comment == null) {
            throw validation("Для статуса PROBLEM обязательно укажите описание проблемы.");
        }
        authorize(actor, assignments, target);

        UUID historyId = repository.changeStatus(
                table,
                assignments.shiftId(),
                actor.membershipId(),
                target,
                comment);
        StatusHistoryRecord history = repository.findHistory(restaurantId, historyId)
                .orElseThrow(TableStatusService::notFound);
        sendNotifications(restaurantId, table, assignments, target, comment);
        realtimePublisher.publish(new TableStatusChangedRealtimeEvent(
                restaurantId,
                table.id(),
                table.number(),
                assignments.shiftId(),
                table.status(),
                target,
                comment,
                actor.membershipId(),
                history.createdAt()));
        return new TableStatusResponse(
                table.id(),
                table.number(),
                assignments.shiftId(),
                target,
                comment,
                actor.membershipId(),
                history.createdAt());
    }

    @Transactional(readOnly = true)
    public List<TableStatusHistoryResponse> history(
            UUID restaurantId,
            UUID tableId,
            UUID shiftId,
            UUID actorUserId) {
        MemberReference actor = requireMember(restaurantId, actorUserId);
        if (!List.of("DIRECTOR", "MANAGER").contains(actor.role())) {
            throw forbidden("История статусов доступна только менеджеру или директору.");
        }
        return repository.findHistory(restaurantId, tableId, shiftId).stream()
                .map(TableStatusService::historyResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TableStatusHistoryResponse> restaurantHistory(
            UUID restaurantId,
            UUID tableId,
            UUID shiftId,
            UUID changedByMembershipId,
            Instant from,
            Instant to,
            UUID actorUserId) {
        MemberReference actor = requireMember(restaurantId, actorUserId);
        if (!List.of("DIRECTOR", "MANAGER").contains(actor.role())) {
            throw forbidden("История статусов доступна только менеджеру или директору.");
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw validation("Параметр to должен быть позже from.");
        }
        return repository.findRestaurantHistory(
                        restaurantId,
                        tableId,
                        shiftId,
                        changedByMembershipId,
                        from,
                        to)
                .stream()
                .map(TableStatusService::historyResponse)
                .toList();
    }

    private void authorize(MemberReference actor, TableAssignments assignments, String target) {
        if (List.of("DIRECTOR", "MANAGER").contains(actor.role())) {
            return;
        }
        if (!"EMPLOYEE".equals(actor.role())) {
            throw forbidden("Нет доступа к изменению статуса стола.");
        }
        if ("WAITER".equals(actor.position())
                && actor.membershipId().equals(assignments.waiterMembershipId())
                && List.of("NEEDS_CLEANING", "NEEDS_SERVING", "PROBLEM").contains(target)) {
            return;
        }
        if ("RUNNER".equals(actor.position())
                && actor.membershipId().equals(assignments.runnerMembershipId())
                && "READY".equals(target)) {
            return;
        }
        throw forbidden("Сотрудник может менять статус только закреплённого за ним стола по своему сценарию.");
    }

    private void sendNotifications(
            UUID restaurantId,
            TableStateRecord table,
            TableAssignments assignments,
            String status,
            String comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", table.id().toString());
        payload.put("tableNumber", table.number());
        payload.put("shiftId", assignments.shiftId().toString());
        payload.put("status", status);
        if (comment != null) {
            payload.put("comment", comment);
        }

        String genericMessage = "Стол №" + table.number() + " изменил свой статус";
        MemberReference manager = workforceClient.findByMembership(
                restaurantId,
                assignments.managerMembershipId());
        if ("MANAGER".equals(manager.role()) && manager.userId() != null) {
            createNotification(
                    manager.userId(),
                    restaurantId,
                    "TABLE_STATUS_CHANGED",
                    "Изменение статуса стола",
                    genericMessage,
                    payload);
        }

        if (List.of("NEEDS_CLEANING", "NEEDS_SERVING").contains(status)) {
            MemberReference runner = workforceClient.findByMembership(
                    restaurantId,
                    assignments.runnerMembershipId());
            String action = "NEEDS_CLEANING".equals(status) ? "нужна уборка" : "нужна сервировка";
            createNotification(
                    runner.userId(),
                    restaurantId,
                    "NEEDS_CLEANING".equals(status) ? "TABLE_NEEDS_CLEANING" : "TABLE_NEEDS_SERVING",
                    "Стол №" + table.number(),
                    "Столу №" + table.number() + " " + action,
                    payload);
        }
    }

    private void createNotification(
            UUID userId,
            UUID restaurantId,
            String type,
            String title,
            String message,
            Map<String, Object> payload) {
        try {
            notificationClient.create(userId, restaurantId, type, title, message, payload);
        } catch (RestClientException ignored) {
            // The table event remains in the transactional outbox for asynchronous retry.
        }
    }

    private MemberReference requireMember(UUID restaurantId, UUID userId) {
        try {
            MemberReference member = workforceClient.findByUser(restaurantId, userId);
            if (member == null || member.membershipId() == null) {
                throw new RestClientException("Empty workforce response");
            }
            return member;
        } catch (RestClientException exception) {
            throw forbidden("Нет активного доступа к ресторану.");
        }
    }

    private TableAssignments requireAssignments(UUID restaurantId, UUID tableId) {
        try {
            TableAssignments assignments = operationsClient.activeAssignments(restaurantId, tableId);
            if (assignments == null
                    || assignments.shiftId() == null
                    || assignments.managerMembershipId() == null
                    || assignments.waiterMembershipId() == null
                    || assignments.runnerMembershipId() == null) {
                throw new RestClientException("Incomplete shift assignment response");
            }
            return assignments;
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "TABLE_NOT_ASSIGNED_IN_ACTIVE_SHIFT",
                    "Статус можно менять только у стола, закреплённого в открытой смене.");
        }
    }

    private static TableStatusHistoryResponse historyResponse(StatusHistoryRecord history) {
        return new TableStatusHistoryResponse(
                history.id(),
                history.tableId(),
                history.tableNumber(),
                history.shiftId(),
                history.changedByMembershipId(),
                history.oldStatus(),
                history.newStatus(),
                history.comment(),
                history.createdAt());
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TABLE_NOT_FOUND", "Стол не найден.");
    }

    private static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "TABLE_STATUS_ACCESS_REQUIRED", message);
    }
}
