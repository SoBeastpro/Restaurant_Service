package com.restaurant.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class DatabaseSchemaContractTests {

    private static final Map<String, DatabaseExpectation> DATABASES = databases();
    private static final Path INIT_DIRECTORY = findInitDirectory();
    private static final Path MIGRATION_DIRECTORY = findMigrationDirectory();
    private static final Path DEMO_SEED_FILE = findDemoSeedFile();

    @Container
    static final GenericContainer<?> POSTGRES = postgresContainer();

    @BeforeAll
    static void applyServiceOwnedMigrations() throws IOException, InterruptedException {
        for (DatabaseExpectation database : DATABASES.values()) {
            String containerPath = "/opt/restaurant/migrations/" + database.name() + ".sql";
            ExecResult result = POSTGRES.execInContainer(
                    "env", "PGPASSWORD=" + database.password(),
                    "psql", "-h", "127.0.0.1",
                    "-v", "ON_ERROR_STOP=1",
                    "-U", database.user(),
                    "-d", database.name(),
                    "-f", containerPath);
            assertEquals(0, result.getExitCode(), result.getStderr());
        }
    }

    @Test
    void createsEveryServiceSchemaWithTheExpectedOwner() throws SQLException {
        for (DatabaseExpectation database : DATABASES.values()) {
            try (Connection connection = connect(database);
                    Statement statement = connection.createStatement()) {
                Set<String> actualTables = new TreeSet<>();
                try (ResultSet result = statement.executeQuery("""
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public'
                        ORDER BY tablename
                        """)) {
                    while (result.next()) {
                        actualTables.add(result.getString(1));
                    }
                }

                assertEquals(database.tables(), actualTables, database.name());

                try (ResultSet result = statement.executeQuery("""
                        SELECT DISTINCT tableowner
                        FROM pg_tables
                        WHERE schemaname = 'public'
                        """)) {
                    assertTrue(result.next(), database.name());
                    assertEquals(database.user(), result.getString(1), database.name());
                    assertTrue(!result.next(), "All tables must have one service owner in " + database.name());
                }
            }
        }
    }

    @Test
    void createsCriticalIndexes() throws SQLException {
        for (DatabaseExpectation database : DATABASES.values()) {
            try (Connection connection = connect(database);
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                            SELECT indexname
                            FROM pg_indexes
                            WHERE schemaname = 'public'
                            """)) {
                Set<String> actualIndexes = new TreeSet<>();
                while (result.next()) {
                    actualIndexes.add(result.getString(1));
                }
                assertTrue(
                        actualIndexes.containsAll(database.criticalIndexes()),
                        () -> database.name() + " misses indexes "
                                + difference(database.criticalIndexes(), actualIndexes));
            }
        }
    }

    @Test
    void loadsRequiredRolesPermissionsAndTariff() throws SQLException {
        assertEquals(1, scalar("identity_db", "identity_app", "identity",
                "SELECT count(*) FROM platform_roles WHERE code = 'SYSTEM_ADMIN'"));
        assertEquals(6, scalar("identity_db", "identity_app", "identity",
                "SELECT count(*) FROM platform_permissions"));
        assertEquals(3, scalar("workforce_db", "workforce_app", "workforce",
                "SELECT count(*) FROM restaurant_roles"));
        assertEquals(37, scalar("workforce_db", "workforce_app", "workforce",
                "SELECT count(*) FROM permissions"));
        assertEquals(1, scalar("billing_db", "billing_app", "billing", """
                SELECT count(*)
                FROM tariff_plans
                WHERE code = 'MONTHLY_PLACEHOLDER' AND duration_days = 30
                """));
    }

    @Test
    void rejectsTwoDirectorsForOneRestaurant() throws SQLException {
        DatabaseExpectation database = DATABASES.get("workforce_db");
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    INSERT INTO restaurant_members
                        (id, restaurant_id, role_code, first_name, last_name, status)
                    VALUES
                        ('90000000-0000-0000-0000-000000000001',
                         '90000000-0000-0000-0000-000000000010',
                         'DIRECTOR', 'Первый', 'Директор', 'ACTIVE')
                    """);

            SQLException error = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO restaurant_members
                        (id, restaurant_id, role_code, first_name, last_name, status)
                    VALUES
                        ('90000000-0000-0000-0000-000000000002',
                         '90000000-0000-0000-0000-000000000010',
                         'DIRECTOR', 'Второй', 'Директор', 'ACTIVE')
                    """));

            assertEquals("23505", error.getSQLState());
            connection.rollback();
        }
    }

    @Test
    void rejectsTwoActiveSubscriptionsForOneRestaurant() throws SQLException {
        DatabaseExpectation database = DATABASES.get("billing_db");
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate(activeSubscriptionSql("90000000-0000-0000-0000-000000000001"));

            SQLException error = assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate(activeSubscriptionSql(
                            "90000000-0000-0000-0000-000000000002")));

            assertEquals("23505", error.getSQLState());
            connection.rollback();
        }
    }

    @Test
    void serviceCannotConnectToAnotherServiceDatabase() {
        SQLException error = assertThrows(
                SQLException.class,
                () -> connect("workforce_db", "identity_app", "identity"));
        assertEquals("42501", error.getSQLState());
    }

    @Test
    void loadsDemoDataTwiceWithoutCreatingDuplicates() throws IOException, InterruptedException, SQLException {
        ExecResult firstRun = runDemoSeed();
        assertEquals(0, firstRun.getExitCode(), firstRun.getStderr());

        ExecResult secondRun = runDemoSeed();
        assertEquals(0, secondRun.getExitCode(), secondRun.getStderr());

        assertEquals(4, scalar("identity_db", "identity_app", "identity", "SELECT count(*) FROM users"));
        assertEquals(1, scalar("restaurant_db", "restaurant_app", "restaurant", "SELECT count(*) FROM restaurants"));
        assertEquals(3, scalar("restaurant_db", "restaurant_app", "restaurant", "SELECT count(*) FROM dining_tables"));
        assertEquals(4, scalar("workforce_db", "workforce_app", "workforce", "SELECT count(*) FROM restaurant_members"));
        assertEquals(1, scalar("workforce_db", "workforce_app", "workforce", "SELECT count(*) FROM invitations"));
        assertEquals(1, scalar("operations_db", "operations_app", "operations", "SELECT count(*) FROM shifts"));
        assertEquals(3, scalar("operations_db", "operations_app", "operations", "SELECT count(*) FROM tasks"));
        assertEquals(1, scalar("billing_db", "billing_app", "billing", "SELECT count(*) FROM subscriptions"));
        assertEquals(2, scalar("notification_db", "notification_app", "notification", "SELECT count(*) FROM notifications"));
        assertEquals(1, scalar("audit_db", "audit_app", "audit", "SELECT count(*) FROM audit_logs"));
        assertEquals(1, scalar("reporting_db", "reporting_app", "reporting", "SELECT count(*) FROM restaurant_daily_metrics"));
    }

    private static GenericContainer<?> postgresContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withEnv("POSTGRES_DB", "postgres")
                .withEnv("POSTGRES_USER", "platform_admin")
                .withEnv("POSTGRES_PASSWORD", "platform_admin")
                .withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                .withStartupTimeout(Duration.ofMinutes(2));

        Path databaseBootstrap = INIT_DIRECTORY.resolve("01-create-service-databases.sql");
        container.withCopyFileToContainer(
                MountableFile.forHostPath(databaseBootstrap),
                "/docker-entrypoint-initdb.d/01-create-service-databases.sql");
        for (DatabaseExpectation database : DATABASES.values()) {
            Path source = MIGRATION_DIRECTORY.resolve(database.migrationFile());
            if (!Files.isRegularFile(source)) {
                throw new IllegalStateException("Service migration not found: " + source);
            }
            container.withCopyFileToContainer(
                    MountableFile.forHostPath(source),
                    "/opt/restaurant/migrations/" + database.name() + ".sql");
        }
        container.withCopyFileToContainer(
                MountableFile.forHostPath(DEMO_SEED_FILE),
                "/opt/restaurant/dev-seed/demo-data.sql");
        return container;
    }

    private static Path findInitDirectory() {
        String configured = System.getProperty("database.init.directory");
        if (configured != null && !configured.isBlank()) {
            Path candidate = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("infrastructure/postgres/init");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate infrastructure/postgres/init");
    }

    private static Path findDemoSeedFile() {
        String configured = System.getProperty("database.demo.seed.file");
        if (configured != null && !configured.isBlank()) {
            Path candidate = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("infrastructure/postgres/dev-seed/demo-data.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate infrastructure/postgres/dev-seed/demo-data.sql");
    }

    private static Path findMigrationDirectory() {
        String configured = System.getProperty("database.migration.directory");
        if (configured != null && !configured.isBlank()) {
            Path candidate = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("services");
            if (Files.isDirectory(candidate.resolve("identity-service"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate service migration directory");
    }

    private static ExecResult runDemoSeed() throws IOException, InterruptedException {
        return POSTGRES.execInContainer(
                "psql",
                "-v", "ON_ERROR_STOP=1",
                "-U", "platform_admin",
                "-d", "postgres",
                "-f", "/opt/restaurant/dev-seed/demo-data.sql");
    }

    private static Connection connect(DatabaseExpectation database) throws SQLException {
        return connect(database.name(), database.user(), database.password());
    }

    private static Connection connect(String database, String user, String password) throws SQLException {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ':'
                + POSTGRES.getMappedPort(5432) + '/' + database;
        return DriverManager.getConnection(url, user, password);
    }

    private static long scalar(String database, String user, String password, String sql) throws SQLException {
        try (Connection connection = connect(database, user, password);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static String activeSubscriptionSql(String id) {
        return """
                INSERT INTO subscriptions
                    (id, restaurant_id, plan_id, activated_by_membership_id,
                     status, starts_at, ends_at)
                VALUES
                    ('%s',
                     '90000000-0000-0000-0000-000000000010',
                     '00000000-0000-0000-0000-000000000100',
                     '90000000-0000-0000-0000-000000000020',
                     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 days')
                """.formatted(id);
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static Map<String, DatabaseExpectation> databases() {
        Map<String, DatabaseExpectation> databases = new LinkedHashMap<>();
        databases.put("identity_db", new DatabaseExpectation(
                "identity_db",
                "identity_app",
                "identity",
                "identity-service/src/main/resources/db/migration/V1__identity.sql",
                Set.of(
                        "contact_verification_tokens", "devices", "inbox_messages", "outbox_events",
                        "password_reset_tokens", "platform_permissions", "platform_role_permissions",
                        "platform_roles", "sessions", "user_platform_roles", "users"),
                Set.of(
                        "users_login_lower_key", "users_email_lower_key", "sessions_user_active_idx",
                        "sessions_active_expiration_idx", "outbox_unpublished_idx")));
        databases.put("restaurant_db", new DatabaseExpectation(
                "restaurant_db",
                "restaurant_app",
                "restaurant",
                "restaurant-service/src/main/resources/db/migration/V1__restaurant.sql",
                Set.of("dining_tables", "halls", "inbox_messages", "outbox_events", "restaurants",
                        "table_status_history"),
                Set.of(
                        "halls_restaurant_order_idx", "dining_tables_restaurant_status_idx",
                        "dining_tables_hall_order_idx", "table_status_history_table_created_idx",
                        "outbox_unpublished_idx")));
        databases.put("workforce_db", new DatabaseExpectation(
                "workforce_db",
                "workforce_app",
                "workforce",
                "workforce-service/src/main/resources/db/migration/V1__workforce.sql",
                Set.of(
                        "inbox_messages", "invitations", "member_permission_history",
                        "member_permission_overrides", "outbox_events", "permissions",
                        "restaurant_members", "restaurant_role_permissions", "restaurant_roles"),
                Set.of(
                        "restaurant_members_user_restaurant_key", "restaurant_members_one_director_key",
                        "restaurant_members_restaurant_status_idx",
                        "invitations_restaurant_status_created_idx", "outbox_unpublished_idx")));
        databases.put("operations_db", new DatabaseExpectation(
                "operations_db",
                "operations_app",
                "operations",
                "operations-service/src/main/resources/db/migration/V1__operations.sql",
                Set.of(
                        "favorite_task_templates", "inbox_messages", "outbox_events", "shift_participants",
                        "shift_reports", "shift_task_resolutions", "shifts", "task_history",
                        "task_templates", "tasks"),
                Set.of(
                        "shifts_one_active_per_restaurant_key", "shifts_restaurant_schedule_idx",
                        "shift_participants_member_idx", "tasks_restaurant_status_created_idx",
                        "tasks_assignee_status_due_idx", "tasks_open_due_idx",
                        "task_history_task_created_idx", "outbox_unpublished_idx")));
        databases.put("billing_db", new DatabaseExpectation(
                "billing_db",
                "billing_app",
                "billing",
                "billing-service/src/main/resources/db/migration/V1__billing.sql",
                Set.of("inbox_messages", "outbox_events", "subscription_events", "subscriptions", "tariff_plans"),
                Set.of(
                        "subscriptions_one_active_per_restaurant_key", "subscriptions_active_ends_idx",
                        "subscription_events_subscription_created_idx", "outbox_unpublished_idx")));
        databases.put("notification_db", new DatabaseExpectation(
                "notification_db",
                "notification_app",
                "notification",
                "notification-service/src/main/resources/db/migration/V1__notification.sql",
                Set.of("delivery_attempts", "inbox_messages", "notifications", "outbox_events",
                        "push_registrations"),
                Set.of(
                        "notifications_user_created_idx", "notifications_user_unread_idx",
                        "push_registrations_user_active_idx", "delivery_attempts_pending_idx",
                        "outbox_unpublished_idx")));
        databases.put("audit_db", new DatabaseExpectation(
                "audit_db",
                "audit_app",
                "audit",
                "audit-service/src/main/resources/db/migration/V1__audit.sql",
                Set.of("activity_logs", "audit_logs", "inbox_messages", "system_events"),
                Set.of(
                        "activity_logs_user_created_idx", "audit_logs_restaurant_created_idx",
                        "audit_logs_entity_created_idx", "system_events_service_created_idx",
                        "inbox_unprocessed_idx")));
        databases.put("reporting_db", new DatabaseExpectation(
                "reporting_db",
                "reporting_app",
                "reporting",
                "reporting-service/src/main/resources/db/migration/V1__reporting.sql",
                Set.of("employee_daily_metrics", "inbox_messages", "restaurant_daily_metrics",
                        "table_daily_metrics"),
                Set.of(
                        "restaurant_daily_metrics_pkey", "employee_daily_metrics_pkey",
                        "table_daily_metrics_pkey", "inbox_unprocessed_idx")));
        return Map.copyOf(databases);
    }

    private record DatabaseExpectation(
            String name,
            String user,
            String password,
            String migrationFile,
            Set<String> tables,
            Set<String> criticalIndexes) {
    }
}
