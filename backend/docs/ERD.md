# ERD модульной базы данных REST

Диаграмма построена по фактическим Flyway-миграциям `V1`–`V9`. Она описывает 37 таблиц,
одно вычисляемое представление и связи между семью PostgreSQL-схемами.

## PNG-версии

- [Полная ERD со всеми модулями](erd/png/restaurant-database-full-erd.png)
- [Карта модулей](erd/png/00-modules-overview.png)
- [Identity и RBAC](erd/png/01-identity-rbac.png)
- [Restaurant и Workforce](erd/png/02-restaurant-workforce.png)
- [Operations](erd/png/03-operations.png)
- [Billing](erd/png/04-billing.png)
- [Notification](erd/png/05-notification.png)
- [Audit](erd/png/06-audit.png)

## Карта модулей

```mermaid
flowchart LR
    identity["identity<br/>11 таблиц<br/>Пользователи, роли, RBAC, сессии"]
    restaurant["restaurant<br/>4 таблицы<br/>Рестораны, залы, столы"]
    workforce["workforce<br/>4 таблицы + view<br/>Сотрудники, приглашения, персональные права"]
    operations["operations<br/>8 таблиц<br/>Смены и задачи"]
    billing["billing<br/>3 таблицы<br/>Тариф и условная подписка"]
    notification["notification<br/>4 таблицы<br/>Push-уведомления и outbox"]
    audit["audit<br/>3 таблицы<br/>Активность, аудит, системные события"]

    identity --> workforce
    identity --> billing
    identity --> notification
    identity --> audit
    restaurant <--> workforce
    restaurant --> operations
    restaurant --> billing
    restaurant --> notification
    restaurant --> audit
    workforce --> operations
    workforce --> billing
    workforce --> audit
```

Циклическая связь `restaurant ↔ workforce` намеренная: ресторан содержит ссылку на своего
единственного директора, а запись директора принадлежит этому же ресторану. Оба внешних ключа
отложенные (`DEFERRABLE INITIALLY DEFERRED`), поэтому пара создаётся в одной транзакции.

## Identity и RBAC

```mermaid
erDiagram
    identity_users {
        uuid id PK
        varchar login UK
        varchar email UK
        varchar phone UK
        varchar password_hash
        varchar first_name
        varchar last_name
        varchar avatar_object_key
        varchar status
        timestamptz email_verified_at
        timestamptz phone_verified_at
        timestamptz last_login_at
        timestamptz created_at
        timestamptz updated_at
    }

    identity_restaurant_roles {
        varchar code PK
        varchar name
        varchar description
        smallint hierarchy_level UK
    }

    identity_platform_roles {
        varchar code PK
        varchar name
        varchar description
    }

    identity_permissions {
        varchar code PK
        varchar module
        varchar name
        varchar description
        boolean is_individually_assignable
        timestamptz created_at
    }

    identity_restaurant_role_permissions {
        varchar role_code PK, FK
        varchar permission_code PK, FK
        timestamptz created_at
    }

    identity_platform_role_permissions {
        varchar role_code PK, FK
        varchar permission_code PK, FK
        timestamptz created_at
    }

    identity_user_platform_roles {
        uuid user_id PK, FK
        varchar role_code PK, FK
        uuid assigned_by_user_id FK
        timestamptz assigned_at
    }

    identity_devices {
        uuid id PK
        uuid user_id FK
        varchar device_uid UK
        varchar device_name
        varchar platform
        varchar app_version
        timestamptz created_at
        timestamptz last_active_at
    }

    identity_sessions {
        uuid id PK
        uuid user_id FK
        uuid device_id FK
        varchar refresh_token_hash UK
        inet ip_address
        varchar user_agent
        timestamptz created_at
        timestamptz last_active_at
        timestamptz expires_at
        timestamptz revoked_at
        varchar revoke_reason
    }

    identity_contact_verification_tokens {
        uuid id PK
        uuid user_id FK
        varchar contact_type
        varchar destination
        varchar token_hash UK
        timestamptz created_at
        timestamptz expires_at
        timestamptz used_at
    }

    identity_password_reset_tokens {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz created_at
        timestamptz expires_at
        timestamptz used_at
    }

    identity_restaurant_roles ||--o{ identity_restaurant_role_permissions : grants
    identity_permissions ||--o{ identity_restaurant_role_permissions : included_in
    identity_platform_roles ||--o{ identity_platform_role_permissions : grants
    identity_permissions ||--o{ identity_platform_role_permissions : included_in
    identity_users ||--o{ identity_user_platform_roles : has
    identity_platform_roles ||--o{ identity_user_platform_roles : assigned_as
    identity_users o|--o{ identity_user_platform_roles : assigned_by
    identity_users ||--o{ identity_devices : owns
    identity_users ||--o{ identity_sessions : opens
    identity_devices o|--o{ identity_sessions : used_by
    identity_users ||--o{ identity_contact_verification_tokens : verifies
    identity_users ||--o{ identity_password_reset_tokens : resets
```

