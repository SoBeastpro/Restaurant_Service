\set ON_ERROR_STOP on

-- Local development data only.
-- Demo users all use the password: password

\connect identity_db
SET ROLE identity_app;
BEGIN;

INSERT INTO users (
    id, login, email, password_hash, first_name, last_name, role_code, position_code,
    restaurant_id, status, email_verified_at
) VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'admin.demo',
        'admin.demo@example.test',
        '$2y$10$VSZq72MJUUpaWJ20pFsMa..islhoQ3bdjpcynYF8ppWIgnACi/T9G',
        'Системный', 'Администратор', 'SYSTEM_ADMIN', NULL, NULL, 'ACTIVE', CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'director.demo',
        'director.demo@example.test',
        '$2y$10$VSZq72MJUUpaWJ20pFsMa..islhoQ3bdjpcynYF8ppWIgnACi/T9G',
        'Анна', 'Директорова', 'DIRECTOR', NULL, '20000000-0000-0000-0000-000000000001', 'ACTIVE', CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'manager.demo',
        'manager.demo@example.test',
        '$2y$10$VSZq72MJUUpaWJ20pFsMa..islhoQ3bdjpcynYF8ppWIgnACi/T9G',
        'Михаил', 'Менеджеров', 'MANAGER', NULL, '20000000-0000-0000-0000-000000000001', 'ACTIVE', CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'employee.demo',
        'employee.demo@example.test',
        '$2y$10$VSZq72MJUUpaWJ20pFsMa..islhoQ3bdjpcynYF8ppWIgnACi/T9G',
        'Елена', 'Сотрудникова', 'EMPLOYEE', 'WAITER', '20000000-0000-0000-0000-000000000001', 'ACTIVE', CURRENT_TIMESTAMP
    )
ON CONFLICT (id) DO UPDATE SET
    login = EXCLUDED.login,
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    role_code = EXCLUDED.role_code,
    position_code = EXCLUDED.position_code,
    restaurant_id = EXCLUDED.restaurant_id,
    status = EXCLUDED.status,
    email_verified_at = EXCLUDED.email_verified_at;

INSERT INTO user_platform_roles (user_id, role_code, assigned_by_user_id)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'SYSTEM_ADMIN',
    '10000000-0000-0000-0000-000000000001'
)
ON CONFLICT (user_id, role_code) DO NOTHING;

INSERT INTO devices (id, user_id, device_uid, device_name, platform, app_version)
VALUES (
    '11000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000004',
    'demo-employee-device',
    'Demo iPhone',
    'IOS',
    '0.1.0'
)
ON CONFLICT (id) DO UPDATE SET
    device_name = EXCLUDED.device_name,
    platform = EXCLUDED.platform,
    app_version = EXCLUDED.app_version,
    last_active_at = CURRENT_TIMESTAMP;

COMMIT;
RESET ROLE;

\connect restaurant_db
SET ROLE restaurant_app;
BEGIN;

INSERT INTO restaurants (
    id, director_user_id, name, address, phone, description, status
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    'Демо-ресторан REST',
    'Москва, Тестовая улица, 1',
    '+79990000001',
    'Локальный ресторан для проверки backend',
    'ACTIVE'
)
ON CONFLICT (id) DO UPDATE SET
    director_user_id = EXCLUDED.director_user_id,
    name = EXCLUDED.name,
    address = EXCLUDED.address,
    phone = EXCLUDED.phone,
    description = EXCLUDED.description,
    status = EXCLUDED.status;

