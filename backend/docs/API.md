# REST Restaurant API

- Версия контракта: `0.2.0`
- Базовый путь: `/api/v1`
- Основной формат: JSON
- Формальная спецификация: [`api/openapi.yaml`](api/openapi.yaml)
- Статус: публичный контракт Gateway, черновик реализации

## API в микросервисной архитектуре

Мобильные приложения обращаются только к API Gateway. Публичные пути `/api/v1` сохраняются при
разделении бэкенда: iOS и Android не должны знать адреса внутренних сервисов. Точный владелец
каждой группы путей задан в [`../contracts/gateway/routes.yaml`](../contracts/gateway/routes.yaml).

| Публичная область | Владелец |
|---|---|
| `/auth/**`, профиль и сессии | `identity-service` |
| рестораны, залы и столы | `restaurant-service` |
| сотрудники, приглашения и permissions | `workforce-service` |
| смены, посещаемость, задачи и шаблоны | `operations-service` |
| подписки | `billing-service` |
| уведомления и push-устройства | `notification-service` |
| аудит | `audit-service` |
| статистика | `reporting-service` |
| `/platform/users/**` | `identity-service` |
| `/platform/restaurants/**` | `restaurant-service` |
| составные read-only `/me/**` | композиция в Gateway |

Все изменяющие состояние запросы должны поддерживать `Idempotency-Key`; Gateway прокидывает
`X-Request-Id` и W3C `traceparent`. Повторять автоматически разрешено только безопасные запросы.
Событийный контракт сервисов описан в
[`../contracts/events/asyncapi.yaml`](../contracts/events/asyncapi.yaml).

## Условные обозначения доступа

| Обозначение | Значение |
|---|---|
| Public | Авторизация не требуется |
| Authenticated | Любой авторизованный пользователь |
| Manager | Менеджер ресторана |
| Employee | Официант или раннер (или другой сотрудник)    |
| Platform admin | Системная поддержка (будет реализована позднее) |
| TBD | Будет уточнено после утверждения permissions |

## Авторизация и сессии

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `POST` | `/auth/register-manager` | Public | Зарегистрировать менеджера |
| `POST` | `/auth/login` | Public | Выполнить вход |
| `POST` | `/auth/refresh` | Public | Обновить пару токенов |
| `POST` | `/auth/logout` | Authenticated | Завершить текущую сессию |
| `POST` | `/auth/logout-all` | Authenticated | Завершить все сессии пользователя |
| `POST` | `/auth/email/verify` | Public | Подтвердить email |
| `POST` | `/auth/email/resend` | Public | Повторно отправить подтверждение |
| `POST` | `/auth/password/forgot` | Public | Запросить восстановление пароля |
| `POST` | `/auth/password/reset` | Public | Установить новый пароль по токену |
| `POST` | `/auth/password/change` | Authenticated | Изменить пароль |
| `GET` | `/auth/sessions` | Authenticated | Получить активные сессии и устройства |
| `DELETE` | `/auth/sessions/{sessionId}` | Authenticated | Завершить выбранную сессию |

## Текущий пользователь

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/me` | Authenticated | Получить глобальный профиль пользователя |
| `PATCH` | `/me` | Authenticated | Изменить доступные поля профиля |
| `GET` | `/me/restaurants` | Authenticated | Получить рестораны и роли текущего пользователя |
| `POST` | `/me/avatar` | Authenticated | Загрузить фотографию профиля |
| `DELETE` | `/me/avatar` | Authenticated | Удалить фотографию профиля |
| `GET` | `/me/permissions` | Authenticated | Получить permissions; состав пока TBD |
| `GET` | `/me/current-shift` | Authenticated | Получить текущую смену пользователя |

## Ресторан

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `POST` | `/restaurants` | Authenticated | Создать ресторан во время onboarding |
| `GET` | `/restaurants/{restaurantId}` | Authenticated | Получить профиль ресторана |
| `PATCH` | `/restaurants/{restaurantId}` | Manager | Изменить профиль ресторана |
| `DELETE` | `/restaurants/{restaurantId}` | Manager | Архивировать ресторан |
| `POST` | `/restaurants/{restaurantId}/logo` | Manager | Загрузить логотип |
| `DELETE` | `/restaurants/{restaurantId}/logo` | Manager | Удалить логотип |

## Сотрудники

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/restaurants/{restaurantId}/employees` | Manager | Получить сотрудников с фильтрами |
| `GET` | `/restaurants/{restaurantId}/employees/{employeeId}` | Manager | Получить карточку сотрудника |
| `PATCH` | `/restaurants/{restaurantId}/employees/{employeeId}` | Manager | Изменить имя, фамилию и должность |
| `PATCH` | `/restaurants/{restaurantId}/employees/{employeeId}/status` | Manager | Изменить статус сотрудника |
| `DELETE` | `/restaurants/{restaurantId}/employees/{employeeId}` | Manager | Архивировать сотрудника |
| `GET` | `/restaurants/{restaurantId}/employees/{employeeId}/shifts` | Manager | Получить историю смен сотрудника |

