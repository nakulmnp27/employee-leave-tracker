import java.sql.*;

public class Database {
    static {
        try { Class.forName("org.sqlite.JDBC"); } 
        catch (ClassNotFoundException e) { System.out.println("Driver not found: " + e.getMessage()); }
    }

    static Connection get() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:data/leave_tracker.db");
    }

    public static void init() {
        try {
            new java.io.File("data").mkdirs();
            try (Connection c = get(); Statement s = c.createStatement()) {
                s.executeUpdate("PRAGMA foreign_keys = ON");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS departments(dept_id INTEGER PRIMARY KEY, name TEXT UNIQUE)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS employees(emp_id INTEGER PRIMARY KEY, name TEXT, dept_id INTEGER, role TEXT, FOREIGN KEY(dept_id) REFERENCES departments(dept_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS leave_balances(emp_id INTEGER PRIMARY KEY, casual INTEGER, sick INTEGER, earned INTEGER, wfh INTEGER, FOREIGN KEY(emp_id) REFERENCES employees(emp_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS leaves(leave_id INTEGER PRIMARY KEY AUTOINCREMENT, emp_id INTEGER, type TEXT, start_date TEXT, end_date TEXT, status TEXT, reason TEXT, manager_remark TEXT, FOREIGN KEY(emp_id) REFERENCES employees(emp_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS notifications(notif_id INTEGER PRIMARY KEY AUTOINCREMENT, emp_id INTEGER, message TEXT, date TEXT, FOREIGN KEY(emp_id) REFERENCES employees(emp_id))");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
