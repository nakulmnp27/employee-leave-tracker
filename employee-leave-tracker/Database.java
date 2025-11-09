import java.sql.*;

public class Database {
    private static final String DB_NAME = "leave_tracker";
    private static final String URL_WITH_DB = "jdbc:mysql://127.0.0.1:3306/" + DB_NAME;
    private static final String URL_NO_DB = "jdbc:mysql://127.0.0.1:3306/";
    private static final String USER = "root";
    private static final String PASSWORD = "nakulmnp27@";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL JDBC driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            System.out.println("Driver not found: " + e.getMessage());
        }
    }

    // Standard connection to the database
    static Connection get() throws SQLException {
        return DriverManager.getConnection(URL_WITH_DB, USER, PASSWORD);
    }

    public static void init() {
        try {
            ensureDatabaseExists();
            try (Connection c = get(); Statement s = c.createStatement()) {
                System.out.println("Initializing MySQL tables...");

                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS departments (
                        dept_id INT PRIMARY KEY,
                        name VARCHAR(100) UNIQUE
                    )
                """);

                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employees (
                        emp_id INT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        dept_id INT,
                        role ENUM('EMP','HR') NOT NULL,
                        password VARCHAR(100) NOT NULL,
                        FOREIGN KEY (dept_id) REFERENCES departments(dept_id)
                            ON DELETE SET NULL ON UPDATE CASCADE
                    )
                """);

                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS leave_balances (
                        emp_id INT PRIMARY KEY,
                        casual INT DEFAULT 5,
                        sick INT DEFAULT 5,
                        earned INT DEFAULT 10,
                        wfh INT DEFAULT 10,
                        FOREIGN KEY (emp_id) REFERENCES employees(emp_id)
                            ON DELETE CASCADE ON UPDATE CASCADE
                    )
                """);

                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS leaves (
                        leave_id INT AUTO_INCREMENT PRIMARY KEY,
                        emp_id INT NOT NULL,
                        type ENUM('CASUAL','SICK','EARNED','WFH') NOT NULL,
                        start_date DATE NOT NULL,
                        end_date DATE NOT NULL,
                        status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
                        reason TEXT,
                        manager_remark TEXT,
                        FOREIGN KEY (emp_id) REFERENCES employees(emp_id)
                            ON DELETE CASCADE ON UPDATE CASCADE
                    )
                """);

                s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        notif_id INT AUTO_INCREMENT PRIMARY KEY,
                        emp_id INT NOT NULL,
                        message TEXT,
                        date DATE DEFAULT (CURRENT_DATE),
                        FOREIGN KEY (emp_id) REFERENCES employees(emp_id)
                            ON DELETE CASCADE ON UPDATE CASCADE
                    )
                """);

                System.out.println("All MySQL tables created successfully.");
                seedData(c);
            }
        } catch (Exception e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }

    // Check if DB exists and create if missing
    private static void ensureDatabaseExists() {
        try (Connection c = DriverManager.getConnection(URL_NO_DB, USER, PASSWORD);
             Statement s = c.createStatement()) {

            ResultSet rs = s.executeQuery("SHOW DATABASES LIKE '" + DB_NAME + "'");
            if (!rs.next()) {
                System.out.println("Database '" + DB_NAME + "' not found. Creating now...");
                s.executeUpdate("CREATE DATABASE " + DB_NAME);
                System.out.println("Database '" + DB_NAME + "' created successfully.");
            }
        } catch (Exception e) {
            System.out.println("Error ensuring database exists: " + e.getMessage());
        }
    }

    private static void seedData(Connection c) {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("INSERT IGNORE INTO departments(dept_id,name) VALUES (1,'R&D'),(2,'Development'),(3,'Testing')");
            s.executeUpdate("INSERT IGNORE INTO employees(emp_id,name,dept_id,role,password) VALUES (100,'Nakul',1,'EMP','1234'),(200,'Manager1',2,'HR','hr123')");
            s.executeUpdate("INSERT IGNORE INTO leave_balances(emp_id,casual,sick,earned,wfh) VALUES (100,5,5,10,10)");
        } catch (Exception e) {
            System.out.println("Error inserting seed data: " + e.getMessage());
        }
    }
}