Ресторанные роли: `EMPLOYEE`, `MANAGER`, `DIRECTOR`. Глобальная платформенная роль:
`SYSTEM_ADMIN`. Права лежат в данных, а не зашиты в коде.

## Restaurant и Workforce

```mermaid
erDiagram
    identity_users {
        uuid id PK
    }

    identity_restaurant_roles {
        varchar code PK
    }

    identity_permissions {
        varchar code PK
    }

    restaurant_restaurants {
        uuid id PK
        varchar name
        varchar address
        varchar phone
        text description
        varchar logo_object_key
        varchar status
        uuid director_membership_id FK, UK
        timestamptz created_at
        timestamptz updated_at
    }

    restaurant_halls {
        uuid id PK
        uuid restaurant_id FK
        varchar name
        integer display_order
        jsonb layout_settings
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    restaurant_dining_tables {
        uuid id PK
        uuid restaurant_id FK
        uuid hall_id FK
        varchar number
        smallint seats_count
        integer display_order
        varchar status
        varchar status_comment
        timestamptz archived_at
        timestamptz created_at
        timestamptz updated_at
    }

    restaurant_table_status_history {
        uuid id PK
        uuid restaurant_id FK
        uuid table_id FK
        uuid changed_by_membership_id FK
        varchar old_status
        varchar new_status
        varchar comment
        timestamptz created_at
    }

    workforce_restaurant_members {
        uuid id PK
        uuid restaurant_id FK
        uuid user_id FK
        varchar role_code FK
        varchar first_name
        varchar last_name
        varchar job_title
        varchar status
        timestamptz last_active_at
        timestamptz blocked_at
        timestamptz archived_at
        timestamptz created_at
        timestamptz updated_at
    }

    workforce_member_permission_overrides {
        uuid membership_id PK, FK
        varchar permission_code PK, FK
        varchar effect
        uuid changed_by_user_id FK
        varchar reason
        timestamptz created_at
        timestamptz updated_at
    }

    workforce_member_permission_history {
        uuid id PK
        uuid membership_id FK
        varchar permission_code FK
        varchar old_effect
        varchar new_effect
        uuid changed_by_user_id FK
        varchar reason
        timestamptz created_at
    }

    workforce_invitations {
        uuid id PK
        uuid restaurant_id FK
        uuid membership_id FK
        uuid created_by_membership_id FK
        uuid accepted_by_user_id FK
        uuid replaces_invitation_id FK
        varchar email
        varchar role_code FK
        varchar token_hash UK
        varchar status
        timestamptz created_at
        timestamptz expires_at
        timestamptz accepted_at
        timestamptz revoked_at
    }

    workforce_effective_member_permissions {
        uuid membership_id FK
        varchar permission_code FK
    }

    restaurant_restaurants ||--o{ restaurant_halls : contains
    restaurant_halls ||--o{ restaurant_dining_tables : contains
    restaurant_dining_tables ||--o{ restaurant_table_status_history : has_history
    restaurant_restaurants ||--o{ workforce_restaurant_members : employs
    workforce_restaurant_members ||--|| restaurant_restaurants : directs
    identity_users o|--o{ workforce_restaurant_members : account_for
    identity_restaurant_roles ||--o{ workforce_restaurant_members : role
    workforce_restaurant_members ||--o{ restaurant_table_status_history : changes
    workforce_restaurant_members ||--o{ workforce_member_permission_overrides : has_override
    identity_permissions ||--o{ workforce_member_permission_overrides : overrides
    identity_users ||--o{ workforce_member_permission_overrides : changed_by
    workforce_restaurant_members ||--o{ workforce_member_permission_history : has_history
    identity_permissions ||--o{ workforce_member_permission_history : concerns
    identity_users ||--o{ workforce_member_permission_history : changed_by
    workforce_restaurant_members ||--o{ workforce_invitations : receives
    workforce_restaurant_members ||--o{ workforce_invitations : creates
    identity_users o|--o{ workforce_invitations : accepts
    identity_restaurant_roles ||--o{ workforce_invitations : offered_role
    workforce_invitations o|--o{ workforce_invitations : replaces
    workforce_restaurant_members ||--o{ workforce_effective_member_permissions : resolves
    identity_permissions ||--o{ workforce_effective_member_permissions : exposes
```

