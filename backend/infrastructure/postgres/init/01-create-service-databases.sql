-- Local development only. Production credentials must come from a secret manager.
CREATE ROLE identity_app LOGIN PASSWORD 'identity';
CREATE ROLE restaurant_app LOGIN PASSWORD 'restaurant';
CREATE ROLE workforce_app LOGIN PASSWORD 'workforce';
CREATE ROLE operations_app LOGIN PASSWORD 'operations';
CREATE ROLE billing_app LOGIN PASSWORD 'billing';
CREATE ROLE notification_app LOGIN PASSWORD 'notification';
CREATE ROLE audit_app LOGIN PASSWORD 'audit';
CREATE ROLE reporting_app LOGIN PASSWORD 'reporting';

CREATE DATABASE identity_db OWNER identity_app;
CREATE DATABASE restaurant_db OWNER restaurant_app;
CREATE DATABASE workforce_db OWNER workforce_app;
CREATE DATABASE operations_db OWNER operations_app;
CREATE DATABASE billing_db OWNER billing_app;
CREATE DATABASE notification_db OWNER notification_app;
CREATE DATABASE audit_db OWNER audit_app;
CREATE DATABASE reporting_db OWNER reporting_app;