Предварительные должности: `MANAGER`, `WAITER`, `RUNNER`.
Статусы профиля сотрудника в ресторане: `INVITED`, `ACTIVE`, `INACTIVE`, `BLOCKED`, `ARCHIVED`.

Если сотрудник не использовал профиль конкретного ресторана 30 дней, профиль получает статус
`INACTIVE`. Профили `INACTIVE` и `BLOCKED` хранятся ещё минимум 30 дней. После этого профиль
можно архивировать, но нельзя удалять глобальный аккаунт, если он используется в других ресторанах.
Исторические ссылки из смен, задач и аудита должны сохраняться.

## Приглашения

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `POST` | `/restaurants/{restaurantId}/invitations` | Manager | Создать сотрудника со статусом `INVITED` и приглашение |
| `GET` | `/restaurants/{restaurantId}/invitations` | Manager | Получить приглашения ресторана |
| `DELETE` | `/restaurants/{restaurantId}/invitations/{invitationId}` | Manager | Отозвать приглашение |
| `POST` | `/restaurants/{restaurantId}/invitations/{invitationId}/resend` | Manager | Перевыпустить или повторно отправить приглашение |
| `GET` | `/invitations/{token}` | Public | Проверить приглашение и показать сведения сотруднику |
| `POST` | `/invitations/{token}/accept` | Public | Создать аккаунт или привязать существующий и активировать профиль ресторана |

Создание приглашения:

```json
{
  "firstName": "Анна",
  "lastName": "Соколова",
  "position": "WAITER"
}
```

Принятие приглашения:

```json
{
  "login": "anna.sokolova",
  "password": "strong-password"
}
```

Если глобального аккаунта ещё нет, сотрудник задаёт логин и пароль при принятии первого
приглашения. Если аккаунт уже существует, те же логин и пароль подтверждают личность, а backend
добавляет новую связь с рестораном. Логин после создания аккаунта не изменяется. Пароль сотрудник
может менять самостоятельно через `/auth/password/change`.

Статусы приглашения: `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED`.

## Залы и столы

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/restaurants/{restaurantId}/halls` | Authenticated | Получить залы ресторана |
| `POST` | `/restaurants/{restaurantId}/halls` | Manager | Создать зал |
| `GET` | `/restaurants/{restaurantId}/halls/{hallId}` | Authenticated | Получить зал и его план |
| `PATCH` | `/restaurants/{restaurantId}/halls/{hallId}` | Manager | Изменить зал |
| `DELETE` | `/restaurants/{restaurantId}/halls/{hallId}` | Manager | Архивировать зал |
| `GET` | `/restaurants/{restaurantId}/tables` | Authenticated | Получить столы ресторана |
| `POST` | `/restaurants/{restaurantId}/halls/{hallId}/tables` | Manager | Добавить стол в зал |
| `GET` | `/restaurants/{restaurantId}/tables/{tableId}` | Authenticated | Получить стол |
| `PATCH` | `/restaurants/{restaurantId}/tables/{tableId}` | Manager | Изменить номер, места или положение |
| `DELETE` | `/restaurants/{restaurantId}/tables/{tableId}` | Manager | Архивировать стол |
| `POST` | `/restaurants/{restaurantId}/tables/{tableId}/status-changes` | TBD | Изменить состояние стола и записать историю |
| `GET` | `/restaurants/{restaurantId}/tables/{tableId}/status-history` | Manager | Получить историю состояний стола |

Предварительные состояния стола: `NEEDS_CLEANING`, `NEEDS_SERVING`, `READY`, `PROBLEM`.

Новый стол всегда получает статус `READY`. Из любого состояния разрешён переход в любое другое
состояние. Для `PROBLEM` непустой комментарий обязателен.

План зала использует автоматическую сетку: backend хранит порядок столов, но не координаты `x/y`.
Расположение рассчитывает React Native в зависимости от размера экрана.

## Смены и посещаемость

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/restaurants/{restaurantId}/shifts` | Manager | Получить смены с фильтрами |
| `POST` | `/restaurants/{restaurantId}/shifts` | Manager | Запланировать смену |
| `GET` | `/restaurants/{restaurantId}/shifts/current` | Authenticated | Получить активную смену ресторана |
| `GET` | `/restaurants/{restaurantId}/shifts/{shiftId}` | Authenticated | Получить смену |
| `PATCH` | `/restaurants/{restaurantId}/shifts/{shiftId}` | Manager | Изменить запланированную смену |
| `POST` | `/restaurants/{restaurantId}/shifts/{shiftId}/start` | Manager | Начать смену |
| `POST` | `/restaurants/{restaurantId}/shifts/{shiftId}/close` | Manager | Завершить смену |
| `GET` | `/restaurants/{restaurantId}/shifts/{shiftId}/report` | Manager | Получить отчёт смены |
| `GET` | `/restaurants/{restaurantId}/shifts/{shiftId}/participants` | Authenticated | Получить участников смены |
| `POST` | `/restaurants/{restaurantId}/shifts/{shiftId}/participants` | Manager | Добавить сотрудника в смену |
| `POST` | `/restaurants/{restaurantId}/shifts/{shiftId}/participants/{employeeId}/check-in` | Manager | Зафиксировать приход сотрудника |
| `POST` | `/restaurants/{restaurantId}/shifts/{shiftId}/participants/{employeeId}/check-out` | Manager | Зафиксировать уход сотрудника |
| `GET` | `/restaurants/{restaurantId}/shifts/{shiftId}/attendance` | Manager | Получить интервалы присутствия |

