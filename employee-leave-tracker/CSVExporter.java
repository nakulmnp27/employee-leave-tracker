import java.sql.*;

public class CSVExporter {

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
                fw.write(
                        rs.getString("department") + "," +
                        rs.getInt("pending") + "," +
                        rs.getInt("approved") + "," +
                        rs.getInt("rejected") + "\n"
                );
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
                fw.write(
                        rs.getInt("emp_id") + "," +
                        rs.getString("name") + "," +
                        rs.getString("department") + "," +
                        rs.getString("type") + "," +
                        rs.getDate("start_date") + "," +
                        rs.getDate("end_date") + "," +
                        rs.getString("status") + "," +
                        (rs.getString("manager_remark") != null ? rs.getString("manager_remark") : "") + "," +
                        (rs.getString("reason") != null ? rs.getString("reason").replace(",", ";") : "") +
                        "\n"
                );
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
}
