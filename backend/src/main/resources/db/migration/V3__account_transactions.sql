CREATE TABLE account_transactions (
 id uuid PRIMARY KEY,
 account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 posted_at timestamptz NOT NULL,
 description varchar(200) NOT NULL,
 category varchar(20) NOT NULL CHECK (category IN ('INCOME','GROCERIES','SAVINGS','DINING','TRANSPORT','GENERAL')),
 direction varchar(6) NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
 status varchar(8) NOT NULL CHECK (status IN ('POSTED','PENDING','DECLINED')),
 amount numeric(15,2) NOT NULL CHECK (amount >= 0),
 reference varchar(40) NOT NULL UNIQUE
);
CREATE INDEX account_transactions_account_date_idx ON account_transactions(account_id, posted_at DESC, id);
