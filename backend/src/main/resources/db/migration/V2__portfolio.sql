ALTER TABLE auth_sessions ADD COLUMN previous_login timestamptz;
CREATE TABLE accounts (
 id uuid PRIMARY KEY,
 owner_id uuid NOT NULL REFERENCES bank_users(id) ON DELETE CASCADE,
 identifier_suffix char(4) NOT NULL CHECK (identifier_suffix ~ '^[0-9]{4}$'),
 type varchar(16) NOT NULL CHECK (type IN ('DEPOSIT','CREDIT_CARD','LOAN')),
 name varchar(120) NOT NULL,
 available_balance numeric(15,2), current_balance numeric(15,2),
 credit_limit numeric(15,2), interest_rate numeric(5,2),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
 last_statement_date date,
 CHECK (type <> 'DEPOSIT' OR available_balance IS NOT NULL),
 CHECK (type <> 'CREDIT_CARD' OR (credit_limit IS NOT NULL AND current_balance IS NOT NULL))
);
CREATE INDEX accounts_owner_idx ON accounts(owner_id);