`workforce.effective_member_permissions` — вычисляемое представление, а не отдельная таблица.
Оно объединяет права роли с индивидуальными `GRANT`/`DENY`.

Состояния столов: `READY`, `NEEDS_CLEANING`, `NEEDS_SERVING`, `PROBLEM`. Цветов в базе нет;
интерфейс получает только состояние и сам выбирает оформление.

## Operations

```mermaid
erDiagram
    restaurant_restaurants {
        uuid id PK
    }

    workforce_restaurant_members {
        uuid id PK
        uuid restaurant_id FK
    }

    operations_shifts {
        uuid id PK
        uuid restaurant_id FK
        varchar name
        timestamptz scheduled_start_at
        timestamptz scheduled_end_at
        timestamptz actual_started_at
        timestamptz actual_closed_at
        varchar status
        uuid responsible_membership_id FK
        uuid created_by_membership_id FK
        uuid started_by_membership_id FK
        uuid closed_by_membership_id FK
        varchar close_comment
        timestamptz created_at
        timestamptz updated_at
    }

    operations_shift_participants {
        uuid id PK
        uuid restaurant_id FK
        uuid shift_id FK
        uuid membership_id FK
        uuid added_by_membership_id FK
        varchar role_in_shift
        varchar attendance_status
        timestamptz checked_in_at
        timestamptz checked_out_at
        timestamptz self_completed_at
        timestamptz created_at
        timestamptz updated_at
    }

    operations_task_templates {
        uuid id PK
        uuid restaurant_id FK
        varchar title
        text description
        varchar default_priority
        uuid created_by_membership_id FK
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    operations_tasks {
        uuid id PK
        uuid restaurant_id FK
        uuid shift_id FK
        uuid carried_from_shift_id FK
        uuid template_id FK
        uuid created_by_membership_id FK
        uuid assigned_to_membership_id FK
        varchar title
        text description
        varchar status
        varchar priority
        varchar carry_over_state
        timestamptz due_at
        timestamptz accepted_at
        timestamptz completed_at
        timestamptz cancelled_at
        timestamptz archived_at
        varchar cancellation_reason
        varchar carry_over_comment
        timestamptz created_at
        timestamptz updated_at
    }

    operations_task_history {
        uuid id PK
        uuid restaurant_id FK
        uuid task_id FK
        uuid actor_membership_id FK
        varchar event_type
        varchar old_status
        varchar new_status
        uuid old_assignee_membership_id FK
        uuid new_assignee_membership_id FK
        uuid from_shift_id FK
        uuid to_shift_id FK
        varchar comment
        jsonb details
        timestamptz created_at
    }

    operations_shift_task_resolutions {
        uuid id PK
        uuid restaurant_id FK
        uuid shift_id FK
        uuid task_id FK
        varchar action
        uuid decided_by_membership_id FK
        varchar comment
        timestamptz created_at
    }

    operations_shift_reports {
        uuid id PK
        uuid restaurant_id FK
        uuid shift_id FK, UK
        uuid generated_by_membership_id FK
        text summary
        jsonb metrics
        timestamptz generated_at
    }

    operations_favorite_task_templates {
        uuid membership_id PK, FK
        uuid restaurant_id FK
        uuid template_id PK, FK
        timestamptz created_at
    }

    restaurant_restaurants ||--o{ operations_shifts : owns
    workforce_restaurant_members ||--o{ operations_shifts : manages
    operations_shifts ||--o{ operations_shift_participants : includes
    workforce_restaurant_members ||--o{ operations_shift_participants : participates
    workforce_restaurant_members ||--o{ operations_shift_participants : adds
    restaurant_restaurants ||--o{ operations_task_templates : owns
    workforce_restaurant_members ||--o{ operations_task_templates : creates
    restaurant_restaurants ||--o{ operations_tasks : owns
    operations_shifts o|--o{ operations_tasks : scheduled_in
    operations_shifts o|--o{ operations_tasks : carried_from
    operations_task_templates o|--o{ operations_tasks : instantiates
    workforce_restaurant_members ||--o{ operations_tasks : creates
    workforce_restaurant_members o|--o{ operations_tasks : assigned_to
    operations_tasks ||--o{ operations_task_history : has_history
    workforce_restaurant_members ||--o{ operations_task_history : changes
    operations_shifts o|--o{ operations_task_history : references
    operations_shifts ||--o{ operations_shift_task_resolutions : resolves_at_close
    operations_tasks ||--o{ operations_shift_task_resolutions : resolution_for
    workforce_restaurant_members ||--o{ operations_shift_task_resolutions : decides
    operations_shifts ||--o| operations_shift_reports : produces
    workforce_restaurant_members ||--o{ operations_shift_reports : generates
    workforce_restaurant_members ||--o{ operations_favorite_task_templates : saves
    operations_task_templates ||--o{ operations_favorite_task_templates : favorited
```

