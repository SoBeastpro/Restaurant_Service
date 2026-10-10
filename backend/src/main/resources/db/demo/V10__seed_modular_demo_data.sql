-- Local-only demo seed. All four demo accounts use password: password

INSERT INTO identity.users (
    id, login, email, password_hash, first_name, last_name, status, email_verified_at
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'demo.director',
        'director@example.com',
        '$2y$12$VzCm9dBZZ1NHzvMcbKMXSOWVU54dM8xU15unjC6cIKclx1IyKkN1y',
        'Anna',
        'Director',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'demo.manager',
        'manager@example.com',
        '$2y$12$VzCm9dBZZ1NHzvMcbKMXSOWVU54dM8xU15unjC6cIKclx1IyKkN1y',
        'Maria',
        'Manager',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'demo.employee',
        'employee@example.com',
        '$2y$12$VzCm9dBZZ1NHzvMcbKMXSOWVU54dM8xU15unjC6cIKclx1IyKkN1y',
        'Ivan',
        'Employee',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'demo.sysadmin',
        'sysadmin@example.com',
        '$2y$12$VzCm9dBZZ1NHzvMcbKMXSOWVU54dM8xU15unjC6cIKclx1IyKkN1y',
        'Alex',
        'Administrator',
        'ACTIVE',
        CURRENT_TIMESTAMP
    );

INSERT INTO identity.user_platform_roles (user_id, role_code, assigned_by_user_id)
VALUES (
    '10000000-0000-0000-0000-000000000004',
    'SYSTEM_ADMIN',
    '10000000-0000-0000-0000-000000000004'
);

INSERT INTO restaurant.restaurants (
    id, name, address, phone, description, status, director_membership_id
)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    'Demo Restaurant',
    'Moscow, Tverskaya Street, 1',
    '+74950000000',
    'Restaurant used for local development',
    'ACTIVE',
    '30000000-0000-0000-0000-000000000001'
);

INSERT INTO workforce.restaurant_members (
    id, restaurant_id, user_id, role_code, first_name, last_name, job_title, status, last_active_at
)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'DIRECTOR',
        'Anna',
        'Director',
        'Director',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000002',
        'MANAGER',
        'Maria',
        'Manager',
        'Shift manager',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000003',
        'EMPLOYEE',
        'Ivan',
        'Employee',
        'Waiter',
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        '30000000-0000-0000-0000-000000000004',
        '20000000-0000-0000-0000-000000000001',
        NULL,
        'EMPLOYEE',
        'Elena',
        'Invited',
        'Runner',
        'INVITED',
        NULL
    );

INSERT INTO workforce.member_permission_overrides (
    membership_id, permission_code, effect, changed_by_user_id, reason
)
VALUES
    (
        '30000000-0000-0000-0000-000000000003',
        'TABLE_STATUS_SET_READY',
        'GRANT',
        '10000000-0000-0000-0000-000000000002',
        'Demo employee may mark a table ready'
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        'TABLE_STATUS_SET_NEEDS_SERVING',
        'GRANT',
        '10000000-0000-0000-0000-000000000002',
        'Demo employee may request service'
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        'TABLE_STATUS_SET_PROBLEM',
        'GRANT',
        '10000000-0000-0000-0000-000000000002',
        'Demo employee may report a problem'
    );

INSERT INTO workforce.member_permission_history (
    membership_id, permission_code, old_effect, new_effect, changed_by_user_id, reason
)
SELECT
    membership_id,
    permission_code,
    NULL,
    effect,
    changed_by_user_id,
    reason
FROM workforce.member_permission_overrides
WHERE membership_id = '30000000-0000-0000-0000-000000000003';

INSERT INTO workforce.invitations (
    id, restaurant_id, membership_id, created_by_membership_id,
    email, role_code, token_hash, status, expires_at
)
VALUES (
    '31000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000004',
    '30000000-0000-0000-0000-000000000002',
    'invited@example.com',
    'EMPLOYEE',
    REPEAT('a', 64),
    'PENDING',
    CURRENT_TIMESTAMP + INTERVAL '7 days'
);

INSERT INTO restaurant.halls (
    id, restaurant_id, name, display_order, layout_settings
)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Main hall',
    0,
    '{"columns": 4}'::JSONB
);

