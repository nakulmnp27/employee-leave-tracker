import java.sql.*;

public class Utils {

    static boolean hasEnoughBalance(Connection c, int empId, String type, long days) {
        try {
            String col = switch (type) {
                case "CASUAL" -> "casual";
                case "SICK" -> "sick";
                case "EARNED" -> "earned";
                default -> "wfh";
            };
            PreparedStatement ps = c.prepareStatement("SELECT " + col + " FROM leave_balances WHERE emp_id=?");
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) >= days;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    static void sendUpcomingLeaveNotification(Connection c, int empId) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM leaves WHERE emp_id=? AND status='APPROVED' AND start_date = CURRENT_DATE + INTERVAL 1 DAY")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                PreparedStatement n = c.prepareStatement(
                        "INSERT INTO notifications(emp_id,message,date) VALUES (?,?,CURRENT_DATE)");
                n.setInt(1, empId);
                n.setString(2, "Reminder: Your approved leave starts tomorrow.");
                n.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    static void sendLowBalanceNotification(Connection c, int empId) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT casual,sick,earned,wfh FROM leave_balances WHERE emp_id=?")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                StringBuilder msg = new StringBuilder("Alert: Low balance in ");
                boolean found = false;
                if (rs.getInt("casual") < 2) { msg.append("Casual"); found = true; }
                if (rs.getInt("sick") < 2) { if (found) msg.append(", "); msg.append("Sick"); found = true; }
                if (rs.getInt("earned") < 2) { if (found) msg.append(", "); msg.append("Earned"); found = true; }
                if (rs.getInt("wfh") < 2) { if (found) msg.append(", "); msg.append("WFH"); }
                if (found) {
                    msg.append(" leaves.");
                    PreparedStatement n = c.prepareStatement(
                            "INSERT INTO notifications(emp_id,message,date) VALUES (?,?,CURRENT_DATE)");
                    n.setInt(1, empId);
                    n.setString(2, msg.toString());
                    n.executeUpdate();
                }
            }
        } catch (Exception ignored) {}
    }

    static void viewDashboard() {
        try (Connection c = Database.get()) {
            System.out.println("\n--- HR Dashboard ---");

            PreparedStatement totalEmp = c.prepareStatement("SELECT COUNT(*) FROM employees WHERE role='EMP'");
            ResultSet e = totalEmp.executeQuery();
            e.next();
            System.out.println("Total Employees: " + e.getInt(1));

            PreparedStatement totalLeaves = c.prepareStatement("SELECT COUNT(*) FROM leaves");
            ResultSet t = totalLeaves.executeQuery();
            t.next();
            System.out.println("Total Leave Requests: " + t.getInt(1));

            System.out.println("\n--- Department-wise Leave Summary ---");
            PreparedStatement dept = c.prepareStatement(
                    "SELECT d.name AS department, " +
                            "SUM(CASE WHEN l.status='PENDING' THEN 1 ELSE 0 END) AS pending, " +
                            "SUM(CASE WHEN l.status='APPROVED' THEN 1 ELSE 0 END) AS approved, " +
                            "SUM(CASE WHEN l.status='REJECTED' THEN 1 ELSE 0 END) AS rejected " +
                            "FROM departments d " +
                            "LEFT JOIN employees e ON d.dept_id = e.dept_id " +
                            "LEFT JOIN leaves l ON e.emp_id = l.emp_id " +
                            "GROUP BY d.dept_id");
            ResultSet d = dept.executeQuery();
            boolean any = false;
            while (d.next()) {
                any = true;
                System.out.printf("%s - Pending: %d | Approved: %d | Rejected: %d%n",
                        d.getString("department"),
                        d.getInt("pending"),
                        d.getInt("approved"),
                        d.getInt("rejected"));
            }
            if (!any) System.out.println("No department data available.");
        } catch (Exception e) {
            System.out.println("Error loading dashboard: " + e.getMessage());
        }
    }
}
