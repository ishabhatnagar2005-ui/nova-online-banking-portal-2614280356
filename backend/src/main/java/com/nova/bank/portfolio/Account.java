package com.nova.bank.portfolio;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Persist only the known suffix of a synthetic account identifier. */
@Entity @Table(name="accounts")
public class Account {
    @Id UUID id;
    @Column(name="owner_id", nullable=false) UUID ownerId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="identifier_suffix", nullable=false, length=4) String identifierSuffix;
    @Column(nullable=false, length=16) String type;
    @Column(nullable=false, length=120) String name;
    @Column(name="available_balance", precision=15, scale=2) BigDecimal availableBalance;
    @Column(name="current_balance", precision=15, scale=2) BigDecimal currentBalance;
    @Column(name="credit_limit", precision=15, scale=2) BigDecimal creditLimit;
    @Column(name="interest_rate", precision=5, scale=2) BigDecimal interestRate;
    @Column(nullable=false, length=16) String status;
    @Column(name="last_statement_date") LocalDate lastStatementDate;
    protected Account() {}
}
