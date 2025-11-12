import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

                long days = ChronoUnit.DAYS.between(start, end) + 1;
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
             PreparedStatement ps = c.prepareStatement(
                     "SELECT type,start_date,end_date,status,manager_remark,reason FROM leaves WHERE emp_id=? ORDER BY start_date DESC")) {
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
            System.out.println("\n--- Leave History ---");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("%s | %s to %s | %s | %s | Reason: %s%n",
                        rs.getString("type"), rs.getDate("start_date"), rs.getDate("end_date"),
                        rs.getString("status"), rs.getString("manager_remark"),
                        rs.getString("reason"));
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
            System.out.println("4. View Dashboard");
            System.out.println("5. Reset Employee Leave Balance");
            System.out.println("6. Download Dashboard Report (CSV)");
            System.out.println("7. Download Employee Leave Reports (CSV)");
            System.out.println("8. Logout");
            System.out.print("Choose: ");
            String ch = sc.nextLine().trim();

            if (ch.equals("1")) approveRejectLeaves();
            else if (ch.equals("2")) addEmployee();
            else if (ch.equals("3")) removeEmployee();
            else if (ch.equals("4")) viewDashboard();
            else if (ch.equals("5")) resetLeaveBalance();
            else if (ch.equals("6")) exportDashboardCSV();
            else if (ch.equals("7")) exportEmployeeLeaveCSV();
            else if (ch.equals("8")) break;
        }
    }

    static void approveRejectLeaves() {
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT l.leave_id, e.name, l.emp_id, l.type, l.start_date, l.end_date, l.reason " +
                             "FROM leaves l JOIN employees e ON l.emp_id = e.emp_id WHERE l.status='PENDING'")) {
            ResultSet rs = ps.executeQuery();
            boolean any = false;
            while (rs.next()) {
                any = true;
                int leaveId = rs.getInt("leave_id");
                int empId = rs.getInt("emp_id");
                String empName = rs.getString("name");
                String type = rs.getString("type");
                LocalDate start = rs.getDate("start_date").toLocalDate();
                LocalDate end = rs.getDate("end_date").toLocalDate();
                String reason = rs.getString("reason");

                long days = ChronoUnit.DAYS.between(start, end) + 1;
                System.out.printf("\nLeave ID: %d | Employee: %s (ID %d)\nType: %s | %s to %s | Days: %d\nReason: %s\n",
                        leaveId, empName, empId, type, start, end, days, reason);
                System.out.print("Approve (A) / Reject (R) / Skip (S): ");
                String choice = sc.nextLine().trim().toUpperCase();

                if (choice.equals("A") || choice.equals("R")) {
                    System.out.print("Remark: ");
                    String remark = sc.nextLine().trim();
                    String status = choice.equals("A") ? "APPROVED" : "REJECTED";

                    PreparedStatement upd = c.prepareStatement(
                            "UPDATE leaves SET status=?, manager_remark=? WHERE leave_id=?");
                    upd.setString(1, status);
                    upd.setString(2, remark);
                    upd.setInt(3, leaveId);
                    upd.executeUpdate();

                    if (status.equals("APPROVED")) {
                        PreparedStatement bal = c.prepareStatement(
                                "UPDATE leave_balances SET " + type.toLowerCase() + " = " + type.toLowerCase() + " - ? WHERE emp_id=?");
                        bal.setLong(1, days);
                        bal.setInt(2, empId);
                        bal.executeUpdate();
                    }

                    PreparedStatement notif = c.prepareStatement(
                            "INSERT INTO notifications(emp_id,message,date) VALUES (?,?,CURRENT_DATE)");
                    notif.setInt(1, empId);
                    notif.setString(2, "Your " + type + " leave from " + start + " to " + end + " has been " + status.toLowerCase() + ".");
                    notif.executeUpdate();

                    System.out.println("Leave " + status.toLowerCase() + " successfully for " + empName + ".");
                }
            }
            if (!any) System.out.println("No pending leaves found.");
        } catch (Exception e) {
            System.out.println("Error approving/rejecting leaves: " + e.getMessage());
        }
    }

    static void addEmployee() {
        try (Connection c = Database.get()) {
            PreparedStatement maxId = c.prepareStatement("SELECT IFNULL(MAX(emp_id),1000) FROM employees WHERE role='EMP'");
            ResultSet r = maxId.executeQuery();
            r.next();
            int newId = r.getInt(1) + 1;

            System.out.println("\nAssigning Employee ID: " + newId);
            System.out.print("Name: ");
            String name = sc.nextLine().trim();
            System.out.print("Password: ");
            String password = sc.nextLine().trim();

            System.out.println("\nSelect Department:");
            PreparedStatement deptList = c.prepareStatement("SELECT dept_id, name FROM departments");
            ResultSet ds = deptList.executeQuery();
            while (ds.next()) {
                System.out.println(ds.getInt("dept_id") + ". " + ds.getString("name"));
            }
            System.out.print("Enter Department ID: ");
            int deptId = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO employees(emp_id,name,password,role,dept_id) VALUES (?,?,?,?,?)");
            ps.setInt(1, newId);
            ps.setString(2, name);
            ps.setString(3, password);
            ps.setString(4, "EMP");
            ps.setInt(5, deptId);
            ps.executeUpdate();

            System.out.println("Set initial leave balances:");
            System.out.print("Casual: ");
            int casual = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Sick: ");
            int sick = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Earned: ");
            int earned = Integer.parseInt(sc.nextLine().trim());
            System.out.print("WFH: ");
            int wfh = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement lb = c.prepareStatement(
                    "INSERT INTO leave_balances(emp_id,casual,sick,earned,wfh) VALUES (?,?,?,?,?)");
            lb.setInt(1, newId);
            lb.setInt(2, casual);
            lb.setInt(3, sick);
            lb.setInt(4, earned);
            lb.setInt(5, wfh);
            lb.executeUpdate();

            System.out.println("Employee added successfully with ID: " + newId);
        } catch (Exception e) {
            System.out.println("Error adding employee: " + e.getMessage());
        }
    }

    static void removeEmployee() {
        try (Connection c = Database.get()) {
            System.out.print("Employee ID to remove: ");
            int empId = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement chk = c.prepareStatement("SELECT role FROM employees WHERE emp_id=? AND role='EMP'");
            chk.setInt(1, empId);
            ResultSet rs = chk.executeQuery();
            if (!rs.next()) {
                System.out.println("Employee not found or not an EMP role.");
                return;
            }

            PreparedStatement delNotif = c.prepareStatement("DELETE FROM notifications WHERE emp_id=?");
            delNotif.setInt(1, empId);
            delNotif.executeUpdate();

            PreparedStatement delLeaves = c.prepareStatement("DELETE FROM leaves WHERE emp_id=?");
            delLeaves.setInt(1, empId);
            delLeaves.executeUpdate();

            PreparedStatement delLB = c.prepareStatement("DELETE FROM leave_balances WHERE emp_id=?");
            delLB.setInt(1, empId);
            delLB.executeUpdate();

            PreparedStatement delEmp = c.prepareStatement("DELETE FROM employees WHERE emp_id=?");
            delEmp.setInt(1, empId);
            delEmp.executeUpdate();

            System.out.println("Employee removed successfully.");
        } catch (Exception e) {
            System.out.println("Error removing employee: " + e.getMessage());
        }
    }

    static void resetLeaveBalance() {
        try (Connection c = Database.get()) {
            System.out.print("Enter Employee ID to reset: ");
            int empId = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement check = c.prepareStatement("SELECT * FROM leave_balances WHERE emp_id=?");
            check.setInt(1, empId);
            ResultSet rs = check.executeQuery();

            if (!rs.next()) {
                System.out.println("Employee not found or leave balance not set.");
                return;
            }

            System.out.print("Set Casual leaves: ");
            int casual = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Set Sick leaves: ");
            int sick = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Set Earned leaves: ");
            int earned = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Set WFH leaves: ");
            int wfh = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement ps = c.prepareStatement(
                    "UPDATE leave_balances SET casual=?, sick=?, earned=?, wfh=? WHERE emp_id=?");
            ps.setInt(1, casual);
            ps.setInt(2, sick);
            ps.setInt(3, earned);
            ps.setInt(4, wfh);
            ps.setInt(5, empId);
            ps.executeUpdate();

            PreparedStatement notif = c.prepareStatement(
                    "INSERT INTO notifications(emp_id,message,date) VALUES (?,?,CURRENT_DATE)");
            notif.setInt(1, empId);
            notif.setString(2, "HR has reset your leave balances.");
            notif.executeUpdate();

            System.out.println("Leave balances reset successfully for Employee ID " + empId);
        } catch (Exception e) {
            System.out.println("Error resetting leave balance: " + e.getMessage());
        }
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
                String dep = d.getString("department");
                int pending = d.getInt("pending");
                int approved = d.getInt("approved");
                int rejected = d.getInt("rejected");
                System.out.printf("%s - Pending: %d | Approved: %d | Rejected: %d%n",
                        dep, pending, approved, rejected);
            }
            if (!any) System.out.println("No department data available.");

        } catch (Exception e) {
            System.out.println("Error loading dashboard: " + e.getMessage());
        }
    }

    static void exportDashboardCSV() {
        String fileName = "HR_Dashboard_Report.csv";

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT d.name AS department, " +
                             "SUM(CASE WHEN l.status='PENDING' THEN 1 ELSE 0 END) AS pending, " +
                             "SUM(CASE WHEN l.status='APPROVED' THEN 1 ELSE 0 END) AS approved, " +
                             "SUM(CASE WHEN l.status='REJECTED' THEN 1 ELSE 0 END) AS rejected " +
                             "FROM departments d " +
                             "LEFT JOIN employees e ON d.dept_id = e.dept_id " +
                             "LEFT JOIN leaves l ON e.emp_id = l.emp_id " +
                             "GROUP BY d.dept_id");
             ResultSet rs = ps.executeQuery()) {

            java.io.FileWriter fw = new java.io.FileWriter(fileName);
            fw.write("Department,Pending,Approved,Rejected\n");

            boolean any = false;
            while (rs.next()) {
                any = true;
                fw.write(String.format("%s,%d,%d,%d\n",
                        rs.getString("department"),
                        rs.getInt("pending"),
                        rs.getInt("approved"),
                        rs.getInt("rejected")));
            }

            fw.close();
            if (any)
                System.out.println("CSV report generated successfully: " + fileName);
            else
                System.out.println("No data available to export.");

        } catch (Exception e) {
            System.out.println("Error exporting CSV: " + e.getMessage());
        }
    }

    static void exportEmployeeLeaveCSV() {
        String fileName = "Employee_Leave_Report.csv";

        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT e.emp_id, e.name, d.name AS department, " +
                             "l.type, l.start_date, l.end_date, l.status, l.manager_remark, l.reason " +
                             "FROM employees e " +
                             "LEFT JOIN departments d ON e.dept_id = d.dept_id " +
                             "LEFT JOIN leaves l ON e.emp_id = l.emp_id " +
                             "WHERE e.role='EMP' ORDER BY e.emp_id, l.start_date");
             ResultSet rs = ps.executeQuery()) {

            java.io.FileWriter fw = new java.io.FileWriter(fileName);
            fw.write("Emp_ID,Name,Department,Type,Start_Date,End_Date,Status,Manager_Remark,Reason\n");

            boolean any = false;
            while (rs.next()) {
                any = true;
                fw.write(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        rs.getInt("emp_id"),
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getString("type"),
                        rs.getDate("start_date"),
                        rs.getDate("end_date"),
                        rs.getString("status"),
                        rs.getString("manager_remark") != null ? rs.getString("manager_remark") : "",
                        rs.getString("reason") != null ? rs.getString("reason").replace(",", ";") : ""));
            }

            fw.close();
            if (any)
                System.out.println("Employee leave report exported successfully: " + fileName);
            else
                System.out.println("No leave data found.");

        } catch (Exception e) {
            System.out.println("Error exporting employee leave report: " + e.getMessage());
        }
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
}
