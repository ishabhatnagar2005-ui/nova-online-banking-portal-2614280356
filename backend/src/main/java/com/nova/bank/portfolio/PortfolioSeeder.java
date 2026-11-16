package com.nova.bank.portfolio;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Insert the specified synthetic accounts without overwriting existing data. */
@Component public class PortfolioSeeder {
    private final JdbcTemplate jdbc;
    public PortfolioSeeder(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    /** Seed only if the expected demo identity is present. */
    public void seed() {
        insert("01","11","miguel","4821","DEPOSIT","Payroll","26240.15","26240.15",null,null,null);
        insert("02","11","miguel","9034","DEPOSIT","Peso Savings","116620.10","116620.10",null,"3.25","2026-09-30");
        insert("03","11","miguel","1188","CREDIT_CARD","Rewards Credit Card",null,"8450.00","50000.00",null,null);
        insert("04","11","miguel","7712","LOAN","Home Loan",null,"2418450.00",null,null,null);
        insert("05","12","rosa","2204","DEPOSIT","Salary","54300.00","54300.00",null,null,null);
        insert("06","12","rosa","6640","LOAN","Home Loan",null,"1250000.00",null,null,null);
    }
    private void insert(String account, String owner, String username, String suffix, String type, String name, String available, String current, String limit, String rate, String statement) {
        jdbc.update("INSERT INTO accounts (id,owner_id,identifier_suffix,type,name,available_balance,current_balance,credit_limit,interest_rate,status,last_statement_date) SELECT ?,id,?,?,?,?::numeric,?::numeric,?::numeric,?::numeric,'ACTIVE',?::date FROM bank_users WHERE id=? AND username=? ON CONFLICT (id) DO NOTHING",
            UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+account),suffix,type,name,available,current,limit,rate,statement,UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+owner),username);
    }
}
