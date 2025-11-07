import java.sql.*;
import java.time.LocalDate;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Database.init();
        seed();
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("1. Apply Leave");
            System.out.println("2. Approve/Reject Leave");
            System.out.println("3. Dashboard");
            System.out.println("4. Exit");
            System.out.print("Choose: ");
            String ch = sc.nextLine().trim();
            if (ch.equals("1")) apply(sc);
            else if (ch.equals("2")) decide(sc);
            else if (ch.equals("3")) dashboard();
            else if (ch.equals("4")) break;
        }
    }

    static void seed() {
        try (Connection c = Database.get(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT OR IGNORE INTO departments(dept_id,name) VALUES (1,'Engineering'),(2,'HR')");
            s.executeUpdate("INSERT OR IGNORE INTO employees(emp_id,name,dept_id,role) VALUES (100,'Nakul',1,'EMP'),(200,'Manager1',2,'MGR')");
            s.executeUpdate("INSERT OR IGNORE INTO leave_balances(emp_id,casual,sick,earned,wfh) VALUES (100,5,5,10,10)");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void apply(Scanner sc) {
        try {
            System.out.print("Emp ID: ");
            int empId = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Type (CASUAL/SICK/EARNED/WFH): ");
            String type = sc.nextLine().trim().toUpperCase();
            System.out.print("Start (YYYY-MM-DD): ");
            LocalDate start = LocalDate.parse(sc.nextLine().trim());
            System.out.print("End (YYYY-MM-DD): ");
            LocalDate end = LocalDate.parse(sc.nextLine().trim());
            System.out.print("Reason: ");
            String reason = sc.nextLine().trim();
            try (Connection c = Database.get()) {
                PreparedStatement ps = c.prepareStatement("INSERT INTO leaves(emp_id,type,start_date,end_date,status,reason) VALUES (?,?,?,?,?,?)");
                ps.setInt(1, empId);
                ps.setString(2, type);
                ps.setString(3, start.toString());
                ps.setString(4, end.toString());
                ps.setString(5, "PENDING");
                ps.setString(6, reason);
                ps.executeUpdate();
                System.out.println("Leave request submitted");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void decide(Scanner sc) {
        try (Connection c = Database.get()) {
            PreparedStatement ps = c.prepareStatement("SELECT leave_id, emp_id, type, start_date, end_date, status FROM leaves WHERE status='PENDING'");
            ResultSet rs = ps.executeQuery();
            System.out.println("Pending Requests:");
            while (rs.next()) {
                System.out.printf("%d | emp %d | %s | %s to %s | %s%n",
                        rs.getInt("leave_id"),
                        rs.getInt("emp_id"),
                        rs.getString("type"),
                        rs.getString("start_date"),
                        rs.getString("end_date"),
                        rs.getString("status"));
            }
            System.out.print("Enter leave_id to decide: ");
            String idStr = sc.nextLine().trim();
            int leaveId = Integer.parseInt(idStr);
            System.out.print("Decision (APPROVED/REJECTED): ");
            String decision = sc.nextLine().trim().toUpperCase();
            System.out.print("Manager remark: ");
            String remark = sc.nextLine().trim();
            c.setAutoCommit(false);
            PreparedStatement upd = c.prepareStatement("UPDATE leaves SET status=?, manager_remark=? WHERE leave_id=?");
            upd.setString(1, decision);
            upd.setString(2, remark);
            upd.setInt(3, leaveId);
            upd.executeUpdate();
            if (decision.equals("APPROVED")) {
                PreparedStatement ps2 = c.prepareStatement("SELECT emp_id,type,start_date,end_date FROM leaves WHERE leave_id=?");
                ps2.setInt(1, leaveId);
                ResultSet r2 = ps2.executeQuery();
                if (r2.next()) {
                    int emp = r2.getInt("emp_id");
                    String type = r2.getString("type");
                    LocalDate s = LocalDate.parse(r2.getString("start_date"));
                    LocalDate e = LocalDate.parse(r2.getString("end_date"));
                    long days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1;
                    String col = type.equals("CASUAL") ? "casual" : type.equals("SICK") ? "sick" : type.equals("EARNED") ? "earned" : "wfh";
                    PreparedStatement dec = c.prepareStatement("UPDATE leave_balances SET " + col + " = " + col + " - ? WHERE emp_id=?");
                    dec.setLong(1, days);
                    dec.setInt(2, emp);
                    dec.executeUpdate();
                    PreparedStatement notif = c.prepareStatement("INSERT INTO notifications(emp_id,message,date) VALUES (?,?,date('now'))");
                    notif.setInt(1, emp);
                    notif.setString(2, "Leave approved for " + s + " to " + e);
                    notif.executeUpdate();
                }
            }
            c.commit();
            c.setAutoCommit(true);
            System.out.println("Decision saved");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void dashboard() {
        try (Connection c = Database.get(); Statement s = c.createStatement()) {
            ResultSet a = s.executeQuery("SELECT COUNT(*) FROM leaves");
            a.next();
            System.out.println("Total Requests: " + a.getInt(1));
            ResultSet p = s.executeQuery("SELECT COUNT(*) FROM leaves WHERE status='PENDING'");
            p.next();
            System.out.println("Pending: " + p.getInt(1));
            ResultSet ap = s.executeQuery("SELECT COUNT(*) FROM leaves WHERE status='APPROVED'");
            ap.next();
            System.out.println("Approved: " + ap.getInt(1));
            ResultSet r = s.executeQuery("SELECT COUNT(*) FROM leaves WHERE status='REJECTED'");
            r.next();
            System.out.println("Rejected: " + r.getInt(1));
            System.out.println("By Department:");
            ResultSet d = s.executeQuery("SELECT departments.name, COUNT(leaves.leave_id) FROM leaves JOIN employees ON leaves.emp_id=employees.emp_id JOIN departments ON employees.dept_id=departments.dept_id GROUP BY departments.name");
            while (d.next()) {
                System.out.println(d.getString(1) + ": " + d.getInt(2));
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
