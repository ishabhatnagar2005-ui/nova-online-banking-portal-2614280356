CREATE TABLE promotions (
 id uuid PRIMARY KEY,
 promotion_key text NOT NULL UNIQUE,
 title text NOT NULL,
 category text NOT NULL CHECK (category IN ('SAVINGS','CARDS','LOANS')),
 target_segment text NOT NULL CHECK (target_segment IN ('MIGUEL','ROSA','ALL')),
 benefit text NOT NULL,
 terms text NOT NULL,
 eligibility text NOT NULL,
 valid_until date NOT NULL,
 active boolean NOT NULL
);
CREATE INDEX promotions_eligibility_idx ON promotions(active, valid_until, target_segment);
CREATE TABLE promotion_interests (
 id uuid PRIMARY KEY,
 customer_id uuid NOT NULL REFERENCES bank_users(id) ON DELETE RESTRICT,
 promotion_id uuid NOT NULL REFERENCES promotions(id) ON DELETE RESTRICT,
 registered_at timestamptz NOT NULL
);
CREATE INDEX promotion_interests_customer_idx ON promotion_interests(customer_id);
CREATE INDEX promotion_interests_promotion_idx ON promotion_interests(promotion_id);
