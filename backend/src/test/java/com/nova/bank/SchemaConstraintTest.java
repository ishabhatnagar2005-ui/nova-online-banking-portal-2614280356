package com.nova.bank;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verify Flyway constraints on PostgreSQL, rolling back all test fixtures. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@org.springframework.test.context.ContextConfiguration(initializers=DisposableDatabaseGuard.class)
class SchemaConstraintTest {
    @Autowired DataSource dataSource;

    void sql(Connection c,String sql,Object... values) throws SQLException {
        try (var statement=c.prepareStatement(sql)) {
            for (int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            statement.executeUpdate();
        }
    }
    int count(Connection c,String sql,Object id) throws SQLException {
        try (var statement=c.prepareStatement(sql)) {
            statement.setObject(1,id);
            try (var rows=statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }
    void rejected(Connection c,String state,String sql,Object... values) throws SQLException {
        var savepoint=c.setSavepoint();
        assertThatThrownBy(()->sql(c,sql,values)).isInstanceOf(SQLException.class)
            .extracting(e->((SQLException)e).getSQLState()).isEqualTo(state);
        c.rollback(savepoint);
    }
    @Test void foreignKeysUniqueNotNullAndChecksAreEnforced() throws Exception {
        try (Connection c=dataSource.getConnection()) {
            assertThat(c.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            c.setAutoCommit(false);
            try {
                UUID user=UUID.randomUUID(); String username="constraint-"+user;
                sql(c,"insert into bank_users(id,username,password_hash,full_name,role) values (?,?, 'hash','Test','CUSTOMER')",user,username);
                rejected(c,"23505","insert into bank_users(id,username,password_hash,full_name,role) values (?,?, 'hash','Test','CUSTOMER')",UUID.randomUUID(),username);
                rejected(c,"23502","insert into bank_users(id,username,password_hash,full_name,role) values (?,null,'hash','Test','CUSTOMER')",UUID.randomUUID());
                rejected(c,"23514","insert into bank_users(id,username,password_hash,full_name,role) values (?,?,'hash','Test','INVALID')",UUID.randomUUID(),"bad-"+user);
                rejected(c,"23503","insert into auth_sessions(id,user_id,last_activity,expires_at) values (?,?,now(),now())",UUID.randomUUID(),UUID.randomUUID());
                rejected(c,"23503","insert into promotion_interests values (?,?,?,now())",UUID.randomUUID(),user,UUID.randomUUID());
                sql(c,"insert into support_inquiries values (?,?, 'topic','subject','message',?,now())",UUID.randomUUID(),user,user.toString());
                rejected(c,"23505","insert into support_inquiries values (?,?, 'topic','subject','message',?,now())",UUID.randomUUID(),user,user.toString());
                rejected(c,"23503","delete from bank_users where id=?",user);
            } finally { c.rollback(); }
        }
    }
    @Test void deletingOwnerCascadesSessionsAccountsAndTransactionsAndNullsAuditOwner() throws Exception {
        try (Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID user=UUID.randomUUID(), account=UUID.randomUUID(), session=UUID.randomUUID(), transaction=UUID.randomUUID(), audit=UUID.randomUUID();
                sql(c,"insert into bank_users(id,username,password_hash,full_name,role) values (?,?, 'hash','Test','CUSTOMER')",user,"cascade-"+user);
                sql(c,"insert into auth_sessions(id,user_id,last_activity,expires_at) values (?,?,now(),now())",session,user);
                sql(c,"insert into auth_audit(id,user_id,event,occurred_at) values (?,?,'TEST',now())",audit,user);
                sql(c,"insert into accounts(id,owner_id,identifier_suffix,type,name,available_balance,status) values (?,?,'1234','DEPOSIT','Test',1,'ACTIVE')",account,user);
                sql(c,"insert into account_transactions(id,account_id,posted_at,description,category,direction,status,amount,reference) values (?,?,now(),'Test','GENERAL','DEBIT','POSTED',1,?)",transaction,account,"ref-"+transaction);
                assertThat(count(c,"select count(*) from account_transactions where id=?",transaction)).isEqualTo(1);
                sql(c,"delete from bank_users where id=?",user);
                assertThat(count(c,"select count(*) from auth_sessions where id=?",session)).isZero();
                assertThat(count(c,"select count(*) from accounts where id=?",account)).isZero();
                assertThat(count(c,"select count(*) from account_transactions where id=?",transaction)).isZero();
                assertThat(count(c,"select count(*) from auth_audit where id=? and user_id is null",audit)).isEqualTo(1);
            } finally { c.rollback(); }
        }
    }
}
