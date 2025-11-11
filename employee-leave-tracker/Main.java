import java.sql.*;
import java.time.LocalDate;
import java.util.Scanner;

public class Main {
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        Database.init();

        while (true) {
            System.out.println("\n--- Leave Management System ---");
            System.out.println("1. Employee Login");
            System.out.println("2. HR Login");
            System.out.println("3. Exit");
            System.out.print("Choose: ");
            String ch = sc.nextLine().trim();

            if (ch.equals("1")) employeeLogin();
            else if (ch.equals("2")) hrLogin();
            else if (ch.equals("3")) {
                System.out.println("Exiting system...");
                break;
            }
        }
    }

    static void employeeLogin() {
        System.out.print("Enter Employee ID: ");
        int empId = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Password: ");
        String pass = sc.nextLine().trim();

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT name, password FROM employees WHERE emp_id=? AND role='EMP'")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                String dbPass = rs.getString("password");

                if (dbPass.equals(pass)) {
                    sendUpcomingLeaveNotification(c, empId);
                    sendLowBalanceNotification(c, empId);
                    System.out.println("\nWelcome, " + name);
                    employeeMenu(empId);
                } else System.out.println("Invalid password.");
            } else System.out.println("Invalid employee ID.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void employeeMenu(int empId) {
        while (true) {
            System.out.println("\n--- Employee Menu ---");
            System.out.println("1. Apply Leave");
            System.out.println("2. View Notifications");
            System.out.println("3. View Leave Balance");
            System.out.println("4. Leave History");
            System.out.println("5. Logout / Exit");
            System.out.print("Choose: ");
            String ch = sc.nextLine().trim();

            if (ch.equals("1")) apply(empId);
            else if (ch.equals("2")) viewNotifications(empId);
            else if (ch.equals("3")) viewBalance(empId);
            else if (ch.equals("4")) leaveHistory(empId);
            else if (ch.equals("5")) {
                System.out.println("Logging out...");
                break;
            }
        }
    }

    static void apply(int empId) {
        try (Connection c = Database.get()) {
            while (true) {
                System.out.print("Type (CASUAL/SICK/EARNED/WFH): ");
                String type = sc.nextLine().trim().toUpperCase();
                System.out.print("Start (YYYY-MM-DD): ");
                LocalDate start = LocalDate.parse(sc.nextLine().trim());
                System.out.print("End (YYYY-MM-DD): ");
                LocalDate end = LocalDate.parse(sc.nextLine().trim());
                LocalDate today = LocalDate.now();

                if (start.isBefore(today)) {
                    System.out.println("Invalid date! Leave cannot start before today's date.");
                    continue;
                }
                if (end.isBefore(start)) {
                    System.out.println("End date must be after or same as start date.");
                    continue;
                }

                PreparedStatement chk = c.prepareStatement(
                        "SELECT COUNT(*) FROM leaves WHERE emp_id=? AND status IN ('PENDING','APPROVED') AND (start_date <= ? AND end_date >= ?)");
                chk.setInt(1, empId);
                chk.setDate(2, Date.valueOf(end));
                chk.setDate(3, Date.valueOf(start));
                ResultSet overlap = chk.executeQuery();
                overlap.next();
                if (overlap.getInt(1) > 0) {
                    System.out.println("You already have a leave request during this period.");
                    continue;
                }

                long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
                if (!hasEnoughBalance(c, empId, type, days)) {
                    System.out.println("Insufficient balance.");
                    continue;
                }

                System.out.print("Reason: ");
                String reason = sc.nextLine().trim();

                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO leaves(emp_id,type,start_date,end_date,status,reason) VALUES (?,?,?,?,?,?)");
                ps.setInt(1, empId);
                ps.setString(2, type);
                ps.setDate(3, Date.valueOf(start));
                ps.setDate(4, Date.valueOf(end));
                ps.setString(5, "PENDING");
                ps.setString(6, reason);
                ps.executeUpdate();

                System.out.println("Leave request submitted successfully.");
                break;
            }
        } catch (Exception e) {
            System.out.println("Error applying leave: " + e.getMessage());
        }
    }

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

    static void viewNotifications(int empId) {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT message, date FROM notifications WHERE emp_id=? ORDER BY notif_id DESC")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            System.out.println("\n--- Notifications ---");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.println(rs.getDate("date") + " - " + rs.getString("message"));
            }
            if (!any) System.out.println("No notifications yet.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void viewBalance(int empId) {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT casual,sick,earned,wfh FROM leave_balances WHERE emp_id=?")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("\n--- Leave Balance ---");
                System.out.printf("Casual: %d | Sick: %d | Earned: %d | WFH: %d%n",
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void leaveHistory(int empId) {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT type,start_date,end_date,status,manager_remark FROM leaves WHERE emp_id=? ORDER BY start_date DESC")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            System.out.println("\n--- Leave History ---");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("%s | %s to %s | %s | %s%n",
                        rs.getString("type"), rs.getDate("start_date"), rs.getDate("end_date"),
                        rs.getString("status"), rs.getString("manager_remark"));
            }
            if (!any) System.out.println("No leave history found.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void hrLogin() {
        System.out.print("Enter HR ID: ");
        int empId = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Password: ");
        String pass = sc.nextLine().trim();

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement("SELECT name FROM employees WHERE emp_id=? AND role='HR' AND password=?")) {
            ps.setInt(1, empId);
            ps.setString(2, pass);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("\nWelcome, " + rs.getString("name"));
                hrMenu();
            } else {
                System.out.println("Invalid credentials.");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void hrMenu() {
        while (true) {
            System.out.println("\n--- HR Menu ---");
            System.out.println("1. Approve/Reject Leaves");
            System.out.println("2. Add New Employee");
            System.out.println("3. Remove Employee");
            System.out.println("4. Dashboard");
            System.out.println("5. Logout / Exit");
            System.out.print("Choose: ");
            String ch = sc.nextLine().trim();

            if (ch.equals("1")) decide();
            else if (ch.equals("2")) addEmployee();
            else if (ch.equals("3")) removeEmployee();
            else if (ch.equals("4")) showDashboard();
            else if (ch.equals("5")) break;
        }
    }

    static void decide() {
        try (Connection c = Database.get()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT leave_id, emp_id, type, start_date, end_date FROM leaves WHERE status='PENDING'");
                 ResultSet rs = ps.executeQuery()) {
                System.out.println("\nPending Requests:");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("%d | Emp %d | %s | %s to %s%n",
                            rs.getInt("leave_id"), rs.getInt("emp_id"),
                            rs.getString("type"), rs.getDate("start_date"), rs.getDate("end_date"));
                }
                if (!any) {
                    System.out.println("No pending requests.");
                    return;
                }
            }

            System.out.print("Enter leave_id to decide: ");
            int leaveId = Integer.parseInt(sc.nextLine().trim());
            int empId = -1;

            try (PreparedStatement ps2 = c.prepareStatement("SELECT emp_id FROM leaves WHERE leave_id=?")) {
                ps2.setInt(1, leaveId);
                ResultSet r2 = ps2.executeQuery();
                if (r2.next()) empId = r2.getInt("emp_id");
            }

            if (empId == -1) {
                System.out.println("Invalid leave ID.");
                return;
            }

            viewBalance(empId);
            leaveHistory(empId);

            System.out.print("\nDecision (APPROVED/REJECTED): ");
            String decision = sc.nextLine().trim().toUpperCase();
            if (!decision.equals("APPROVED") && !decision.equals("REJECTED")) {
                System.out.println("Invalid input.");
                return;
            }

            System.out.print("Manager remark: ");
            String remark = sc.nextLine().trim();

            c.setAutoCommit(false);
            try (PreparedStatement upd = c.prepareStatement("UPDATE leaves SET status=?, manager_remark=? WHERE leave_id=?")) {
                upd.setString(1, decision);
                upd.setString(2, remark);
                upd.setInt(3, leaveId);
                upd.executeUpdate();
            }

            if (decision.equals("APPROVED")) {
                try (PreparedStatement ps2 = c.prepareStatement("SELECT type,start_date,end_date FROM leaves WHERE leave_id=?")) {
                    ps2.setInt(1, leaveId);
                    ResultSet r2 = ps2.executeQuery();
                    if (r2.next()) {
                        String type = r2.getString("type");
                        LocalDate s = r2.getDate("start_date").toLocalDate();
                        LocalDate e = r2.getDate("end_date").toLocalDate();
                        long days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
                        String col = switch (type) {
                            case "CASUAL" -> "casual";
                            case "SICK" -> "sick";
                            case "EARNED" -> "earned";
                            default -> "wfh";
                        };
                        try (PreparedStatement dec = c.prepareStatement("UPDATE leave_balances SET " + col + " = " + col + " - ? WHERE emp_id=?")) {
                            dec.setLong(1, days);
                            dec.setInt(2, empId);
                            dec.executeUpdate();
                        }
                    }
                }
            }

            try (PreparedStatement notif = c.prepareStatement("INSERT INTO notifications(emp_id,message,date) VALUES (?,?,CURRENT_DATE)")) {
                notif.setInt(1, empId);
                notif.setString(2, "Leave " + decision.toLowerCase());
                notif.executeUpdate();
            }

            c.commit();
            c.setAutoCommit(true);
            System.out.println("Decision saved successfully.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // ✅ UPDATED DEPARTMENT-WISE DASHBOARD
    static void showDashboard() {
        try (Connection c = Database.get()) {
            System.out.println("\n--- HR Dashboard ---");

            String total = "SELECT COUNT(*) FROM leaves";
            String pending = "SELECT COUNT(*) FROM leaves WHERE status='PENDING'";
            String approved = "SELECT COUNT(*) FROM leaves WHERE status='APPROVED'";
            String rejected = "SELECT COUNT(*) FROM leaves WHERE status='REJECTED'";

            System.out.printf("Total Requests: %d | Pending: %d | Approved: %d | Rejected: %d%n",
                    getCount(c, total), getCount(c, pending), getCount(c, approved), getCount(c, rejected));

            System.out.println("\n--- Department-wise Leave Requests ---\n");

            PreparedStatement ps = c.prepareStatement("""
                SELECT d.name AS dept,
                       COUNT(l.leave_id) AS total,
                       SUM(CASE WHEN l.status='APPROVED' THEN 1 ELSE 0 END) AS approved,
                       SUM(CASE WHEN l.status='REJECTED' THEN 1 ELSE 0 END) AS rejected,
                       SUM(CASE WHEN l.status='PENDING' THEN 1 ELSE 0 END) AS pending
                FROM departments d
                LEFT JOIN employees e ON d.dept_id = e.dept_id
                LEFT JOIN leaves l ON e.emp_id = l.emp_id
                GROUP BY d.name
                ORDER BY d.name
            """);

            ResultSet rs = ps.executeQuery();
            boolean any = false;
            while (rs.next()) {
                any = true;
                String dept = rs.getString("dept");
                int totalCount = rs.getInt("total");
                int approvedCount = rs.getInt("approved");
                int rejectedCount = rs.getInt("rejected");
                int pendingCount = rs.getInt("pending");

                System.out.printf("%-12s :  Total: %-3d | Approved: %-3d | Rejected: %-3d | Pending: %-3d%n",
                        dept, totalCount, approvedCount, rejectedCount, pendingCount);
            }
            if (!any)
                System.out.println("No department data available.");
        } catch (Exception e) {
            System.out.println("Error showing dashboard: " + e.getMessage());
        }
    }

    static int getCount(Connection c, String query) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(query)) {
            r.next();
            return r.getInt(1);
        }
    }

    static void sendUpcomingLeaveNotification(Connection c, int empId) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM leaves WHERE emp_id=? AND status='APPROVED' AND start_date = CURRENT_DATE + INTERVAL 1 DAY")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                PreparedStatement n = c.prepareStatement("INSERT INTO notifications(emp_id,message,date) VALUES (?,?,CURRENT_DATE)");
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

    static void addEmployee() {
        try (Connection c = Database.get()) {
            int nextId = 101;
            try (PreparedStatement ps = c.prepareStatement("SELECT MAX(emp_id) FROM employees WHERE role='EMP'")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) nextId = rs.getInt(1) + 1;
            }

            System.out.println("New Employee ID: " + nextId);
            System.out.print("Name: ");
            String name = sc.nextLine().trim();

            System.out.println("Choose Department:");
            System.out.println("1. R&D");
            System.out.println("2. Development");
            System.out.println("3. Testing");
            System.out.print("Select: ");
            int deptChoice = Integer.parseInt(sc.nextLine().trim());
            int deptId = (deptChoice >= 1 && deptChoice <= 3) ? deptChoice : 1;

            System.out.print("Set password for employee: ");
            String empPass = sc.nextLine().trim();

            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO employees(emp_id,name,dept_id,role,password) VALUES (?,?,?,?,?)");
            ps.setInt(1, nextId);
            ps.setString(2, name);
            ps.setInt(3, deptId);
            ps.setString(4, "EMP");
            ps.setString(5, empPass);
            ps.executeUpdate();

            PreparedStatement bal = c.prepareStatement(
                    "INSERT INTO leave_balances(emp_id,casual,sick,earned,wfh) VALUES (?,?,?,?,?)");
            bal.setInt(1, nextId);
            bal.setInt(2, 5);
            bal.setInt(3, 5);
            bal.setInt(4, 10);
            bal.setInt(5, 10);
            bal.executeUpdate();

            System.out.println("New employee added successfully. ID: " + nextId);
        } catch (Exception e) {
            System.out.println("Error adding employee: " + e.getMessage());
        }
    }

    static void removeEmployee() {
        try (Connection c = Database.get()) {
            System.out.print("Enter Employee ID to remove: ");
            int id = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement delEmp = c.prepareStatement("DELETE FROM employees WHERE emp_id=? AND role='EMP'");
            delEmp.setInt(1, id);
            int rows = delEmp.executeUpdate();

            if (rows > 0)
                System.out.println("Employee removed successfully.");
            else
                System.out.println("Employee ID not found or cannot remove HR accounts.");
        } catch (Exception e) {
            System.out.println("Error removing employee: " + e.getMessage());
        }
    }
}
