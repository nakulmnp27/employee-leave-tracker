import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class Employee {
    static Scanner sc = new Scanner(System.in);

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
                    Utils.sendUpcomingLeaveNotification(c, empId);
                    Utils.sendLowBalanceNotification(c, empId);
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
                if (!Utils.hasEnoughBalance(c, empId, type, days)) {
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
            } else {
                System.out.println("No leave balance found.");
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
}
