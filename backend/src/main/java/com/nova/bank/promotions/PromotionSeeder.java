package com.nova.bank.promotions;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Insert illustrative campaigns without overwriting existing records. */
@Component public class PromotionSeeder {
    private final JdbcTemplate jdbc;
    public PromotionSeeder(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    /** Seed the demonstration catalogue idempotently. */
    public void seed() {
        insert("01","payday-ipon-boost","Payday Ipon Boost","SAVINGS","MIGUEL","Earn a goal bonus when you meet your monthly Peso Savings target.","Illustrative monthly goal bonus; no actual bonus is paid.","Miguel's Peso Savings goal customers.",true);
        insert("02","payday-ipon-bonus","Payday Ipon Bonus","SAVINGS","MIGUEL","Build savings with automatic payroll transfers.","Illustrative benefit for Ipon Goal Saver enrollees; no transfer is arranged.","Miguel's Ipon Goal Saver enrollees.",true);
        insert("03","peso-savings-bonus","Peso Savings Bonus","SAVINGS","MIGUEL","An additional 0.50% p.a. on tiered incremental balances.","Illustrative rate applies to tiered incremental balances; no interest is paid through this demo.","Miguel's eligible Peso Savings customers.",true);
        insert("04","cashback-week","Cashback Week","CARDS","MIGUEL","Rewards on department store and weekend grocery spending.","Illustrative cashback capped at PHP 2,000 monthly; requires a Nova Rewards Card.","Miguel's Nova Rewards Card holders.",true);
        insert("05","dining-cashback-5","5% Dining Cashback","CARDS","MIGUEL","5% dining cashback at Metro Manila partners.","Illustrative cashback appears as a subsequent bill credit; no real credit is issued.","Miguel's active cardholders.",true);
        insert("06","personal-loan-offer","Personal Loan Offer","LOANS","MIGUEL","Indicative low APR based on payroll history.","Rates are illustrative and subject to eligibility; this is not a loan application.","Miguel's eligible payroll customers.",true);
        insert("07","home-loan-fee-waiver","Home Loan Fee Waiver","LOANS","ROSA","100% appraisal fee waiver for prime residential applications.","Illustrative waiver for qualifying prime residential applications; no real application is made.","Rosa's eligible home loan customers.",true);
        insert("08","term-deposit-promo","Term Deposit Promo","SAVINGS","ALL","4.25% p.a. for 12 months, minimum PHP 50,000.","Illustrative 12-month term deposit with a PHP 50,000 minimum; no deposit is opened.","All demonstration customers.",true);
        insert("09","referral-reward","Referral Reward","SAVINGS","ALL","PHP 500 when a friend opens a funded deposit within 30 days.","Illustrative reward only; no referral or payment is submitted.","All demonstration customers.",true);
        insert("10","new-year-cashback-expired","New Year Cashback","CARDS","ALL","A past cashback promotion.","This historical campaign expired on 31 January 2026.","All demonstration customers.",false);
    }
    private void insert(String suffix,String key,String title,String category,String segment,String benefit,String terms,String eligibility,boolean active) {
        jdbc.update("INSERT INTO promotions(id,promotion_key,title,category,target_segment,benefit,terms,eligibility,valid_until,active) VALUES (?,?,?,?,?,?,?,?,?::date,?) ON CONFLICT DO NOTHING",UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix),key,title,category,segment,benefit,terms,eligibility,active?"2026-12-31":"2026-01-31",active);
    }
}