Статусы смены: `PLANNED`, `ACTIVE`, `CLOSED`, `CANCELLED`.
Если активная смена уже существует, попытка открыть другую возвращает `409 Conflict`.

При закрытии смены менеджер выбирает действие для каждой незавершённой задачи:

- `CARRY_OVER` — перенести задачу в очередь следующей смены без назначенного сотрудника;
- `CANCEL` — отменить и архивировать задачу, сохранив её в истории.

При открытии следующей смены менеджер назначает перенесённые задачи сотрудникам,
которые присутствуют в этой смене.

## Задачи и шаблоны

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/restaurants/{restaurantId}/tasks` | Authenticated | Получить задачи с фильтрами |
| `POST` | `/restaurants/{restaurantId}/tasks` | Manager | Создать задачу |
| `GET` | `/restaurants/{restaurantId}/tasks/{taskId}` | Authenticated | Получить задачу |
| `PATCH` | `/restaurants/{restaurantId}/tasks/{taskId}` | Manager | Изменить задачу |
| `DELETE` | `/restaurants/{restaurantId}/tasks/{taskId}` | Manager | Отменить или архивировать задачу |
| `POST` | `/restaurants/{restaurantId}/tasks/{taskId}/assign` | Manager | Назначить задачу сотруднику |
| `POST` | `/restaurants/{restaurantId}/tasks/{taskId}/status-changes` | TBD | Изменить статус задачи |
| `GET` | `/restaurants/{restaurantId}/tasks/{taskId}/history` | Authenticated | Получить историю задачи |
| `POST` | `/restaurants/{restaurantId}/tasks/{taskId}/carry-over` | Manager | Перенести задачу без исполнителя в очередь следующей смены |
| `GET` | `/restaurants/{restaurantId}/task-templates` | Manager | Получить шаблоны задач |
| `POST` | `/restaurants/{restaurantId}/task-templates` | Manager | Создать шаблон |
| `GET` | `/restaurants/{restaurantId}/task-templates/{templateId}` | Manager | Получить шаблон |
| `PATCH` | `/restaurants/{restaurantId}/task-templates/{templateId}` | Manager | Изменить шаблон |
| `DELETE` | `/restaurants/{restaurantId}/task-templates/{templateId}` | Manager | Удалить шаблон |
| `POST` | `/restaurants/{restaurantId}/task-templates/{templateId}/tasks` | Manager | Создать задачу из шаблона |

Статусы задачи: `CREATED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.

Разрешённые переходы:

