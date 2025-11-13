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

            if (ch.equals("1")) Employee.employeeLogin();
            else if (ch.equals("2")) HR.hrLogin();
            else if (ch.equals("3")) {
                System.out.println("Exiting system...");
                break;
            }
        }
    }
}
