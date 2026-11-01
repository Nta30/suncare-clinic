
package com.suncare.clinic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class SqlServerConnectivityIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConnectToSuncareClinic() throws Exception {

        String sql = """
            SELECT
                DB_NAME() AS database_name,
                (SELECT COUNT(*)
                 FROM sys.tables
                 WHERE is_ms_shipped = 0
                 AND name <> 'flyway_schema_history') AS table_count
            """;

        try (
                Connection connection =
                        dataSource.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet result = statement.executeQuery()
        ) {
            assertTrue(result.next());

            String database = result.getString(
                    "database_name"
            );

            int tableCount = result.getInt(
                    "table_count"
            );

            System.out.println(
                    "DATABASE: " + database
            );

            System.out.println(
                    "TABLE COUNT: " + tableCount
            );

            assertEquals(
                    "SuncareClinic",
                    database
            );

            assertEquals(
                    58,
                    tableCount
            );
        }
    }
}
