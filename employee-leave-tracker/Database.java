import java.sql.*;

public class Database {
    private static boolean initialized = false;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite JDBC driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            System.out.println("Driver not found: " + e.getMessage());
        }
    }

    static Connection get() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:data/leave_tracker.db");
        if (!initialized) {
            System.out.println("Database connected successfully.");
            initialized = true;
        }
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA journal_mode = WAL");
            s.execute("PRAGMA synchronous = NORMAL");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    public static void init() {
        try {
            new java.io.File("data").mkdirs();
            try (Connection c = get(); Statement s = c.createStatement()) {
                System.out.println("Initializing database tables...");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS departments(dept_id INTEGER PRIMARY KEY, name TEXT UNIQUE)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS employees(emp_id INTEGER PRIMARY KEY, name TEXT, dept_id INTEGER, role TEXT, password TEXT, FOREIGN KEY(dept_id) REFERENCES departments(dept_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS leave_balances(emp_id INTEGER PRIMARY KEY, casual INTEGER, sick INTEGER, earned INTEGER, wfh INTEGER, FOREIGN KEY(emp_id) REFERENCES employees(emp_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS leaves(leave_id INTEGER PRIMARY KEY AUTOINCREMENT, emp_id INTEGER, type TEXT, start_date TEXT, end_date TEXT, status TEXT, reason TEXT, manager_remark TEXT, FOREIGN KEY(emp_id) REFERENCES employees(emp_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS notifications(notif_id INTEGER PRIMARY KEY AUTOINCREMENT, emp_id INTEGER, message TEXT, date TEXT, FOREIGN KEY(emp_id) REFERENCES employees(emp_id))");
                System.out.println("Tables verified/created successfully.");
            }
        } catch (Exception e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }
}
