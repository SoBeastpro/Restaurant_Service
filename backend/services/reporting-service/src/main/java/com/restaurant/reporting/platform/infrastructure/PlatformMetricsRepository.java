package com.restaurant.reporting.platform.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import com.restaurant.reporting.platform.api.PlatformMetricsDtos.DailyMetricsResponse;
import com.restaurant.reporting.platform.api.PlatformMetricsDtos.PlatformMetricsResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformMetricsRepository {

    private final JdbcClient jdbc;

    public PlatformMetricsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PlatformMetricsResponse metrics(LocalDate from, LocalDate to) {
        Totals totals = jdbc.sql("""
                        SELECT COUNT(DISTINCT restaurant_id) AS restaurants,
                               COALESCE(SUM(shifts_opened), 0) AS shifts_opened,
                               COALESCE(SUM(shifts_closed), 0) AS shifts_closed,
                               COALESCE(SUM(tasks_created), 0) AS tasks_created,
                               COALESCE(SUM(tasks_completed), 0) AS tasks_completed,
                               COALESCE(SUM(tasks_cancelled), 0) AS tasks_cancelled,
                               COALESCE(SUM(tasks_overdue), 0) AS tasks_overdue,
                               COALESCE(SUM(table_status_changes), 0) AS table_status_changes
                        FROM restaurant_daily_metrics
                        WHERE metric_date BETWEEN :from AND :to
                        """)
                .param("from", from)
                .param("to", to)
                .query(PlatformMetricsRepository::mapTotals)
                .single();
        long activeMembers = jdbc.sql("""
                        SELECT COALESCE(SUM(active_members), 0)
                        FROM restaurant_daily_metrics metrics
                        WHERE metric_date = (
                            SELECT MAX(latest.metric_date)
                            FROM restaurant_daily_metrics latest
                            WHERE latest.restaurant_id = metrics.restaurant_id
                              AND latest.metric_date BETWEEN :from AND :to
                        )
                          AND metric_date BETWEEN :from AND :to
                        """)
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .single();
        List<DailyMetricsResponse> daily = jdbc.sql("""
                        SELECT metric_date,
                               COUNT(DISTINCT restaurant_id) AS restaurants,
                               SUM(shifts_opened) AS shifts_opened,
                               SUM(shifts_closed) AS shifts_closed,
                               SUM(tasks_created) AS tasks_created,
                               SUM(tasks_completed) AS tasks_completed,
                               SUM(tasks_cancelled) AS tasks_cancelled,
                               SUM(tasks_overdue) AS tasks_overdue,
                               SUM(table_status_changes) AS table_status_changes
                        FROM restaurant_daily_metrics
                        WHERE metric_date BETWEEN :from AND :to
                        GROUP BY metric_date
                        ORDER BY metric_date
                        """)
                .param("from", from)
                .param("to", to)
                .query(PlatformMetricsRepository::mapDaily)
                .list();
        BigDecimal completionRate = totals.tasksCreated() == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(totals.tasksCompleted() * 100L)
                        .divide(BigDecimal.valueOf(totals.tasksCreated()), 2, RoundingMode.HALF_UP);
        return new PlatformMetricsResponse(
                from,
                to,
                totals.restaurants(),
                activeMembers,
                totals.shiftsOpened(),
                totals.shiftsClosed(),
                totals.tasksCreated(),
                totals.tasksCompleted(),
                totals.tasksCancelled(),
                totals.tasksOverdue(),
                totals.tableStatusChanges(),
                completionRate,
                daily);
    }

    private static Totals mapTotals(ResultSet result, int rowNumber) throws SQLException {
        return new Totals(
                result.getLong("restaurants"),
                result.getLong("shifts_opened"),
                result.getLong("shifts_closed"),
                result.getLong("tasks_created"),
                result.getLong("tasks_completed"),
                result.getLong("tasks_cancelled"),
                result.getLong("tasks_overdue"),
                result.getLong("table_status_changes"));
    }

    private static DailyMetricsResponse mapDaily(ResultSet result, int rowNumber) throws SQLException {
        return new DailyMetricsResponse(
                result.getObject("metric_date", LocalDate.class),
                result.getLong("restaurants"),
                result.getLong("shifts_opened"),
                result.getLong("shifts_closed"),
                result.getLong("tasks_created"),
                result.getLong("tasks_completed"),
                result.getLong("tasks_cancelled"),
                result.getLong("tasks_overdue"),
                result.getLong("table_status_changes"));
    }

    private record Totals(
            long restaurants,
            long shiftsOpened,
            long shiftsClosed,
            long tasksCreated,
            long tasksCompleted,
            long tasksCancelled,
            long tasksOverdue,
            long tableStatusChanges) {
    }
}
