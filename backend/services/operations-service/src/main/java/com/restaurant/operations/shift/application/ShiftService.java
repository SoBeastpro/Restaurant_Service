package com.restaurant.operations.shift.application;

import static com.restaurant.operations.shift.api.ShiftDtos.ConfigureShiftRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.AssignCarriedTaskRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.AssignTaskRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.ChangeTaskStatusRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CloseShiftRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CreateShiftRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CreateTaskRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.CurrentShiftResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ParticipantResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ShiftReportResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ShiftResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.ShiftSummaryResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.TaskResolutionRequest;
import static com.restaurant.operations.shift.api.ShiftDtos.TaskResponse;
import static com.restaurant.operations.shift.api.ShiftDtos.TaskHistoryResponse;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.operations.shift.api.ShiftDtos.ParticipantRequest;
import com.restaurant.operations.shift.infrastructure.NotificationClient;
import com.restaurant.operations.shift.infrastructure.RestaurantClient;
import com.restaurant.operations.shift.infrastructure.ShiftRepository;
import com.restaurant.operations.shift.infrastructure.ShiftRepository.ParticipantRecord;
import com.restaurant.operations.shift.infrastructure.ShiftRepository.ShiftRecord;
import com.restaurant.operations.shift.infrastructure.ShiftRepository.TaskRecord;
import com.restaurant.operations.shift.infrastructure.ShiftRepository.TaskHistoryRecord;
import com.restaurant.operations.shift.infrastructure.ShiftRepository.ReportMetricsRecord;
import com.restaurant.operations.shift.infrastructure.WorkforceClient;
import com.restaurant.operations.shift.infrastructure.WorkforceClient.EmployeeAssignmentReference;
import com.restaurant.operations.shift.infrastructure.WorkforceClient.MemberReference;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class ShiftService {

    private static final DateTimeFormatter SHIFT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(ZoneOffset.UTC);

    private final ShiftRepository repository;
    private final WorkforceClient workforceClient;
    private final RestaurantClient restaurantClient;
    private final NotificationClient notificationClient;

    public ShiftService(
            ShiftRepository repository,
            WorkforceClient workforceClient,
            RestaurantClient restaurantClient,
            NotificationClient notificationClient) {
        this.repository = repository;
        this.workforceClient = workforceClient;
        this.restaurantClient = restaurantClient;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public ShiftResponse create(UUID restaurantId, UUID actorUserId, CreateShiftRequest request) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        if (!request.scheduledEndAt().isAfter(request.scheduledStartAt())) {
            throw validation("Время окончания смены должно быть позже времени начала.");
        }
        UUID shiftId = UUID.randomUUID();
        repository.insertShift(
                shiftId,
                restaurantId,
                "Смена " + SHIFT_DATE.format(request.scheduledStartAt()),
                request.scheduledStartAt(),
                request.scheduledEndAt(),
                actor.membershipId());
        return response(requiredShift(restaurantId, shiftId));
    }

    @Transactional
    public ShiftResponse configure(
            UUID restaurantId,
            UUID shiftId,
            UUID actorUserId,
            ConfigureShiftRequest request) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, shiftId);
        requireDraft(shift);

        List<UUID> employeeIds = request.participants().stream()
                .map(ParticipantRequest::membershipId)
                .toList();
        if (new LinkedHashSet<>(employeeIds).size() != employeeIds.size()) {
            throw validation("Сотрудник не может быть добавлен в смену дважды.");
        }

        if (!employeeIds.containsAll(repository.findTaskAssignees(shiftId))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMPLOYEE_HAS_SHIFT_TASKS",
                    "Нельзя убрать из смены сотрудника, пока на него назначены задачи этой смены.");
        }

        List<EmployeeAssignmentReference> employees = validateEmployees(restaurantId, employeeIds);
        Map<UUID, String> positions = employees.stream().collect(Collectors.toMap(
                EmployeeAssignmentReference::membershipId,
                employee -> employee.position().strip().toUpperCase(Locale.ROOT)));
        Map<String, List<UUID>> tablesByPosition = new LinkedHashMap<>();
        for (ParticipantRequest participant : request.participants()) {
            String position = positions.get(participant.membershipId());
            if (!List.of("WAITER", "RUNNER").contains(position)) {
                throw validation("Для работы со столами должность сотрудника должна быть WAITER или RUNNER.");
            }
            tablesByPosition.computeIfAbsent(position, ignored -> new ArrayList<>())
                    .addAll(participant.tableIds());
        }
        for (Map.Entry<String, List<UUID>> entry : tablesByPosition.entrySet()) {
            if (new LinkedHashSet<>(entry.getValue()).size() != entry.getValue().size()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "TABLE_ASSIGNED_MORE_THAN_ONCE_FOR_POSITION",
                        "Один стол нельзя закрепить за двумя сотрудниками одной должности.");
            }
        }
        Set<UUID> waiterTables = new LinkedHashSet<>(tablesByPosition.getOrDefault("WAITER", List.of()));
        Set<UUID> runnerTables = new LinkedHashSet<>(tablesByPosition.getOrDefault("RUNNER", List.of()));
        if (waiterTables.isEmpty() || !waiterTables.equals(runnerTables)) {
            throw validation("Каждый стол смены должен быть закреплён отдельно за одним официантом и одним раннером.");
        }
        validateTables(restaurantId, List.copyOf(waiterTables));
        if (!waiterTables.equals(new LinkedHashSet<>(activeTables(restaurantId)))) {
            throw validation("Перед открытием смены распределите все активные столы между официантами и раннерами.");
        }

        repository.clearConfiguration(shiftId);
        for (ParticipantRequest participant : request.participants()) {
            repository.insertParticipant(
                    restaurantId,
                    shiftId,
                    participant.membershipId(),
                    actor.membershipId());
            for (UUID tableId : participant.tableIds()) {
                repository.insertTableAssignment(
                        restaurantId,
                        shiftId,
                        tableId,
                        participant.membershipId(),
                        positions.get(participant.membershipId()),
                        actor.membershipId());
            }
        }
        return response(requiredShift(restaurantId, shiftId));
    }

    @Transactional
    public ShiftResponse start(UUID restaurantId, UUID shiftId, UUID actorUserId) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, shiftId);
        requireDraft(shift);
        if (repository.countParticipants(shiftId) == 0) {
            throw validation("Перед открытием смены добавьте хотя бы одного сотрудника.");
        }
        if (repository.hasParticipantsWithoutTables(shiftId)) {
            throw validation("Перед открытием смены закрепите хотя бы один стол за каждым сотрудником.");
        }
        try {
            repository.startShift(restaurantId, shiftId, actor.membershipId());
            repository.insertShiftStartedEvent(restaurantId, shiftId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_SHIFT_ALREADY_EXISTS",
                    "В ресторане уже есть открытая смена.");
        }
        return response(requiredShift(restaurantId, shiftId));
    }

    @Transactional
    public TaskResponse createTask(
            UUID restaurantId,
            UUID shiftId,
            UUID actorUserId,
            CreateTaskRequest request) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, shiftId);
        if (!List.of("PLANNED", "ACTIVE").contains(shift.status())) {
            throw conflict("SHIFT_NOT_EDITABLE", "В эту смену уже нельзя добавлять задачи.");
        }
        boolean participates = repository.findParticipants(shiftId).stream()
                .map(ParticipantRecord::membershipId)
                .anyMatch(request.assignedToMembershipId()::equals);
        if (!participates) {
            throw validation("Задачу можно назначить только сотруднику этой смены.");
        }

        String deadlineType = request.deadlineType().strip().toUpperCase(Locale.ROOT);
        Instant dueAt = resolveDeadline(shift, deadlineType, request.deadlineAt());
        UUID taskId = UUID.randomUUID();
        repository.insertTask(
                taskId,
                restaurantId,
                shiftId,
                actor.membershipId(),
                request.assignedToMembershipId(),
                request.description().strip(),
                deadlineType,
                dueAt);
        TaskRecord created = repository.findTask(restaurantId, taskId).orElseThrow(ShiftService::notFound);
        notifyTaskAssigned(restaurantId, created, request.assignedToMembershipId());
        return task(created);
    }

    @Transactional
    public TaskResponse changeTaskStatus(
            UUID restaurantId,
            UUID taskId,
            UUID actorUserId,
            ChangeTaskStatusRequest request) {
        MemberReference actor = requireMember(restaurantId, actorUserId);
        TaskRecord current = requiredTaskForUpdate(restaurantId, taskId);
        if (current.shiftId() == null) {
            throw conflict("TASK_NOT_ASSIGNED_TO_SHIFT", "Сначала назначьте перенесённую задачу в смену.");
        }
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, current.shiftId());
        String target = request.status().strip().toUpperCase(Locale.ROOT);
        boolean manager = List.of("DIRECTOR", "MANAGER").contains(actor.role());
        boolean managerCancelsDraft = manager
                && "CANCELLED".equals(target)
                && "PLANNED".equals(shift.status());
        if (!"ACTIVE".equals(shift.status()) && !managerCancelsDraft) {
            throw conflict("SHIFT_NOT_ACTIVE", "Статус задачи можно менять только в открытой смене.");
        }
        if (!manager && !"EMPLOYEE".equals(actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ACCESS_REQUIRED", "Нет доступа к задаче.");
        }
        if (!manager && !actor.membershipId().equals(current.assignedToMembershipId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ACCESS_REQUIRED", "Можно изменять только свои задачи.");
        }
        if ("CANCELLED".equals(target) && !manager) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_CANCELLATION_FORBIDDEN", "Отменять задачи может только менеджер или директор.");
        }
        validateTaskTransition(current.status(), target, manager);
        String comment = cleanOptional(request.comment());
        if ("CANCELLED".equals(target) && comment == null) {
            comment = "Отменено менеджером";
        }
        repository.changeTaskStatus(restaurantId, current, actor.membershipId(), target, comment);
        return task(requiredTask(restaurantId, taskId));
    }

    @Transactional
    public TaskResponse assignTask(
            UUID restaurantId,
            UUID taskId,
            UUID actorUserId,
            AssignTaskRequest request) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        TaskRecord current = requiredTaskForUpdate(restaurantId, taskId);
        if (current.shiftId() == null) {
            throw conflict("CARRY_OVER_ASSIGNMENT_REQUIRED", "Перенесённую задачу нужно назначить через план новой смены.");
        }
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, current.shiftId());
        if (!List.of("PLANNED", "ACTIVE").contains(shift.status())
                || List.of("COMPLETED", "CANCELLED").contains(current.status())) {
            throw conflict("TASK_NOT_EDITABLE", "Эту задачу уже нельзя переназначить.");
        }
        requireParticipant(current.shiftId(), request.assignedToMembershipId());
        if (!request.assignedToMembershipId().equals(current.assignedToMembershipId())) {
            UUID previousAssignee = current.assignedToMembershipId();
            repository.reassignTask(
                    restaurantId,
                    current,
                    actor.membershipId(),
                    request.assignedToMembershipId());
            TaskRecord reassigned = requiredTask(restaurantId, taskId);
            notifyTaskRemoved(restaurantId, reassigned, previousAssignee);
            notifyTaskAssigned(restaurantId, reassigned, request.assignedToMembershipId());
            return task(reassigned);
        }
        return task(requiredTask(restaurantId, taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> pendingCarryOver(UUID restaurantId, UUID actorUserId) {
        requireManager(restaurantId, actorUserId);
        return repository.findPendingCarryOverTasks(restaurantId).stream().map(ShiftService::task).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryResponse> taskHistory(UUID restaurantId, UUID taskId, UUID actorUserId) {
        requireManager(restaurantId, actorUserId);
        requiredTask(restaurantId, taskId);
        return repository.findTaskHistory(restaurantId, taskId).stream()
                .map(ShiftService::history)
                .toList();
    }

    @Transactional
    public TaskResponse assignCarriedTask(
            UUID restaurantId,
            UUID shiftId,
            UUID taskId,
            UUID actorUserId,
            AssignCarriedTaskRequest request) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, shiftId);
        requireDraft(shift);
        TaskRecord current = requiredTaskForUpdate(restaurantId, taskId);
        if (!"PENDING".equals(current.carryOverState())
                || current.shiftId() != null
                || current.assignedToMembershipId() != null) {
            throw conflict("TASK_NOT_PENDING_CARRY_OVER", "Задача не ожидает переноса в новую смену.");
        }
        requireParticipant(shiftId, request.assignedToMembershipId());
        String deadlineType = request.deadlineType().strip().toUpperCase(Locale.ROOT);
        Instant dueAt = resolveDeadline(shift, deadlineType, request.deadlineAt());
        repository.assignCarriedTask(
                restaurantId,
                current,
                shiftId,
                actor.membershipId(),
                request.assignedToMembershipId(),
                deadlineType,
                dueAt);
        TaskRecord assigned = requiredTask(restaurantId, taskId);
        notifyTaskAssigned(restaurantId, assigned, request.assignedToMembershipId());
        return task(assigned);
    }

    @Transactional
    public ShiftReportResponse close(
            UUID restaurantId,
            UUID shiftId,
            UUID actorUserId,
            CloseShiftRequest request) {
        MemberReference actor = requireManager(restaurantId, actorUserId);
        ShiftRecord shift = requiredShiftForUpdate(restaurantId, shiftId);
        if (!"ACTIVE".equals(shift.status())) {
            throw conflict("SHIFT_NOT_ACTIVE", "Завершить можно только открытую смену.");
        }

        List<TaskRecord> shiftTasks = repository.findShiftTasks(shiftId);
        List<TaskRecord> unfinished = shiftTasks.stream()
                .filter(task -> List.of("CREATED", "IN_PROGRESS").contains(task.status()))
                .toList();
        Map<UUID, TaskResolutionRequest> resolutions = request.unfinishedTasks().stream()
                .collect(Collectors.toMap(
                        TaskResolutionRequest::taskId,
                        Function.identity(),
                        (left, right) -> {
                            throw validation("Для каждой незавершённой задачи решение указывается один раз.");
                        },
                        LinkedHashMap::new));
        Set<UUID> unfinishedIds = unfinished.stream().map(TaskRecord::id).collect(Collectors.toSet());
        if (!unfinishedIds.equals(resolutions.keySet())) {
            throw validation("Перед закрытием смены выберите перенос или отмену для каждой незавершённой задачи.");
        }

        int cancelled = (int) shiftTasks.stream().filter(task -> "CANCELLED".equals(task.status())).count();
        int carried = 0;
        for (TaskRecord task : unfinished) {
            TaskResolutionRequest resolution = resolutions.get(task.id());
            String action = resolution.action().strip().toUpperCase(Locale.ROOT);
            String comment = cleanOptional(resolution.comment());
            if (!List.of("CARRY_OVER", "CANCEL").contains(action)) {
                throw validation("Действие для незавершённой задачи должно быть CARRY_OVER или CANCEL.");
            }
            repository.insertResolution(
                    restaurantId,
                    shiftId,
                    task.id(),
                    action,
                    actor.membershipId(),
                    comment);
            if ("CARRY_OVER".equals(action)) {
                repository.carryOverTask(restaurantId, task, actor.membershipId(), comment);
                carried++;
            } else {
                repository.changeTaskStatus(
                        restaurantId,
                        task,
                        actor.membershipId(),
                        "CANCELLED",
                        comment == null ? "Отменено при закрытии смены" : comment);
                cancelled++;
            }
        }

        int completed = (int) shiftTasks.stream().filter(task -> "COMPLETED".equals(task.status())).count();
        repository.closeShift(restaurantId, shiftId, actor.membershipId());
        repository.insertShiftReport(
                restaurantId,
                shiftId,
                actor.membershipId(),
                shiftTasks.size(),
                completed,
                cancelled,
                carried);
        return report(restaurantId, shiftId);
    }

    @Transactional(readOnly = true)
    public ShiftReportResponse getReport(UUID restaurantId, UUID shiftId, UUID actorUserId) {
        requireManager(restaurantId, actorUserId);
        return report(restaurantId, shiftId);
    }

    @Transactional(readOnly = true)
    public List<ShiftSummaryResponse> list(UUID restaurantId, UUID actorUserId) {
        requireManager(restaurantId, actorUserId);
        return repository.findShifts(restaurantId).stream()
                .map(shift -> {
                    ReportMetricsRecord saved = repository.findReportMetrics(restaurantId, shift.id()).orElse(null);
                    int tasks = saved == null ? (int) repository.countTasks(shift.id()) : saved.totalTasks();
                    int completed = saved == null
                            ? (int) repository.countCompletedTasks(shift.id())
                            : saved.completedTasks();
                    return new ShiftSummaryResponse(
                            shift.id(),
                            shift.name(),
                            shift.scheduledStartAt(),
                            shift.scheduledEndAt(),
                            shift.actualStartedAt(),
                            shift.actualClosedAt(),
                            shift.status(),
                            shift.responsibleMembershipId(),
                            (int) repository.countParticipants(shift.id()),
                            tasks,
                            completed);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(UUID restaurantId, UUID shiftId, UUID actorUserId) {
        requireManager(restaurantId, actorUserId);
        return response(requiredShift(restaurantId, shiftId));
    }

    @Transactional(readOnly = true)
    public CurrentShiftResponse current(UUID restaurantId, UUID userId) {
        MemberReference member = requireMember(restaurantId, userId);
        ShiftRecord shift = repository.findActiveShiftForMember(restaurantId, member.membershipId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CURRENT_SHIFT_NOT_FOUND",
                        "У сотрудника сейчас нет открытой смены."));
        return new CurrentShiftResponse(
                shift.id(),
                shift.restaurantId(),
                shift.scheduledStartAt(),
                shift.scheduledEndAt(),
                shift.actualStartedAt(),
                shift.status(),
                repository.findAssignedTables(shift.id(), member.membershipId()),
                repository.findPersonalPlan(shift.id(), member.membershipId()).stream()
                        .map(ShiftService::task)
                        .toList());
    }

    private ShiftResponse response(ShiftRecord shift) {
        Map<UUID, List<UUID>> assignedTables = new LinkedHashMap<>();
        Map<UUID, String> positions = new LinkedHashMap<>();
        for (ParticipantRecord participant : repository.findParticipants(shift.id())) {
            assignedTables.computeIfAbsent(participant.membershipId(), ignored -> new ArrayList<>());
            positions.putIfAbsent(participant.membershipId(), participant.position());
            if (participant.tableId() != null) {
                assignedTables.get(participant.membershipId()).add(participant.tableId());
            }
        }
        List<ParticipantResponse> participants = assignedTables.entrySet().stream()
                .map(entry -> new ParticipantResponse(
                        entry.getKey(),
                        positions.get(entry.getKey()),
                        List.copyOf(entry.getValue())))
                .toList();
        return new ShiftResponse(
                shift.id(),
                shift.restaurantId(),
                shift.name(),
                shift.scheduledStartAt(),
                shift.scheduledEndAt(),
                shift.actualStartedAt(),
                shift.actualClosedAt(),
                shift.status(),
                shift.responsibleMembershipId(),
                participants,
                repository.findShiftTasks(shift.id()).stream().map(ShiftService::task).toList());
    }

    private MemberReference requireManager(UUID restaurantId, UUID userId) {
        MemberReference member = requireMember(restaurantId, userId);
        if (!List.of("DIRECTOR", "MANAGER").contains(member.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SHIFT_MANAGEMENT_REQUIRED", "Нет доступа к управлению сменой.");
        }
        return member;
    }

    private MemberReference requireMember(UUID restaurantId, UUID userId) {
        try {
            MemberReference member = workforceClient.findMember(restaurantId, userId);
            if (member == null || member.membershipId() == null) {
                throw new RestClientException("Empty workforce response");
            }
            return member;
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "RESTAURANT_MEMBERSHIP_REQUIRED",
                    "Нет активного доступа к ресторану.");
        }
    }

    private List<EmployeeAssignmentReference> validateEmployees(UUID restaurantId, List<UUID> employeeIds) {
        try {
            List<EmployeeAssignmentReference> employees = workforceClient.validateEmployees(restaurantId, employeeIds);
            if (employees.size() != employeeIds.size()) {
                throw new RestClientException("Incomplete workforce response");
            }
            return employees;
        } catch (RestClientException exception) {
            throw validation("В смену можно добавить только активных сотрудников этого ресторана.");
        }
    }

    private void validateTables(UUID restaurantId, List<UUID> tableIds) {
        try {
            if (restaurantClient.validateTables(restaurantId, tableIds).size() != tableIds.size()) {
                throw new RestClientException("Incomplete restaurant response");
            }
        } catch (RestClientException exception) {
            throw validation("Можно закрепить только активные столы этого ресторана.");
        }
    }

    private List<UUID> activeTables(UUID restaurantId) {
        try {
            return restaurantClient.activeTables(restaurantId);
        } catch (RestClientException exception) {
            throw validation("Не удалось получить список активных столов ресторана.");
        }
    }

    private void notifyTaskAssigned(UUID restaurantId, TaskRecord task, UUID membershipId) {
        createTaskNotification(
                restaurantId,
                task,
                membershipId,
                "TASK_ASSIGNED",
                "Новая задача",
                "Вам назначена задача: " + task.description());
    }

    private void notifyTaskRemoved(UUID restaurantId, TaskRecord task, UUID membershipId) {
        createTaskNotification(
                restaurantId,
                task,
                membershipId,
                "TASK_REASSIGNED",
                "Ответственный изменён",
                "Задача «" + task.description() + "» переназначена другому сотруднику");
    }

    private void createTaskNotification(
            UUID restaurantId,
            TaskRecord task,
            UUID membershipId,
            String type,
            String title,
            String message) {
        try {
            MemberReference recipient = workforceClient.findMemberByMembership(restaurantId, membershipId);
            if (recipient == null || recipient.userId() == null) {
                throw new RestClientException("Empty workforce response");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.id().toString());
            if (task.shiftId() != null) {
                payload.put("shiftId", task.shiftId().toString());
            }
            payload.put("description", task.description());
            payload.put("assignedToMembershipId", task.assignedToMembershipId().toString());
            notificationClient.create(recipient.userId(), restaurantId, type, title, message, payload);
        } catch (RestClientException ignored) {
            // The corresponding task event remains in the transactional outbox for asynchronous retry.
        }
    }

    private ShiftRecord requiredShift(UUID restaurantId, UUID shiftId) {
        return repository.findShift(restaurantId, shiftId).orElseThrow(ShiftService::notFound);
    }

    private ShiftRecord requiredShiftForUpdate(UUID restaurantId, UUID shiftId) {
        return repository.findShiftForUpdate(restaurantId, shiftId).orElseThrow(ShiftService::notFound);
    }

    private TaskRecord requiredTask(UUID restaurantId, UUID taskId) {
        return repository.findTask(restaurantId, taskId).orElseThrow(ShiftService::notFound);
    }

    private TaskRecord requiredTaskForUpdate(UUID restaurantId, UUID taskId) {
        return repository.findTaskForUpdate(restaurantId, taskId).orElseThrow(ShiftService::notFound);
    }

    private void requireParticipant(UUID shiftId, UUID membershipId) {
        boolean participates = repository.findParticipants(shiftId).stream()
                .map(ParticipantRecord::membershipId)
                .anyMatch(membershipId::equals);
        if (!participates) {
            throw validation("Задачу можно назначить только сотруднику этой смены.");
        }
    }

    private ShiftReportResponse report(UUID restaurantId, UUID shiftId) {
        ShiftRecord shift = requiredShift(restaurantId, shiftId);
        ReportMetricsRecord metrics = repository.findReportMetrics(restaurantId, shiftId)
                .orElseThrow(() -> conflict("SHIFT_REPORT_NOT_READY", "Отчёт появится после завершения смены."));
        List<UUID> employees = repository.findParticipants(shiftId).stream()
                .map(ParticipantRecord::membershipId)
                .distinct()
                .toList();
        return new ShiftReportResponse(
                shift.id(),
                shift.restaurantId(),
                shift.actualStartedAt(),
                shift.actualClosedAt(),
                metrics.managerMembershipId(),
                employees,
                metrics.totalTasks(),
                metrics.completedTasks(),
                metrics.cancelledTasks(),
                metrics.carriedOverTasks());
    }

    private static void requireDraft(ShiftRecord shift) {
        if (!"PLANNED".equals(shift.status())) {
            throw conflict("SHIFT_NOT_DRAFT", "Изменять состав и столы можно только в черновике смены.");
        }
    }

    private static Instant resolveDeadline(ShiftRecord shift, String type, Instant explicitDeadline) {
        if ("NONE".equals(type)) {
            if (explicitDeadline != null) {
                throw validation("Для задачи без дедлайна время не указывается.");
            }
            return null;
        }
        if ("SHIFT_END".equals(type)) {
            if (explicitDeadline != null) {
                throw validation("Для дедлайна до конца смены время вычисляется автоматически.");
            }
            return shift.scheduledEndAt();
        }
        if (!"TIME".equals(type) || explicitDeadline == null) {
            throw validation("Укажите тип дедлайна NONE, TIME или SHIFT_END и необходимое время.");
        }
        if (explicitDeadline.isBefore(shift.scheduledStartAt())
                || explicitDeadline.isAfter(shift.scheduledEndAt())) {
            throw validation("Временной дедлайн должен находиться в пределах смены.");
        }
        return explicitDeadline;
    }

    private static void validateTaskTransition(String current, String target, boolean manager) {
        if ("CREATED".equals(current) && List.of("IN_PROGRESS", "COMPLETED").contains(target)) {
            return;
        }
        if ("IN_PROGRESS".equals(current) && "COMPLETED".equals(target)) {
            return;
        }
        if (manager
                && List.of("CREATED", "IN_PROGRESS").contains(current)
                && "CANCELLED".equals(target)) {
            return;
        }
        throw conflict("INVALID_TASK_TRANSITION", "Недопустимое изменение статуса задачи.");
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static TaskResponse task(TaskRecord task) {
        return new TaskResponse(
                task.id(),
                task.shiftId(),
                task.description(),
                task.assignedToMembershipId(),
                task.status(),
                task.deadlineType(),
                task.deadlineAt(),
                task.completedAt(),
                task.cancellationReason(),
                task.createdAt());
    }

    private static TaskHistoryResponse history(TaskHistoryRecord history) {
        return new TaskHistoryResponse(
                history.id(),
                history.eventType(),
                history.actorMembershipId(),
                history.oldStatus(),
                history.newStatus(),
                history.oldAssigneeMembershipId(),
                history.newAssigneeMembershipId(),
                history.comment(),
                history.createdAt());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "SHIFT_RESOURCE_NOT_FOUND", "Смена или задача не найдена.");
    }

    private static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
