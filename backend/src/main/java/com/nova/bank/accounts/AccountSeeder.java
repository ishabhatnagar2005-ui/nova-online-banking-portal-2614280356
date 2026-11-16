package com.nova.bank.accounts;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Insert synthetic transactions without overwriting existing records. */
@Component public class AccountSeeder {
    private final JdbcTemplate jdbc;
    public AccountSeeder(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    /** Seed only rows whose expected account exists. */
    public void seed() {
        insert("01","02","2026-10-13T09:30:00Z","Salary","INCOME","CREDIT","POSTED","48500.00","REF-TX-882191");
        insert("02","02","2026-10-12T14:15:00Z","Manila Supermarket","GROCERIES","DEBIT","POSTED","2126.40","REF-TX-882192");
        insert("03","02","2026-10-10T11:00:00Z","Savings goal transfer","SAVINGS","DEBIT","POSTED","5000.00","REF-TX-882193");
        insert("04","02","2026-10-09T16:45:00Z","Makati Coffee Shop","DINING","DEBIT","PENDING","185.00","REF-TX-882194");
        insert("05","02","2026-10-01T08:20:00Z","Metro Manila Transit","TRANSPORT","DEBIT","POSTED","1200.00","REF-TX-882195");
        insert("06","03","2026-10-08T21:10:00Z","Unknown merchant","GENERAL","DEBIT","DECLINED","0.00","REF-TX-882196");
    }
    private void insert(String id,String account,String at,String description,String category,String direction,String status,String amount,String reference) {
        jdbc.update("INSERT INTO account_transactions(id,account_id,posted_at,description,category,direction,status,amount,reference) SELECT ?,a.id,?::timestamptz,?,?,?,?,?::numeric,? FROM accounts a JOIN bank_users u ON u.id=a.owner_id WHERE a.id=? AND u.id=? AND u.username='miguel' ON CONFLICT DO NOTHING",UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+id),at,description,category,direction,status,amount,reference,UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+account),UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
    }
}