- после создания назначенная сотруднику задача имеет статус `CREATED`;
- сотрудник принимает задачу: `CREATED → IN_PROGRESS`; отказаться от задачи нельзя;
- сотрудник завершает принятую задачу: `IN_PROGRESS → COMPLETED`;
- менеджер может выполнить `CREATED → CANCELLED`, `IN_PROGRESS → CANCELLED`,
  `CREATED → COMPLETED` или `IN_PROGRESS → COMPLETED`.

Просроченность вычисляется по сроку выполнения и не является самостоятельным статусом.

## Уведомления и устройства

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/me/notifications` | Authenticated | Получить уведомления |
| `PATCH` | `/me/notifications/{notificationId}/read` | Authenticated | Отметить уведомление прочитанным |
| `POST` | `/me/notifications/read-all` | Authenticated | Отметить все уведомления прочитанными |
| `POST` | `/me/devices` | Authenticated | Зарегистрировать push-токен устройства |
| `DELETE` | `/me/devices/{deviceId}` | Authenticated | Удалить устройство |

## Статистика и аудит

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/restaurants/{restaurantId}/statistics/overview` | Manager | Получить сводную статистику |
| `GET` | `/restaurants/{restaurantId}/statistics/tasks` | Manager | Получить статистику задач |
| `GET` | `/restaurants/{restaurantId}/statistics/tables` | Manager | Получить статистику столов |
| `GET` | `/restaurants/{restaurantId}/statistics/employees` | Manager | Получить статистику сотрудников |
| `GET` | `/restaurants/{restaurantId}/employees/{employeeId}/statistics` | Manager | Получить статистику сотрудника |
| `GET` | `/restaurants/{restaurantId}/audit-log` | Manager | Получить журнал важных действий |

## Подписка

До интеграции платёжного провайдера этот блок является заглушкой.

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/restaurants/{restaurantId}/subscription` | Manager | Получить состояние подписки |
| `POST` | `/restaurants/{restaurantId}/subscription/checkout` | Manager | Начать оформление оплаты |
| `POST` | `/restaurants/{restaurantId}/subscription/skip` | Manager | Пропустить оплату в тестовом режиме |

## Реальное время

WebSocket endpoint: `/api/v1/ws`.

Предварительные события:

- `TABLE_STATUS_CHANGED`
- `TASK_CREATED`
- `TASK_ASSIGNED`
- `TASK_UPDATED`
- `SHIFT_PARTICIPANT_JOINED`
- `SHIFT_PARTICIPANT_LEFT`
- `SHIFT_CLOSED`
- `NOTIFICATION_CREATED`

React Native не подключается напрямую к RabbitMQ. RabbitMQ используется внутри backend.

## Системная поддержка

Этот блок обязателен, но будет реализован позднее под `/api/v1/platform`.

| Метод | Endpoint | Доступ | Назначение |
|---|---|---|---|
| `GET` | `/platform/restaurants` | Platform admin | Получить рестораны платформы |
| `PATCH` | `/platform/restaurants/{restaurantId}/status` | Platform admin | Заблокировать или разблокировать ресторан |
| `GET` | `/platform/users` | Platform admin | Получить пользователей платформы |
| `PATCH` | `/platform/users/{userId}/status` | Platform admin | Заблокировать или разблокировать пользователя |
| `GET` | `/platform/subscriptions` | Platform admin | Получить подписки |
| `GET` | `/platform/audit-log` | Platform admin | Получить журнал платформы |
| `GET` | `/platform/system-events` | Platform admin | Получить технические события |

## Общие HTTP-ответы

| Код | Значение |
|---|---|
| `200 OK` | Успешное чтение или изменение |
| `201 Created` | Объект создан |
| `204 No Content` | Операция выполнена без тела ответа |
| `400 Bad Request` | Некорректные данные |
| `401 Unauthorized` | Пользователь не авторизован |
| `403 Forbidden` | Недостаточно прав или нет доступа к ресторану |
| `404 Not Found` | Объект не найден |
| `409 Conflict` | Конфликт состояния или уникальности |
| `422 Unprocessable Content` | Бизнес-правило не позволяет выполнить действие |
| `429 Too Many Requests` | Превышен лимит запросов |

## Вопросы, которые ещё нужно согласовать

1. Точный состав permissions для менеджера, официанта и раннера.
2. Требуется ли сотруднику подтверждать email, если вход выполняется по логину.
3. Срок действия приглашения и правила его перевыпуска.
4. Кто из ролей может менять каждый статус стола.
5. Что происходит с профилем ресторана после дополнительных 30 дней хранения: архивирование или анонимизация.
6. Точные периоды хранения архивированных данных и истории.