INSERT INTO restaurant.dining_tables (
    id, restaurant_id, hall_id, number, seats_count, display_order, status, status_comment
)
VALUES
    (
        '41000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '1',
        2,
        0,
        'READY',
        NULL
    ),
    (
        '41000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        '2',
        4,
        1,
        'NEEDS_SERVING',
        'Guests are waiting for service'
    );

INSERT INTO restaurant.table_status_history (
    id, restaurant_id, table_id, changed_by_membership_id,
    old_status, new_status, comment
)
VALUES (
    '42000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '41000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000003',
    'READY',
    'NEEDS_SERVING',
    'Guests are waiting for service'
);

INSERT INTO operations.shifts (
    id, restaurant_id, name, scheduled_start_at, scheduled_end_at,
    status, responsible_membership_id, created_by_membership_id
)
VALUES (
    '50000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Day shift',
    DATE_TRUNC('day', CURRENT_TIMESTAMP) + INTERVAL '9 hours',
    DATE_TRUNC('day', CURRENT_TIMESTAMP) + INTERVAL '18 hours',
    'PLANNED',
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000001'
);

INSERT INTO operations.shift_participants (
    id, restaurant_id, shift_id, membership_id, added_by_membership_id,
    role_in_shift, attendance_status
)
VALUES
    (
        '51000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000001',
        'Responsible manager',
        'SCHEDULED'
    ),
    (
        '51000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000001',
        '50000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000002',
        'Waiter',
        'SCHEDULED'
    );

INSERT INTO operations.task_templates (
    id, restaurant_id, title, description, default_priority, created_by_membership_id
)
VALUES (
    '52000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Opening checklist',
    'Standard hall preparation before opening',
    'HIGH',
    '30000000-0000-0000-0000-000000000002'
);

INSERT INTO operations.tasks (
    id, restaurant_id, shift_id, template_id, created_by_membership_id,
    assigned_to_membership_id, title, description, status, priority, due_at
)
VALUES (
    '53000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000001',
    '52000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000003',
    'Prepare the main hall',
    'Check table settings before opening',
    'CREATED',
    'HIGH',
    CURRENT_TIMESTAMP + INTERVAL '1 day'
);

INSERT INTO operations.task_history (
    id, restaurant_id, task_id, actor_membership_id,
    event_type, new_status, new_assignee_membership_id, comment
)
VALUES (
    '54000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '53000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002',
    'CREATED',
    'CREATED',
    '30000000-0000-0000-0000-000000000003',
    'Demo task created and assigned'
);

INSERT INTO operations.favorite_task_templates (
    membership_id, restaurant_id, template_id
)
VALUES (
    '30000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    '52000000-0000-0000-0000-000000000001'
);

INSERT INTO billing.subscriptions (
    id, restaurant_id, plan_id, activated_by_membership_id,
    status, activation_mode, starts_at, ends_at
)
VALUES (
    '60000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000100',
    '30000000-0000-0000-0000-000000000001',
    'ACTIVE',
    'STUB',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '30 days'
);

INSERT INTO billing.subscription_events (
    id, subscription_id, actor_user_id, event_type, details
)
VALUES (
    '61000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'ACTIVATED',
    '{"source": "demo_button"}'::JSONB
);

INSERT INTO identity.devices (
    id, user_id, device_uid, device_name, platform, app_version
)
VALUES (
    '70000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    'demo-android-device',
    'Demo Android phone',
    'ANDROID',
    '0.1.0'
);

INSERT INTO notification.push_registrations (
    id, user_id, device_id, push_token, provider
)
VALUES (
    '71000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '70000000-0000-0000-0000-000000000001',
    'demo-push-token',
    'FCM'
);

INSERT INTO notification.notifications (
    id, user_id, restaurant_id, type, title, message, payload
)
VALUES (
    '72000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000001',
    'TASK_ASSIGNED',
    'New task',
    'You have been assigned: Prepare the main hall',
    '{"taskId": "53000000-0000-0000-0000-000000000001"}'::JSONB
);

INSERT INTO audit.audit_logs (
    id, actor_user_id, actor_membership_id, restaurant_id,
    scope, action, entity_type, entity_id, outcome, details
)
VALUES (
    '80000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    'RESTAURANT',
    'TASK_CREATED',
    'TASK',
    '53000000-0000-0000-0000-000000000001',
    'SUCCESS',
    '{}'::JSONB
);
