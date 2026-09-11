package com.carddemo.pendingauth.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V901 converts auth_fraud.pos_entry_mode from the pre-V900 CHAR(2) to the
 * SMALLINT of CARDDEMO.AUTHFRDS. It runs against tables that already hold
 * authorization history, so the conversion has to carry the existing entry
 * modes across rather than rebuild the column empty (decision D-11).
 */
class PosEntryModeUpgradeTest {

    @Test
    void theH2MigrationCarriesExistingEntryModesAcross() throws Exception {
        String migration = new String(
                new ClassPathResource("db/vendor/h2/V901__auth_fraud_pos_entry_mode_smallint.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:pos_entry_mode_upgrade;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE auth_fraud ("
                    + "auth_key VARCHAR(19) PRIMARY KEY, pos_entry_mode CHAR(2))");
            statement.execute("INSERT INTO auth_fraud VALUES ('4111111111111111001', '05')");
            statement.execute("INSERT INTO auth_fraud VALUES ('4111111111111111002', '90')");

            statement.execute(migration);

            assertThat(columnType(connection)).isEqualTo("SMALLINT");
            assertThat(entryModes(statement)).containsExactly(5, 90);
        }
    }

    private String columnType(Connection connection) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(null, null, "AUTH_FRAUD", "POS_ENTRY_MODE")) {
            assertThat(columns.next()).isTrue();
            return columns.getString("TYPE_NAME");
        }
    }

    private List<Integer> entryModes(Statement statement) throws Exception {
        List<Integer> modes = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery(
                "SELECT pos_entry_mode FROM auth_fraud ORDER BY auth_key")) {
            while (rows.next()) {
                modes.add(rows.getInt(1));
            }
        }
        return modes;
    }
}
