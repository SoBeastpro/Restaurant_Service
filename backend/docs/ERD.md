# ERD микросервисных баз

Диаграммы разделены по физическим базам. Пунктирные логические ссылки между диаграммами не являются
внешними ключами PostgreSQL.

## Identity

```mermaid
erDiagram
    USERS ||--o{ DEVICES : owns
    USERS ||--o{ SESSIONS : owns
    DEVICES ||--o{ SESSIONS : starts
    USERS ||--o{ USER_PLATFORM_ROLES : has
    PLATFORM_ROLES ||--o{ USER_PLATFORM_ROLES : grants
    PLATFORM_ROLES ||--o{ PLATFORM_ROLE_PERMISSIONS : contains
    PLATFORM_PERMISSIONS ||--o{ PLATFORM_ROLE_PERMISSIONS : includes
    USERS ||--o{ CONTACT_VERIFICATION_TOKENS : verifies
    USERS ||--o{ PASSWORD_RESET_TOKENS : resets
```

## Restaurant

```mermaid
erDiagram
    RESTAURANTS ||--o{ HALLS : contains
    HALLS ||--o{ DINING_TABLES : contains
    DINING_TABLES ||--o{ TABLE_STATUS_HISTORY : records
```

`RESTAURANTS.director_user_id` ссылается логически на identity, а
`TABLE_STATUS_HISTORY.changed_by_membership_id` — на workforce.

## Workforce

```mermaid
erDiagram
    RESTAURANT_ROLES ||--o{ RESTAURANT_MEMBERS : assigns
    RESTAURANT_ROLES ||--o{ RESTAURANT_ROLE_PERMISSIONS : contains
    PERMISSIONS ||--o{ RESTAURANT_ROLE_PERMISSIONS : includes
    RESTAURANT_MEMBERS ||--o{ MEMBER_PERMISSION_OVERRIDES : overrides
    PERMISSIONS ||--o{ MEMBER_PERMISSION_OVERRIDES : targets
    RESTAURANT_MEMBERS ||--o{ MEMBER_PERMISSION_HISTORY : audits
    RESTAURANT_MEMBERS ||--o{ INVITATIONS : activates
    INVITATIONS o|--o{ INVITATIONS : replaces
```

`restaurant_id` и `user_id` — логические ссылки на restaurant и identity.

## Operations

```mermaid
erDiagram
    SHIFTS ||--o{ SHIFT_PARTICIPANTS : includes
    SHIFTS ||--o{ TASKS : groups
    TASK_TEMPLATES ||--o{ TASKS : creates
    TASKS ||--o{ TASK_HISTORY : records
    SHIFTS ||--o{ SHIFT_TASK_RESOLUTIONS : resolves
    TASKS ||--o{ SHIFT_TASK_RESOLUTIONS : resolves
    SHIFTS ||--o| SHIFT_REPORTS : summarizes
    TASK_TEMPLATES ||--o{ FAVORITE_TASK_TEMPLATES : favorites
```

Все `membership_id` и `restaurant_id` являются внешними UUID без FK.

## Billing, Notification, Audit, Reporting

```mermaid
erDiagram
    TARIFF_PLANS ||--o{ SUBSCRIPTIONS : selected
    SUBSCRIPTIONS ||--o{ SUBSCRIPTION_EVENTS : records

    NOTIFICATIONS ||--o{ DELIVERY_ATTEMPTS : delivered
    PUSH_REGISTRATIONS ||--o{ DELIVERY_ATTEMPTS : targets

    RESTAURANT_DAILY_METRICS {
        uuid restaurant_id
        date metric_date
    }
    EMPLOYEE_DAILY_METRICS {
        uuid restaurant_id
        uuid membership_id
        date metric_date
    }
    TABLE_DAILY_METRICS {
        uuid restaurant_id
        uuid table_id
        date metric_date
    }
```

Audit-таблицы append-only и не имеют доменных FK: они должны сохранять историю даже после
архивирования исходной сущности.

## Интеграционные таблицы

`identity`, `restaurant`, `workforce`, `operations`, `billing` и `notification` имеют собственную
`outbox_events`. Все потребители имеют `inbox_messages`. Эти таблицы намеренно дублируются в базах:
они являются частью надёжности каждого сервиса, а не общей инфраструктурной базой.