У ресторана может быть только одна активная смена. Просроченность задачи вычисляется из
`due_at` и `status`, отдельного состояния `OVERDUE` нет. Перенос хранится в
`carry_over_state`, а решение при закрытии смены — в `shift_task_resolutions`.

## Billing

```mermaid
erDiagram
    identity_users {
        uuid id PK
    }

    restaurant_restaurants {
        uuid id PK
    }

    workforce_restaurant_members {
        uuid id PK
        uuid restaurant_id FK
    }

    billing_tariff_plans {
        uuid id PK
        varchar code UK
        varchar name
        text description
        numeric price
        integer duration_days
        jsonb features
        varchar status
        uuid created_by_user_id FK
        timestamptz created_at
        timestamptz updated_at
    }

    billing_subscriptions {
        uuid id PK
        uuid restaurant_id FK
        uuid plan_id FK
        uuid activated_by_membership_id FK
        varchar status
        varchar activation_mode
        timestamptz starts_at
        timestamptz ends_at
        timestamptz cancelled_at
        timestamptz created_at
        timestamptz updated_at
    }

    billing_subscription_events {
        uuid id PK
        uuid subscription_id FK
        uuid actor_user_id FK
        varchar event_type
        jsonb details
        timestamptz created_at
    }

    identity_users o|--o{ billing_tariff_plans : creates
    restaurant_restaurants ||--o{ billing_subscriptions : owns
    billing_tariff_plans ||--o{ billing_subscriptions : selected_plan
    workforce_restaurant_members ||--o{ billing_subscriptions : activates
    billing_subscriptions ||--o{ billing_subscription_events : has_history
    identity_users o|--o{ billing_subscription_events : performs
```

Платёжных транзакций, возвратов и споров нет. Кнопка-заглушка активирует бесплатный
`MONTHLY_PLACEHOLDER` на 30 дней; у ресторана может быть только одна активная подписка.

## Notification

