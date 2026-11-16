CREATE TABLE support_inquiries (
 id uuid PRIMARY KEY,
 customer_id uuid NOT NULL REFERENCES bank_users(id) ON DELETE RESTRICT,
 topic varchar(80) NOT NULL CHECK (length(trim(topic)) > 0),
 subject varchar(160) NOT NULL CHECK (length(trim(subject)) > 0),
 message varchar(4000) NOT NULL CHECK (length(trim(message)) > 0),
 reference_number varchar(40) NOT NULL UNIQUE,
 created_at timestamptz NOT NULL
);
CREATE INDEX support_inquiries_customer_idx ON support_inquiries(customer_id);
