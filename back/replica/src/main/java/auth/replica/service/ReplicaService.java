package auth.replica.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReplicaService {

    @Autowired
    @Qualifier("customerDataSource")
    private DataSource customerDataSource;

    @Autowired
    @Qualifier("localDataSource")
    private DataSource localDataSource;

    public void syncReplicaSchema() throws SQLException {
        try (Connection customerConn = customerDataSource.getConnection();
             Connection localConn = localDataSource.getConnection();
             Statement localStmt = localConn.createStatement()) {

            DatabaseMetaData metaData = customerConn.getMetaData();
            DatabaseMetaData localMetaData = localConn.getMetaData();
            String catalog = customerConn.getCatalog();
            String local_catalog = localConn.getCatalog();

            ResultSet local_tables = localConn.getMetaData().getTables(local_catalog, "public", "%", new String[]{"TABLE"});
            Map<String, ArrayList<String>> localTablesMap = new HashMap<>();

            while (local_tables.next()) {
                String tableName = local_tables.getString("TABLE_NAME");
                ResultSet columns = localMetaData.getColumns(local_catalog, "public", tableName, "%");
                ArrayList<String> columnNames = new ArrayList<>();
                while(columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    System.out.println(columnName);
                    columnNames.add(columnName);
                }
                localTablesMap.put(tableName, columnNames);
            }
            ResultSet tables = metaData.getTables(catalog, "public", "%", new String[]{"TABLE"});
            
            while (tables.next()) {

                String tableName = tables.getString("TABLE_NAME");
                boolean isAlter = false;
                if (localTablesMap.containsKey(tableName)) {
                    isAlter = true;
                }
                StringBuilder createTableSql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (");
                StringBuilder alterTableSql = new StringBuilder("ALTER TABLE " + tableName + " ADD COLUMN ");


                ResultSet columns = metaData.getColumns(catalog, "public", tableName, "%");
                boolean firstColumn = true;
                // System.out.println("Get into while loop: " + tableName);
                while (columns.next()) {

                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    int columnSize = columns.getInt("COLUMN_SIZE");
                    if (!isAlter) {
                        if (!firstColumn) createTableSql.append(", ");
                        System.out.println("Column: " + columnName + " " + columnType + " " + columnSize + " " + "ok");
                        createTableSql.append(columnName).append(" ").append(mapColumnType(columnType, columnSize));
                        firstColumn = false;
                    } else {
                        System.out.println("Check column: " + columnName + " inside Alter logic");
                        // for(String col: localTablesMap.get(tableName)) {
                            System.out.println(localTablesMap.get(tableName).size());
                        // }
                        if (!localTablesMap.get(tableName).contains(columnName)) {
                            System.out.println("Column: " + columnName + " " + columnType + " " + columnSize + " " + "ok");
                            if(!firstColumn){
                                alterTableSql.append(", ");
                                alterTableSql.append("ADD COLUMN ");
                            } 
                            
                            alterTableSql.append(columnName).append(" ").append(mapColumnType(columnType, columnSize));
                            firstColumn = false;
                        }
                    }
                    
                }
                if (isAlter) {
                    // alterTableSql.deleteCharAt(alterTableSql.length() - 2);
                    System.out.println("check before altering: " + tableName);
                    System.out.println(alterTableSql.toString());
                    localStmt.execute(alterTableSql.toString());
                    System.out.println("Table altered: " + tableName);
                }else {
                    System.out.println("check before creating: " + tableName);
                    createTableSql.append(")");
                    System.out.print(createTableSql.toString());
                    localStmt.execute(createTableSql.toString());
                    System.out.println("Created table: " + tableName);
                }
                
            }
        }
    }

    public void copyInitialData() throws SQLException {
        try (Connection customerConn = customerDataSource.getConnection();
             Connection localConn = localDataSource.getConnection()) {

            DatabaseMetaData metaData = customerConn.getMetaData();
            String catalog = customerConn.getCatalog();

            ResultSet tables = metaData.getTables(catalog, "public", "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                Statement customerStmt = customerConn.createStatement();
                ResultSet rs = customerStmt.executeQuery(
                    "SELECT * FROM " + tableName );
                

                ResultSetMetaData rsMeta = rs.getMetaData();
                PreparedStatement localStmt = localConn.prepareStatement(buildInsertSql(tableName, rsMeta));

                while (rs.next()) {
                    for (int i = 1; i <= rsMeta.getColumnCount(); i++) {
                        localStmt.setObject(i, rs.getObject(i));
                    }
                    localStmt.executeUpdate();
                }
            }
        }
    }

    @Scheduled(fixedRate = 5000)
    public void syncData() throws SQLException {
        copyInitialData();
        System.out.println("Data synchronized");
    }

    private String buildInsertSql(String tableName, ResultSetMetaData rsMeta) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " (");
        int columnCount = rsMeta.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            sql.append(rsMeta.getColumnName(i));
            if (i < columnCount) sql.append(", ");
        }
        sql.append(") VALUES (");

        for (int i = 1; i <= columnCount; i++) {
            sql.append("?");
            if (i < columnCount) sql.append(", ");
        }
        sql.append(")");

        return sql.toString();
    }

    private String mapColumnType(String columnType, int columnSize) {
        switch (columnType.toUpperCase()) {
            case "VARCHAR":
            case "CHAR":
                return columnType + "(" + columnSize + ")";
            case "TEXT":
            case "UUID":
            case "BOOLEAN":
            case "INTEGER":
            case "BIGINT":
            case "SMALLINT":
            case "SERIAL":
            case "BIGSERIAL":
            case "REAL":
            case "DOUBLE PRECISION":
            case "DECIMAL":
            case "NUMERIC":
                return columnType;
            case "JSON":
            case "JSONB":
                return "TEXT"; // Force using JSONB type for better performance
            default:
                return "TEXT"; // Fallback to TEXT for unknown types
        }
    }
}
