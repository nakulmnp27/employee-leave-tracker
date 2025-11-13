import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class HR {
    static Scanner sc = new Scanner(System.in);

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
            else if (ch.equals("4")) Utils.viewDashboard();
            else if (ch.equals("5")) resetLeaveBalance();
            else if (ch.equals("6")) CSVExporter.exportDashboardCSV();
            else if (ch.equals("7")) CSVExporter.exportEmployeeLeaveCSV();
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
            System.out.println("\n--- Existing Employees ---");
            PreparedStatement ps = c.prepareStatement(
                    "SELECT e.emp_id, e.name, d.name AS department, e.role " +
                            "FROM employees e LEFT JOIN departments d ON e.dept_id = d.dept_id " +
                            "WHERE e.role='EMP' ORDER BY e.emp_id");
            ResultSet rs = ps.executeQuery();
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("ID: %d | Name: %s | Department: %s | Role: %s%n",
                        rs.getInt("emp_id"),
                        rs.getString("name"),
                        rs.getString("department") != null ? rs.getString("department") : "N/A",
                        rs.getString("role"));
            }
            if (!any) {
                System.out.println("No employees available to remove.");
                return;
            }

            System.out.print("\nEnter Employee ID to remove: ");
            int empId = Integer.parseInt(sc.nextLine().trim());

            PreparedStatement chk = c.prepareStatement("SELECT role FROM employees WHERE emp_id=? AND role='EMP'");
            chk.setInt(1, empId);
            ResultSet chkRs = chk.executeQuery();
            if (!chkRs.next()) {
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
        Utils.viewDashboard();
    }
}
