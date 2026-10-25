package com.restaurant.operations.shift.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftRepository {

    private final JdbcClient jdbc;

    public ShiftRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertShift(
            UUID id,
            UUID restaurantId,
            String name,
            Instant start,
            Instant end,
            UUID actorMembershipId) {
        jdbc.sql("""
                        INSERT INTO shifts (
                            id, restaurant_id, name, scheduled_start_at, scheduled_end_at,
                            responsible_membership_id, created_by_membership_id
                        ) VALUES (
                            :id, :restaurantId, :name, :start, :end, :actor, :actor
                        )
                        """)
                .param("id", id)
                .param("restaurantId", restaurantId)
                .param("name", name)
                .param("start", time(start))
                .param("end", time(end))
                .param("actor", actorMembershipId)
                .update();
    }

    public Optional<ShiftRecord> findShift(UUID restaurantId, UUID shiftId) {
        return jdbc.sql(shiftSql() + " WHERE restaurant_id = :restaurantId AND id = :shiftId")
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .query(ShiftRepository::mapShift)
                .optional();
    }

    public Optional<ShiftRecord> findShiftForUpdate(UUID restaurantId, UUID shiftId) {
        return jdbc.sql(shiftSql() + " WHERE restaurant_id = :restaurantId AND id = :shiftId FOR UPDATE")
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .query(ShiftRepository::mapShift)
                .optional();
    }

    public List<ShiftRecord> findShifts(UUID restaurantId) {
        return jdbc.sql(shiftSql() + " WHERE restaurant_id = :restaurantId ORDER BY scheduled_start_at DESC")
                .param("restaurantId", restaurantId)
                .query(ShiftRepository::mapShift)
                .list();
    }

    public Optional<ShiftRecord> findActiveShiftForMember(UUID restaurantId, UUID membershipId) {
        return jdbc.sql(shiftSql() + """
                         JOIN shift_participants participant ON participant.shift_id = shifts.id
                         WHERE shifts.restaurant_id = :restaurantId
                           AND shifts.status = 'ACTIVE'
                           AND participant.membership_id = :membershipId
                        """)
                .param("restaurantId", restaurantId)
                .param("membershipId", membershipId)
                .query(ShiftRepository::mapShift)
                .optional();
    }

    public void clearConfiguration(UUID shiftId) {
        jdbc.sql("DELETE FROM shift_participants WHERE shift_id = :shiftId")
                .param("shiftId", shiftId)
                .update();
    }

    public void insertParticipant(UUID restaurantId, UUID shiftId, UUID membershipId, UUID actorMembershipId) {
        jdbc.sql("""
                        INSERT INTO shift_participants (
                            restaurant_id, shift_id, membership_id, added_by_membership_id
                        ) VALUES (:restaurantId, :shiftId, :membershipId, :actor)
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("membershipId", membershipId)
                .param("actor", actorMembershipId)
                .update();
    }

    public void insertTableAssignment(
            UUID restaurantId,
            UUID shiftId,
            UUID tableId,
            UUID membershipId,
            String assignmentRole,
            UUID actorMembershipId) {
        jdbc.sql("""
                        INSERT INTO shift_table_assignments (
                            restaurant_id, shift_id, table_id, membership_id,
                            assignment_role, assigned_by_membership_id
                        ) VALUES (
                            :restaurantId, :shiftId, :tableId, :membershipId,
                            :assignmentRole, :actor
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("tableId", tableId)
                .param("membershipId", membershipId)
                .param("assignmentRole", assignmentRole)
                .param("actor", actorMembershipId)
                .update();
    }

    public List<ParticipantRecord> findParticipants(UUID shiftId) {
        return jdbc.sql("""
                        SELECT participant.membership_id, assignment.assignment_role, assignment.table_id
                        FROM shift_participants participant
                        LEFT JOIN shift_table_assignments assignment
                          ON assignment.shift_id = participant.shift_id
                         AND assignment.membership_id = participant.membership_id
                        WHERE participant.shift_id = :shiftId
                        ORDER BY participant.created_at, assignment.created_at
                        """)
                .param("shiftId", shiftId)
                .query((result, rowNumber) -> new ParticipantRecord(
                        result.getObject("membership_id", UUID.class),
                        result.getString("assignment_role"),
                        result.getObject("table_id", UUID.class)))
                .list();
    }

    public List<UUID> findTaskAssignees(UUID shiftId) {
        return jdbc.sql("""
                        SELECT DISTINCT assigned_to_membership_id
                        FROM tasks
                        WHERE shift_id = :shiftId
                          AND status <> 'CANCELLED'
                          AND assigned_to_membership_id IS NOT NULL
                        """)
                .param("shiftId", shiftId)
                .query(UUID.class)
                .list();
    }

    public boolean hasParticipantsWithoutTables(UUID shiftId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM shift_participants participant
                            WHERE participant.shift_id = :shiftId
                              AND NOT EXISTS (
                                  SELECT 1 FROM shift_table_assignments assignment
                                  WHERE assignment.shift_id = participant.shift_id
                                    AND assignment.membership_id = participant.membership_id
                              )
                        )
                        """)
                .param("shiftId", shiftId)
                .query(Boolean.class)
                .single();
    }

    public long countParticipants(UUID shiftId) {
        return jdbc.sql("SELECT count(*) FROM shift_participants WHERE shift_id = :shiftId")
                .param("shiftId", shiftId)
                .query(Long.class)
                .single();
    }

    public void startShift(UUID restaurantId, UUID shiftId, UUID actorMembershipId) {
        jdbc.sql("""
                        UPDATE shifts
                        SET status = 'ACTIVE', actual_started_at = CURRENT_TIMESTAMP,
                            started_by_membership_id = :actor
                        WHERE restaurant_id = :restaurantId AND id = :shiftId AND status = 'PLANNED'
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("actor", actorMembershipId)
                .update();
    }

    public void closeShift(UUID restaurantId, UUID shiftId, UUID actorMembershipId) {
        jdbc.sql("""
                        UPDATE shifts
                        SET status = 'CLOSED', actual_closed_at = CURRENT_TIMESTAMP,
                            closed_by_membership_id = :actor
                        WHERE restaurant_id = :restaurantId AND id = :shiftId AND status = 'ACTIVE'
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("actor", actorMembershipId)
                .update();
    }

    public void insertTask(
            UUID id,
            UUID restaurantId,
            UUID shiftId,
            UUID actorMembershipId,
            UUID assigneeMembershipId,
            String description,
            String deadlineType,
            Instant dueAt) {
        jdbc.sql("""
                        INSERT INTO tasks (
                            id, restaurant_id, shift_id, created_by_membership_id,
                            assigned_to_membership_id, title, deadline_type, due_at
                        ) VALUES (
                            :id, :restaurantId, :shiftId, :actor,
                            :assignee, :description, :deadlineType, :dueAt
                        )
                        """)
                .param("id", id)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("actor", actorMembershipId)
                .param("assignee", assigneeMembershipId)
                .param("description", description)
                .param("deadlineType", deadlineType)
                .param("dueAt", dueAt == null ? null : time(dueAt))
                .update();
        jdbc.sql("""
                        INSERT INTO task_history (
                            restaurant_id, task_id, actor_membership_id, event_type,
                            new_status, new_assignee_membership_id, to_shift_id
                        ) VALUES (
                            :restaurantId, :taskId, :actor, 'CREATED',
                            'CREATED', :assignee, :shiftId
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("taskId", id)
                .param("actor", actorMembershipId)
                .param("assignee", assigneeMembershipId)
                .param("shiftId", shiftId)
                .update();
        insertOutboxEvent(
                id,
                "Task",
                "operations.task.created.v1",
                restaurantId,
                "CREATED",
                assigneeMembershipId,
                null);
    }

    public Optional<TaskRecord> findTask(UUID restaurantId, UUID taskId) {
        return jdbc.sql(taskSql() + " WHERE restaurant_id = :restaurantId AND id = :taskId")
                .param("restaurantId", restaurantId)
                .param("taskId", taskId)
                .query(ShiftRepository::mapTask)
                .optional();
    }

    public Optional<TaskRecord> findTaskForUpdate(UUID restaurantId, UUID taskId) {
        return jdbc.sql(taskSql() + " WHERE restaurant_id = :restaurantId AND id = :taskId FOR UPDATE")
                .param("restaurantId", restaurantId)
                .param("taskId", taskId)
                .query(ShiftRepository::mapTask)
                .optional();
    }

    public List<TaskRecord> findShiftTasks(UUID shiftId) {
        return jdbc.sql(taskSql() + """
                         WHERE shift_id = :shiftId
                           AND archived_at IS NULL
                         ORDER BY created_at
                        """)
                .param("shiftId", shiftId)
                .query(ShiftRepository::mapTask)
                .list();
    }

    public List<TaskRecord> findPersonalPlan(UUID shiftId, UUID membershipId) {
        return jdbc.sql(taskSql() + """
                         WHERE shift_id = :shiftId
                           AND assigned_to_membership_id = :membershipId
                           AND archived_at IS NULL
                           AND status <> 'CANCELLED'
                         ORDER BY
                           CASE WHEN status IN ('CREATED', 'IN_PROGRESS') THEN 0 ELSE 1 END,
                           CASE WHEN status IN ('CREATED', 'IN_PROGRESS') AND due_at IS NULL THEN 1 ELSE 0 END,
                           CASE WHEN status IN ('CREATED', 'IN_PROGRESS') THEN due_at END ASC,
                           CASE WHEN status = 'COMPLETED' THEN completed_at END ASC,
                           created_at ASC
                        """)
                .param("shiftId", shiftId)
                .param("membershipId", membershipId)
                .query(ShiftRepository::mapTask)
                .list();
    }

    public List<TaskRecord> findPendingCarryOverTasks(UUID restaurantId) {
        return jdbc.sql(taskSql() + """
                         WHERE restaurant_id = :restaurantId
                           AND carry_over_state = 'PENDING'
                           AND shift_id IS NULL
                           AND assigned_to_membership_id IS NULL
                         ORDER BY created_at
                        """)
                .param("restaurantId", restaurantId)
                .query(ShiftRepository::mapTask)
                .list();
    }

    public List<TaskHistoryRecord> findTaskHistory(UUID restaurantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT history.id, history.event_type, history.actor_membership_id,
                               history.old_status, history.new_status,
                               history.old_assignee_membership_id,
                               history.new_assignee_membership_id,
                               history.comment, history.created_at
                        FROM task_history history
                        JOIN tasks task
                          ON task.id = history.task_id
                         AND task.restaurant_id = history.restaurant_id
                        WHERE history.restaurant_id = :restaurantId
                          AND history.task_id = :taskId
                        ORDER BY history.created_at, history.id
                        """)
                .param("restaurantId", restaurantId)
                .param("taskId", taskId)
                .query((result, rowNumber) -> new TaskHistoryRecord(
                        result.getObject("id", UUID.class),
                        result.getString("event_type"),
                        result.getObject("actor_membership_id", UUID.class),
                        result.getString("old_status"),
                        result.getString("new_status"),
                        result.getObject("old_assignee_membership_id", UUID.class),
                        result.getObject("new_assignee_membership_id", UUID.class),
                        result.getString("comment"),
                        instant(result, "created_at")))
                .list();
    }

    public List<TaskRecord> findUnfinishedTasks(UUID shiftId) {
        return jdbc.sql(taskSql() + """
                         WHERE shift_id = :shiftId
                           AND status IN ('CREATED', 'IN_PROGRESS')
                         ORDER BY created_at
                        """)
                .param("shiftId", shiftId)
                .query(ShiftRepository::mapTask)
                .list();
    }

    public void changeTaskStatus(
            UUID restaurantId,
            TaskRecord task,
            UUID actorMembershipId,
            String newStatus,
            String comment) {
        jdbc.sql("""
                        UPDATE tasks
                        SET status = :newStatus,
                            accepted_at = CASE
                                WHEN :newStatus = 'IN_PROGRESS' THEN CURRENT_TIMESTAMP
                                ELSE accepted_at
                            END,
                            completed_at = CASE
                                WHEN :newStatus = 'COMPLETED' THEN CURRENT_TIMESTAMP
                                ELSE completed_at
                            END,
                            cancelled_at = CASE
                                WHEN :newStatus = 'CANCELLED' THEN CURRENT_TIMESTAMP
                                ELSE cancelled_at
                            END,
                            cancellation_reason = CASE
                                WHEN :newStatus = 'CANCELLED' THEN :comment
                                ELSE cancellation_reason
                            END
                        WHERE id = :taskId AND restaurant_id = :restaurantId
                        """)
                .param("newStatus", newStatus)
                .param("comment", comment)
                .param("taskId", task.id())
                .param("restaurantId", restaurantId)
                .update();
        insertTaskHistory(
                restaurantId,
                task.id(),
                actorMembershipId,
                "CANCELLED".equals(newStatus) ? "CANCELLED" : "STATUS_CHANGED",
                task.status(),
                newStatus,
                task.assignedToMembershipId(),
                task.assignedToMembershipId(),
                task.shiftId(),
                task.shiftId(),
                comment);
        insertOutboxEvent(
                task.id(),
                "Task",
                "operations.task.status-changed.v1",
                restaurantId,
                newStatus,
                task.assignedToMembershipId(),
                null);
    }

    public void reassignTask(
            UUID restaurantId,
            TaskRecord task,
            UUID actorMembershipId,
            UUID newAssigneeMembershipId) {
        jdbc.sql("""
                        UPDATE tasks
                        SET assigned_to_membership_id = :newAssignee
                        WHERE id = :taskId AND restaurant_id = :restaurantId
                        """)
                .param("newAssignee", newAssigneeMembershipId)
                .param("taskId", task.id())
                .param("restaurantId", restaurantId)
                .update();
        insertTaskHistory(
                restaurantId,
                task.id(),
                actorMembershipId,
                "ASSIGNED",
                task.status(),
                task.status(),
                task.assignedToMembershipId(),
                newAssigneeMembershipId,
                task.shiftId(),
                task.shiftId(),
                null);
        insertOutboxEvent(
                task.id(),
                "Task",
                "operations.task.assigned.v1",
                restaurantId,
                task.status(),
                newAssigneeMembershipId,
                task.assignedToMembershipId());
    }

    public void carryOverTask(
            UUID restaurantId,
            TaskRecord task,
            UUID actorMembershipId,
            String comment) {
        jdbc.sql("""
                        UPDATE tasks
                        SET carried_from_shift_id = :fromShiftId,
                            shift_id = NULL,
                            assigned_to_membership_id = NULL,
                            carry_over_state = 'PENDING',
                            deadline_type = 'NONE',
                            due_at = NULL,
                            accepted_at = NULL
                        WHERE id = :taskId AND restaurant_id = :restaurantId
                        """)
                .param("fromShiftId", task.shiftId())
                .param("taskId", task.id())
                .param("restaurantId", restaurantId)
                .update();
        insertTaskHistory(
                restaurantId,
                task.id(),
                actorMembershipId,
                "CARRIED_OVER",
                task.status(),
                task.status(),
                task.assignedToMembershipId(),
                null,
                task.shiftId(),
                null,
                comment);
    }

    public void assignCarriedTask(
            UUID restaurantId,
            TaskRecord task,
            UUID shiftId,
            UUID actorMembershipId,
            UUID assigneeMembershipId,
            String deadlineType,
            Instant dueAt) {
        jdbc.sql("""
                        UPDATE tasks
                        SET shift_id = :shiftId,
                            assigned_to_membership_id = :assignee,
                            carry_over_state = 'ASSIGNED',
                            deadline_type = :deadlineType,
                            due_at = :dueAt
                        WHERE id = :taskId
                          AND restaurant_id = :restaurantId
                          AND carry_over_state = 'PENDING'
                        """)
                .param("shiftId", shiftId)
                .param("assignee", assigneeMembershipId)
                .param("deadlineType", deadlineType)
                .param("dueAt", dueAt == null ? null : time(dueAt))
                .param("taskId", task.id())
                .param("restaurantId", restaurantId)
                .update();
        insertTaskHistory(
                restaurantId,
                task.id(),
                actorMembershipId,
                "ASSIGNED",
                task.status(),
                task.status(),
                null,
                assigneeMembershipId,
                task.shiftId(),
                shiftId,
                "Перенесённая задача назначена в новую смену");
        insertOutboxEvent(
                task.id(),
                "Task",
                "operations.task.assigned.v1",
                restaurantId,
                task.status(),
                assigneeMembershipId,
                null);
    }

    public void insertResolution(
            UUID restaurantId,
            UUID shiftId,
            UUID taskId,
            String action,
            UUID actorMembershipId,
            String comment) {
        jdbc.sql("""
                        INSERT INTO shift_task_resolutions (
                            restaurant_id, shift_id, task_id, action, decided_by_membership_id, comment
                        ) VALUES (
                            :restaurantId, :shiftId, :taskId, :action, :actor, :comment
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("taskId", taskId)
                .param("action", action)
                .param("actor", actorMembershipId)
                .param("comment", comment)
                .update();
    }

    public void insertShiftReport(
            UUID restaurantId,
            UUID shiftId,
            UUID actorMembershipId,
            int totalTasks,
            int completedTasks,
            int cancelledTasks,
            int carriedOverTasks) {
        jdbc.sql("""
                        INSERT INTO shift_reports (
                            restaurant_id, shift_id, generated_by_membership_id, metrics
                        ) VALUES (
                            :restaurantId, :shiftId, :actor,
                            jsonb_build_object(
                                'totalTasks', :totalTasks,
                                'completedTasks', :completedTasks,
                                'cancelledTasks', :cancelledTasks,
                                'carriedOverTasks', :carriedOverTasks
                            )
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .param("actor", actorMembershipId)
                .param("totalTasks", totalTasks)
                .param("completedTasks", completedTasks)
                .param("cancelledTasks", cancelledTasks)
                .param("carriedOverTasks", carriedOverTasks)
                .update();
        insertOutboxEvent(
                shiftId,
                "Shift",
                "operations.shift.closed.v1",
                restaurantId,
                "CLOSED",
                null,
                null);
    }

    public Optional<ReportMetricsRecord> findReportMetrics(UUID restaurantId, UUID shiftId) {
        return jdbc.sql("""
                        SELECT generated_by_membership_id,
                               (metrics ->> 'totalTasks')::integer AS total_tasks,
                               (metrics ->> 'completedTasks')::integer AS completed_tasks,
                               (metrics ->> 'cancelledTasks')::integer AS cancelled_tasks,
                               (metrics ->> 'carriedOverTasks')::integer AS carried_over_tasks
                        FROM shift_reports
                        WHERE restaurant_id = :restaurantId AND shift_id = :shiftId
                        """)
                .param("restaurantId", restaurantId)
                .param("shiftId", shiftId)
                .query((result, rowNumber) -> new ReportMetricsRecord(
                        result.getObject("generated_by_membership_id", UUID.class),
                        result.getInt("total_tasks"),
                        result.getInt("completed_tasks"),
                        result.getInt("cancelled_tasks"),
                        result.getInt("carried_over_tasks")))
                .optional();
    }

    public long countTasks(UUID shiftId) {
        return jdbc.sql("SELECT count(*) FROM tasks WHERE shift_id = :shiftId")
                .param("shiftId", shiftId)
                .query(Long.class)
                .single();
    }

    public long countCompletedTasks(UUID shiftId) {
        return jdbc.sql("SELECT count(*) FROM tasks WHERE shift_id = :shiftId AND status = 'COMPLETED'")
                .param("shiftId", shiftId)
                .query(Long.class)
                .single();
    }

    public List<UUID> findAssignedTables(UUID shiftId, UUID membershipId) {
        return jdbc.sql("""
                        SELECT table_id
                        FROM shift_table_assignments
                        WHERE shift_id = :shiftId AND membership_id = :membershipId
                        ORDER BY created_at
                        """)
                .param("shiftId", shiftId)
                .param("membershipId", membershipId)
                .query(UUID.class)
                .list();
    }

    public Optional<TableAssignmentRecord> findActiveTableAssignments(UUID restaurantId, UUID tableId) {
        return jdbc.sql("""
                        SELECT shifts.id AS shift_id,
                               shifts.started_by_membership_id AS manager_membership_id,
                               (ARRAY_AGG(assignment.membership_id) FILTER (
                                   WHERE assignment.assignment_role = 'WAITER'
                               ))[1] AS waiter_membership_id,
                               (ARRAY_AGG(assignment.membership_id) FILTER (
                                   WHERE assignment.assignment_role = 'RUNNER'
                               ))[1] AS runner_membership_id
                        FROM shifts
                        JOIN shift_table_assignments assignment ON assignment.shift_id = shifts.id
                        WHERE shifts.restaurant_id = :restaurantId
                          AND shifts.status = 'ACTIVE'
                          AND assignment.table_id = :tableId
                        GROUP BY shifts.id
                        """)
                .param("restaurantId", restaurantId)
                .param("tableId", tableId)
                .query((result, rowNumber) -> new TableAssignmentRecord(
                        result.getObject("shift_id", UUID.class),
                        result.getObject("manager_membership_id", UUID.class),
                        result.getObject("waiter_membership_id", UUID.class),
                        result.getObject("runner_membership_id", UUID.class)))
                .optional();
    }

    private void insertTaskHistory(
            UUID restaurantId,
            UUID taskId,
            UUID actorMembershipId,
            String eventType,
            String oldStatus,
            String newStatus,
            UUID oldAssignee,
            UUID newAssignee,
            UUID fromShiftId,
            UUID toShiftId,
            String comment) {
        jdbc.sql("""
                        INSERT INTO task_history (
                            restaurant_id, task_id, actor_membership_id, event_type,
                            old_status, new_status, old_assignee_membership_id,
                            new_assignee_membership_id, from_shift_id, to_shift_id, comment
                        ) VALUES (
                            :restaurantId, :taskId, :actor, :eventType,
                            :oldStatus, :newStatus, :oldAssignee,
                            :newAssignee, :fromShiftId, :toShiftId, :comment
                        )
                        """)
                .param("restaurantId", restaurantId)
                .param("taskId", taskId)
                .param("actor", actorMembershipId)
                .param("eventType", eventType)
                .param("oldStatus", oldStatus)
                .param("newStatus", newStatus)
                .param("oldAssignee", oldAssignee)
                .param("newAssignee", newAssignee)
                .param("fromShiftId", fromShiftId)
                .param("toShiftId", toShiftId)
                .param("comment", comment)
                .update();
    }

    public void insertShiftStartedEvent(UUID restaurantId, UUID shiftId) {
        insertOutboxEvent(
                shiftId,
                "Shift",
                "operations.shift.started.v1",
                restaurantId,
                "ACTIVE",
                null,
                null);
    }

    private void insertOutboxEvent(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            UUID restaurantId,
            String status,
            UUID assigneeMembershipId,
            UUID previousAssigneeMembershipId) {
        jdbc.sql("""
                        INSERT INTO outbox_events (
                            aggregate_type, aggregate_id, event_type, payload
                        ) VALUES (
                            :aggregateType, :aggregateId, :eventType,
                            jsonb_strip_nulls(jsonb_build_object(
                                'restaurantId', CAST(:restaurantId AS text),
                                'status', CAST(:status AS text),
                                'assignedToMembershipId', CAST(:assignee AS text),
                                'previousAssigneeMembershipId', CAST(:previousAssignee AS text)
                            ))
                        )
                        """)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("restaurantId", restaurantId)
                .param("status", status)
                .param("assignee", assigneeMembershipId)
                .param("previousAssignee", previousAssigneeMembershipId)
                .update();
    }

    private static String shiftSql() {
        return """
                SELECT shifts.id, shifts.restaurant_id, shifts.name,
                       shifts.scheduled_start_at, shifts.scheduled_end_at,
                       shifts.actual_started_at, shifts.actual_closed_at,
                       shifts.status, shifts.responsible_membership_id
                FROM shifts
                """;
    }

    private static String taskSql() {
        return """
                SELECT id, shift_id, title, assigned_to_membership_id, status,
                       deadline_type, due_at, completed_at, cancellation_reason,
                       created_at, carry_over_state
                FROM tasks
                """;
    }

    private static ShiftRecord mapShift(ResultSet result, int rowNumber) throws SQLException {
        return new ShiftRecord(
                result.getObject("id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getString("name"),
                instant(result, "scheduled_start_at"),
                instant(result, "scheduled_end_at"),
                nullableInstant(result, "actual_started_at"),
                nullableInstant(result, "actual_closed_at"),
                result.getString("status"),
                result.getObject("responsible_membership_id", UUID.class));
    }

    private static TaskRecord mapTask(ResultSet result, int rowNumber) throws SQLException {
        return new TaskRecord(
                result.getObject("id", UUID.class),
                result.getObject("shift_id", UUID.class),
                result.getString("title"),
                result.getObject("assigned_to_membership_id", UUID.class),
                result.getString("status"),
                result.getString("deadline_type"),
                nullableInstant(result, "due_at"),
                nullableInstant(result, "completed_at"),
                result.getString("cancellation_reason"),
                instant(result, "created_at"),
                result.getString("carry_over_state"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record ShiftRecord(
            UUID id,
            UUID restaurantId,
            String name,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartedAt,
            Instant actualClosedAt,
            String status,
            UUID responsibleMembershipId) {
    }

    public record ParticipantRecord(UUID membershipId, String position, UUID tableId) {
    }

    public record TableAssignmentRecord(
            UUID shiftId,
            UUID managerMembershipId,
            UUID waiterMembershipId,
            UUID runnerMembershipId) {
    }

    public record TaskRecord(
            UUID id,
            UUID shiftId,
            String description,
            UUID assignedToMembershipId,
            String status,
            String deadlineType,
            Instant deadlineAt,
            Instant completedAt,
            String cancellationReason,
            Instant createdAt,
            String carryOverState) {
    }

    public record TaskHistoryRecord(
            UUID id,
            String eventType,
            UUID actorMembershipId,
            String oldStatus,
            String newStatus,
            UUID oldAssigneeMembershipId,
            UUID newAssigneeMembershipId,
            String comment,
            Instant createdAt) {
    }

    public record ReportMetricsRecord(
            UUID managerMembershipId,
            int totalTasks,
            int completedTasks,
            int cancelledTasks,
            int carriedOverTasks) {
    }
}