INSERT INTO halls (id, restaurant_id, name, display_order, status)
VALUES (
    '21000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Основной зал',
    1,
    'ACTIVE'
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    status = EXCLUDED.status;

INSERT INTO dining_tables (
    id, restaurant_id, hall_id, number, seats_count, display_order, status, status_comment
) VALUES
    (
        '22000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '1', 2, 1, 'READY', NULL
    ),
    (
        '22000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '2', 4, 2, 'NEEDS_CLEANING', NULL
    ),
    (
        '22000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '3', 6, 3, 'PROBLEM', 'Требуется проверить крепление стола'
    )
ON CONFLICT (id) DO UPDATE SET
    number = EXCLUDED.number,
    seats_count = EXCLUDED.seats_count,
    display_order = EXCLUDED.display_order,
    status = EXCLUDED.status,
    status_comment = EXCLUDED.status_comment;

INSERT INTO table_status_history (
    id, restaurant_id, table_id, changed_by_membership_id,
    old_status, new_status, comment
) VALUES
    (
        '23000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '22000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000003',
        'READY', 'NEEDS_CLEANING', NULL
    ),
    (
        '23000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '22000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000002',
        'READY', 'PROBLEM', 'Требуется проверить крепление стола'
    )
ON CONFLICT (id) DO NOTHING;

COMMIT;
RESET ROLE;

\connect workforce_db
SET ROLE workforce_app;
BEGIN;

INSERT INTO restaurant_members (
    id, restaurant_id, user_id, role_code, first_name, last_name, job_title, status, last_active_at
) VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000002',
        'DIRECTOR', 'Анна', 'Директорова', 'Директор', 'ACTIVE', CURRENT_TIMESTAMP
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000003',
        'MANAGER', 'Михаил', 'Менеджеров', 'Менеджер смены', 'ACTIVE', CURRENT_TIMESTAMP
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000004',
        'EMPLOYEE', 'Елена', 'Сотрудникова', 'Официант', 'ACTIVE', CURRENT_TIMESTAMP
    ),
    (
        '30000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000001',
        NULL,
        'EMPLOYEE', 'Иван', 'Приглашённый', 'Раннер', 'INVITED', NULL
    )
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role_code = EXCLUDED.role_code,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    job_title = EXCLUDED.job_title,
    status = EXCLUDED.status,
    last_active_at = EXCLUDED.last_active_at;

INSERT INTO member_permission_overrides (
    membership_id, permission_code, effect, changed_by_user_id, reason
) VALUES (
    '30000000-0000-0000-0000-000000000003',
    'TABLE_STATUS_SET_NEEDS_CLEANING',
    'GRANT',
    '10000000-0000-0000-0000-000000000002',
    'Демонстрация индивидуального разрешения'
)
ON CONFLICT (membership_id, permission_code) DO UPDATE SET
    effect = EXCLUDED.effect,
    changed_by_user_id = EXCLUDED.changed_by_user_id,
    reason = EXCLUDED.reason;

