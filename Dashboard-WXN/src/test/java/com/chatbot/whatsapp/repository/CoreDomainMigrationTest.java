package com.chatbot.whatsapp.repository;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CoreDomainMigrationTest {
    @Test
    void migratesLegacyAssignmentsAndPreservesClosedAttendance() throws Exception {
        String url = "jdbc:h2:mem:legacy_core_domain;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "sa").target("4").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "sa");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO customers (phone_number) VALUES ('551188776655')");
            statement.executeUpdate("""
                    INSERT INTO conversations (customer_id, status, channel, assigned_to, assigned_at, closed_at)
                    SELECT id, 'CLOSED', 'WHATSAPP', ' Carlos ', TIMESTAMP '2026-09-01 10:00:00',
                        TIMESTAMP '2026-09-01 11:00:00' FROM customers
                    """);
            statement.executeUpdate("""
                    INSERT INTO conversations (customer_id, status, channel, assigned_to)
                    SELECT id, 'HUMAN_ACTIVE', 'WHATSAPP', 'Carlos' FROM customers
                    """);
            Flyway.configure().dataSource(url, "sa", "sa").load().migrate();
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM attendants")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM attendances")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery("""
                    SELECT a.started_at, a.closed_at, c.chatbot_id FROM attendances a
                    JOIN conversations c ON c.id = a.conversation_id WHERE c.status = 'CLOSED'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getTimestamp(1).toLocalDateTime().toString()).isEqualTo("2026-09-01T10:00");
                assertThat(result.getTimestamp(2).toLocalDateTime().toString()).isEqualTo("2026-09-01T11:00");
                assertThat(result.getLong(3)).isEqualTo(1L);
            }
        }
    }
}