```mermaid
erDiagram
    identity_users {
        uuid id PK
    }

    identity_devices {
        uuid id PK
        uuid user_id FK
    }

    restaurant_restaurants {
        uuid id PK
    }

    notification_notifications {
        uuid id PK
        uuid user_id FK
        uuid restaurant_id FK
        varchar type
        varchar title
        text message
        jsonb payload
        timestamptz read_at
        timestamptz created_at
    }

    notification_push_registrations {
        uuid id PK
        uuid user_id FK
        uuid device_id FK, UK
        varchar push_token UK
        varchar provider
        varchar status
        timestamptz created_at
        timestamptz updated_at
        timestamptz last_success_at
        timestamptz last_failure_at
    }

    notification_delivery_attempts {
        uuid id PK
        uuid notification_id FK
        uuid push_registration_id FK
        varchar status
        integer attempt_number
        varchar provider_message_id
        varchar error_message
        timestamptz attempted_at
    }

    notification_outbox_events {
        uuid id PK
        uuid restaurant_id FK
        varchar aggregate_type
        uuid aggregate_id
        varchar event_type
        jsonb payload
        varchar status
        integer attempts_count
        timestamptz available_at
        timestamptz published_at
        varchar last_error
        timestamptz created_at
    }

    identity_users ||--o{ notification_notifications : receives
    restaurant_restaurants o|--o{ notification_notifications : context
    identity_users ||--o{ notification_push_registrations : registers
    identity_devices ||--o| notification_push_registrations : push_endpoint
    notification_notifications ||--o{ notification_delivery_attempts : delivered_by
    notification_push_registrations ||--o{ notification_delivery_attempts : target
    restaurant_restaurants o|--o{ notification_outbox_events : context
```

Внутренних диалогов и сообщений между пользователями нет. `message` — только текст системного
уведомления, а `outbox_events` — техническая очередь событий для RabbitMQ/WebSocket.

## Audit

```mermaid
erDiagram
    identity_users {
        uuid id PK
    }

    workforce_restaurant_members {
        uuid id PK
    }

    restaurant_restaurants {
        uuid id PK
    }

    audit_activity_logs {
        uuid id PK
        uuid user_id FK
        uuid membership_id FK
        uuid restaurant_id FK
        varchar action
        varchar entity_type
        uuid entity_id
        jsonb details
        timestamptz created_at
    }

    audit_audit_logs {
        uuid id PK
        uuid actor_user_id FK
        uuid actor_membership_id FK
        uuid subject_user_id FK
        uuid restaurant_id FK
        varchar scope
        varchar action
        varchar entity_type
        uuid entity_id
        varchar outcome
        inet ip_address
        varchar user_agent
        jsonb details
        timestamptz created_at
    }

    audit_system_events {
        uuid id PK
        varchar level
        varchar service
        varchar event_type
        text message
        varchar correlation_id
        jsonb details
        timestamptz created_at
    }

    identity_users ||--o{ audit_activity_logs : acts
    workforce_restaurant_members o|--o{ audit_activity_logs : restaurant_context
    restaurant_restaurants o|--o{ audit_activity_logs : scopes
    identity_users o|--o{ audit_audit_logs : actor
    identity_users o|--o{ audit_audit_logs : subject
    workforce_restaurant_members o|--o{ audit_audit_logs : member_context
    restaurant_restaurants o|--o{ audit_audit_logs : scopes
```

`audit_logs`, `activity_logs`, `system_events`, а также истории задач, статусов столов и
индивидуальных прав — append-only: изменение и удаление блокируются триггерами.

## Важные обозначения и ограничения

- `PK` — первичный ключ, `FK` — внешний ключ, `UK` — уникальное значение/ограничение.
- В составных внешних ключах вместе с идентификатором передаётся `restaurant_id`, чтобы нельзя
  было связать сущности разных ресторанов.
- Ресторан имеет строго одного неархивного директора.
- Индивидуальные права хранятся для конкретного `restaurant_members.id`, а не глобально для
  пользователя.
- Технические индексы, `CHECK`-ограничения и триггеры перечислены в миграциях; на ERD показаны
  сущности, поля, уникальность и внешние связи.

Исходные миграции: [`backend/src/main/resources/db/migration`](../src/main/resources/db/migration).
