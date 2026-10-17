package com.ledgerflow.ledger.repository;

import com.ledgerflow.ledger.dto.FlaggedReportRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Analyst queue. Rank is computed in SQL so the ordering stays correct on both
 * H2 and PostgreSQL, and columns are read by name so alias case cannot null-out
 * the payload the way a native interface projection did.
 */
@Repository
public class FlaggedReportQuery {
    private static final String SQL = """
            SELECT
                a.account_holder AS account_holder,
                t.transaction_id AS transaction_id,
                t.account_id AS account_id,
                t.amount AS amount,
                f.risk_score AS risk_score,
                f.reason AS reason,
                RANK() OVER (PARTITION BY t.account_id ORDER BY f.risk_score DESC) AS risk_rank,
                f.id AS flag_id,
                f.created_at AS created_at
            FROM fraud_flags f
            JOIN transactions t ON t.transaction_id = f.transaction_id
            JOIN accounts a ON a.account_id = t.account_id
            WHERE f.reviewed = FALSE
            ORDER BY f.risk_score DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public FlaggedReportQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FlaggedReportRow> unreviewed() {
        return jdbcTemplate.query(SQL, (rs, rowNum) -> map(rs));
    }

    private static FlaggedReportRow map(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();
        return new FlaggedReportRow(
                rs.getString("account_holder"),
                rs.getLong("transaction_id"),
                rs.getLong("account_id"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("risk_score"),
                rs.getString("reason"),
                rs.getLong("risk_rank"),
                rs.getLong("flag_id"),
                createdAt
        );
    }
}