INSERT INTO member_permission_history (
    id, membership_id, permission_code, old_effect, new_effect, changed_by_user_id, reason
) VALUES (
    '31000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000003',
    'TABLE_STATUS_SET_NEEDS_CLEANING',
    NULL,
    'GRANT',
    '10000000-0000-0000-0000-000000000002',
    'Демонстрация индивидуального разрешения'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO invitations (
    id, restaurant_id, membership_id, created_by_membership_id,
    email, role_code, token_hash, status, expires_at
) VALUES (
    '32000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000004',
    '30000000-0000-0000-0000-000000000001',
    'invited.demo@example.test',
    'EMPLOYEE',
    'f54c60b3a58ce886b9325e1d6ff8fab191a3b6c0db14a43d263dd33ba112ad16',
    'PENDING',
    CURRENT_TIMESTAMP + INTERVAL '7 days'
)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    role_code = EXCLUDED.role_code,
    token_hash = EXCLUDED.token_hash,
    status = EXCLUDED.status,
    expires_at = EXCLUDED.expires_at;

COMMIT;
RESET ROLE;

\connect operations_db
SET ROLE operations_app;
BEGIN;

INSERT INTO shifts (
    id, restaurant_id, name, scheduled_start_at, scheduled_end_at,
    status, responsible_membership_id, created_by_membership_id
) VALUES (
    '40000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Дневная смена',
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP + INTERVAL '7 hours',
    'PLANNED',
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000001'
)
ON CONFLICT (id) DO NOTHING;

UPDATE shifts
SET status = 'ACTIVE',
    actual_started_at = COALESCE(actual_started_at, CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
    started_by_membership_id = COALESCE(
        started_by_membership_id,
        '30000000-0000-0000-0000-000000000002'
    )
WHERE id = '40000000-0000-0000-0000-000000000001'
  AND status = 'PLANNED';

INSERT INTO shift_participants (
    id, restaurant_id, shift_id, membership_id, added_by_membership_id,
    role_in_shift, attendance_status, checked_in_at
) VALUES
    (
        '41000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000002',
        'Директор', 'CHECKED_IN', CURRENT_TIMESTAMP - INTERVAL '25 minutes'
    ),
    (
        '41000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'Менеджер смены', 'CHECKED_IN', CURRENT_TIMESTAMP - INTERVAL '30 minutes'
    ),
    (
        '41000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000002',
        'Официант', 'SCHEDULED', NULL
    )
ON CONFLICT (shift_id, membership_id) DO UPDATE SET
    role_in_shift = EXCLUDED.role_in_shift,
    attendance_status = EXCLUDED.attendance_status,
    checked_in_at = EXCLUDED.checked_in_at;

INSERT INTO task_templates (
    id, restaurant_id, title, description, default_priority,
    created_by_membership_id, status
) VALUES
    (
        '42000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        'Подготовить стол',
        'Проверить чистоту и сервировку стола',
        'HIGH',
        '30000000-0000-0000-0000-000000000002',
        'ACTIVE'
    ),
    (
        '42000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        'Проверить зал',
        'Обойти зал перед началом обслуживания',
        'MEDIUM',
        '30000000-0000-0000-0000-000000000002',
        'ACTIVE'
    )
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    default_priority = EXCLUDED.default_priority,
    status = EXCLUDED.status;

INSERT INTO tasks (
    id, restaurant_id, shift_id, template_id, created_by_membership_id,
    assigned_to_membership_id, title, description, status, priority, deadline_type, due_at
) VALUES
    (
        '43000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '42000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000003',
        'Убрать стол №2',
        'Подготовить стол к следующему гостю',
        'CREATED', 'HIGH', 'TIME', CURRENT_TIMESTAMP + INTERVAL '30 minutes'
    ),
    (
        '43000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '42000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000002',
        'Проверить основной зал',
        'Проверить готовность столов и проходов',
        'CREATED', 'MEDIUM', 'TIME', CURRENT_TIMESTAMP + INTERVAL '1 hour'
    ),
    (
        '43000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        NULL,
        '30000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000003',
        'Проверить меню на столах',
        NULL,
        'CREATED', 'LOW', 'TIME', CURRENT_TIMESTAMP + INTERVAL '2 hours'
    )
ON CONFLICT (id) DO NOTHING;

UPDATE tasks
SET status = 'IN_PROGRESS', accepted_at = COALESCE(accepted_at, CURRENT_TIMESTAMP)
WHERE id = '43000000-0000-0000-0000-000000000002'
  AND status = 'CREATED';

UPDATE tasks
SET status = 'COMPLETED', completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP)
WHERE id = '43000000-0000-0000-0000-000000000003'
  AND status = 'CREATED';

INSERT INTO task_history (
    id, restaurant_id, task_id, actor_membership_id,
    event_type, old_status, new_status, comment
) VALUES
    (
        '44000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '43000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000002',
        'CREATED', NULL, 'CREATED', 'Демонстрационная задача'
    ),
    (
        '44000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '43000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000002',
        'STATUS_CHANGED', 'CREATED', 'IN_PROGRESS', NULL
    ),
    (
        '44000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001',
        '43000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000003',
        'STATUS_CHANGED', 'CREATED', 'COMPLETED', NULL
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO favorite_task_templates (membership_id, restaurant_id, template_id)
VALUES (
    '30000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000001',
    '42000000-0000-0000-0000-000000000001'
)
ON CONFLICT (membership_id, template_id) DO NOTHING;

COMMIT;
RESET ROLE;

\connect billing_db
SET ROLE billing_app;
BEGIN;

INSERT INTO subscriptions (
    id, restaurant_id, plan_id, activated_by_membership_id,
    status, activation_mode, starts_at, ends_at
)
SELECT
    '50000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000100',
    '30000000-0000-0000-0000-000000000001',
    'ACTIVE',
    'STUB',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE NOT EXISTS (
    SELECT 1
    FROM subscriptions
    WHERE restaurant_id = '20000000-0000-0000-0000-000000000001'
      AND status = 'ACTIVE'
);

INSERT INTO subscription_events (
    id, subscription_id, actor_user_id, event_type, details
)
SELECT
    '51000000-0000-0000-0000-000000000001',
    id,
    '10000000-0000-0000-0000-000000000002',
    'ACTIVATED',
    '{"source": "demo-seed"}'::JSONB
FROM subscriptions
WHERE restaurant_id = '20000000-0000-0000-0000-000000000001'
  AND status = 'ACTIVE'
ON CONFLICT (id) DO NOTHING;

COMMIT;
RESET ROLE;

\connect notification_db
SET ROLE notification_app;
BEGIN;

INSERT INTO notifications (
    id, user_id, restaurant_id, type, title, message, payload, read_at
) VALUES
    (
        '60000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000001',
        'TASK_ASSIGNED',
        'Новая задача',
        'Вам назначена задача «Убрать стол №2»',
        '{"taskId": "43000000-0000-0000-0000-000000000001"}'::JSONB,
        NULL
    ),
    (
        '60000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        'SUBSCRIPTION_ACTIVATED',
        'Подписка активирована',
        'Условная подписка активирована на 30 дней',
        '{"subscriptionId": "50000000-0000-0000-0000-000000000001"}'::JSONB,
        CURRENT_TIMESTAMP
    )
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    message = EXCLUDED.message,
    payload = EXCLUDED.payload,
    read_at = EXCLUDED.read_at;

INSERT INTO push_registrations (
    id, user_id, device_id, push_token, provider, status
) VALUES (
    '61000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000004',
    '11000000-0000-0000-0000-000000000004',
    'demo-push-token',
    'APNS',
    'ACTIVE'
)
ON CONFLICT (id) DO UPDATE SET
    push_token = EXCLUDED.push_token,
    provider = EXCLUDED.provider,
    status = EXCLUDED.status;

COMMIT;
RESET ROLE;

\connect audit_db
SET ROLE audit_app;
BEGIN;

INSERT INTO activity_logs (
    id, user_id, membership_id, restaurant_id, action, entity_type, entity_id, details
) VALUES (
    '70000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '30000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    'SHIFT_STARTED',
    'SHIFT',
    '40000000-0000-0000-0000-000000000001',
    '{"source": "demo-seed"}'::JSONB
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_logs (
    id, event_id, actor_user_id, actor_membership_id, restaurant_id,
    scope, action, entity_type, entity_id, outcome, details
) VALUES (
    '71000000-0000-0000-0000-000000000001',
    '71000000-0000-0000-0000-000000000101',
    '10000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'RESTAURANT',
    'SUBSCRIPTION_ACTIVATED',
    'SUBSCRIPTION',
    '50000000-0000-0000-0000-000000000001',
    'SUCCESS',
    '{"source": "demo-seed"}'::JSONB
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO system_events (
    id, level, service, event_type, message, details
) VALUES (
    '72000000-0000-0000-0000-000000000001',
    'INFO',
    'database-seed',
    'DEMO_DATA_LOADED',
    'Демонстрационные данные загружены',
    '{"restaurantId": "20000000-0000-0000-0000-000000000001"}'::JSONB
)
ON CONFLICT (id) DO NOTHING;

COMMIT;
RESET ROLE;

\connect reporting_db
SET ROLE reporting_app;
BEGIN;

INSERT INTO restaurant_daily_metrics (
    restaurant_id, metric_date, shifts_opened, tasks_created,
    tasks_completed, table_status_changes, active_members
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    CURRENT_DATE,
    1, 3, 1, 2, 3
)
ON CONFLICT (restaurant_id, metric_date) DO UPDATE SET
    shifts_opened = EXCLUDED.shifts_opened,
    tasks_created = EXCLUDED.tasks_created,
    tasks_completed = EXCLUDED.tasks_completed,
    table_status_changes = EXCLUDED.table_status_changes,
    active_members = EXCLUDED.active_members,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO employee_daily_metrics (
    restaurant_id, membership_id, metric_date,
    shifts_count, worked_minutes, tasks_assigned, tasks_completed
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000003',
    CURRENT_DATE,
    1, 30, 2, 1
)
ON CONFLICT (restaurant_id, membership_id, metric_date) DO UPDATE SET
    shifts_count = EXCLUDED.shifts_count,
    worked_minutes = EXCLUDED.worked_minutes,
    tasks_assigned = EXCLUDED.tasks_assigned,
    tasks_completed = EXCLUDED.tasks_completed,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO table_daily_metrics (
    restaurant_id, table_id, metric_date,
    needs_cleaning_count, problem_count, ready_count
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '22000000-0000-0000-0000-000000000002',
    CURRENT_DATE,
    1, 0, 1
)
ON CONFLICT (restaurant_id, table_id, metric_date) DO UPDATE SET
    needs_cleaning_count = EXCLUDED.needs_cleaning_count,
    problem_count = EXCLUDED.problem_count,
    ready_count = EXCLUDED.ready_count,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
RESET ROLE;

