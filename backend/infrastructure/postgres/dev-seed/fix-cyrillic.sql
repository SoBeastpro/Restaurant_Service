\connect operations_db
SET ROLE operations_app;
UPDATE tasks SET
    title = 'Убрать стол №2',
    description = 'Подготовить стол к следующему гостю'
WHERE id = '43000000-0000-0000-0000-000000000001';
UPDATE tasks SET
    title = 'Проверить основной зал',
    description = 'Проверить готовность столов и проходов'
WHERE id = '43000000-0000-0000-0000-000000000002';
UPDATE tasks SET
    title = 'Пополнить воду на раздаче',
    description = 'Пополнить воду на раздаче'
WHERE id = '43000000-0000-0000-0000-000000000003';
RESET ROLE;
