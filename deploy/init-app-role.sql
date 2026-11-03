-- Create the application role once, after the database container first boots.
-- The password is passed in via psql variable so it never appears in this file:
--   docker compose exec -T db psql -U postgres -d magnus \
--       -v app_password="$(cat /etc/magnus/secrets/app-database-password)" \
--       -f - < deploy/init-app-role.sql
-- psql variable interpolation:
\set app_password `cat /run/secrets/app_database_password 2>/dev/null || echo "$APP_DB_PASSWORD"`
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'magnus') THEN
        CREATE ROLE magnus LOGIN;
    END IF;
END $$;
ALTER ROLE magnus WITH PASSWORD :'app_password';
GRANT ALL PRIVILEGES ON DATABASE magnus TO magnus;
-- schema usage + default privileges for tables created by migrations
GRANT ALL ON SCHEMA public TO magnus;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO magnus;
