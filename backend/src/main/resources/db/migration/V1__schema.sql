CREATE TABLE bank_users (
 id uuid PRIMARY KEY, username varchar(64) NOT NULL UNIQUE, password_hash varchar(100) NOT NULL,
 full_name varchar(120) NOT NULL, role varchar(16) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
 failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0), locked boolean NOT NULL DEFAULT false,
 last_login timestamptz
);
CREATE TABLE auth_sessions (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES bank_users(id) ON DELETE CASCADE,
 last_activity timestamptz NOT NULL, expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false
);
CREATE INDEX auth_sessions_user_idx ON auth_sessions(user_id);
CREATE TABLE auth_audit (
 id uuid PRIMARY KEY, user_id uuid REFERENCES bank_users(id) ON DELETE SET NULL,
 event varchar(32) NOT NULL, occurred_at timestamptz NOT NULL
);
CREATE INDEX auth_audit_user_idx ON auth_audit(user_id);
