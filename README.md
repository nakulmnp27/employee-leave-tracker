<h1 align="center">🗂️ Employee Leave Management System (ELMS)</h1>
<p align="center">A Java-based console application for automating employee leave management with MySQL integration.</p>

---

## 📑 Table of Contents
- [Introduction](#-introduction)
- [System Design](#-system-design)
- [Architecture Diagram](#-architecture-diagram)
- [Technology Stack Used](#-technology-stack-used)
- [Key Challenges Faced and Solutions](#️-key-challenges-faced-and-solutions)
- [Possible Future Enhancements](#-possible-future-enhancements)
- [Author](#-author)

---

## 📝 Introduction

The **Employee Leave Management System (ELMS)** is a **Java-based console application** designed to automate and simplify the process of managing employee leaves within an organization. It provides separate access for employees and HR personnel, enabling secure login, streamlined leave applications, real-time approvals, and automated notifications through an integrated **MySQL database**.

---

## 🧩 System Design

The **Employee Leave Management System (ELMS)** is a console-based application developed in **Java** that automates the leave management process within an organization. It provides secure role-based access for **employees** and **HR administrators**, ensuring smooth handling of leave applications, approvals, and record management. The system connects directly to a **MySQL database** and performs all operations such as login validation, leave application submission, and balance tracking through structured SQL queries. Employees can apply for various types of leaves — **Casual, Sick, Earned, or Work-from-Home (WFH)** — and the system performs real-time validation to prevent invalid dates, overlapping periods, or insufficient balance issues.

The **HR module** allows administrators to efficiently manage all leave requests, review applications, and take action by approving or rejecting them with remarks. It also provides functionalities for adding or removing employees and accessing a **department-wise dashboard** displaying total, approved, rejected, and pending requests in real time. This dashboard offers valuable insights into departmental leave patterns, supporting better workforce planning and decision-making. The system ensures data accuracy and consistency during leave approval by updating both the leave status and the corresponding leave balance simultaneously within a single transaction. Additionally, all reports and leave records can be downloaded in **CSV format** for documentation or analysis purposes.

The application also features an **automated notification system** that improves communication between employees and HR. Notifications are generated automatically for events such as upcoming approved leaves and low leave balances. The backend database (**leave_tracker**) initializes automatically during the first run, creating essential tables such as **departments, employees, leave_balances, leaves,** and **notifications** without any manual setup. With its structured flow, automation, and reliability, the ELMS provides an efficient, paperless, and accurate solution for managing employee leave processes within an organization.

---

## 🏗️ Architecture Diagram

<p align="center">
  <img width="700" src="https://github.com/user-attachments/assets/0a954484-d5d1-4366-b93b-56d8b239f8f0" alt="System Architecture Diagram" />
</p>
<p align="center"><em>Employee Leave Management System Architecture</em></p>

---

<h2 align="center">🧠 Technology Stack Used</h2>

| Layer / Component | Technology | Description |
|-------------------|-------------|--------------|
| **Frontend (Interface)** | Command-Line Interface (CLI) | Text-based user interaction handled in Java console menus. |
| **Backend (Logic Layer)** | Java (Core Java, JDBC) | Contains the main application logic for login, leave handling, HR operations, and notifications. |
| **Database** | MySQL 8.0+ | Stores all persistent data such as employees, departments, leaves, balances, and notifications. |
| **Connector / Driver** | MySQL JDBC Connector (`mysql-connector-j-8.0.31.jar`) | Enables communication between the Java application and the MySQL database. |
| **Development Tools** | JDK 17+, Command Prompt (CMD) | Used for compilation, execution, and local testing of the application. |

---

### ⚙️ Key Challenges Faced and Solutions

- Ensured **automatic database setup** by implementing logic in `Database.java` to create the database and tables if not found.  
- Solved **invalid and overlapping date issues** using `LocalDate` validation and SQL range checks.  
- Maintained **data consistency** during HR approvals through SQL transactions that update leave status and balances atomically.  
- Added **real-time notifications** for upcoming approved leaves and low leave balances to enhance user awareness.  
- Designed a **department-wise analytical dashboard** using optimized SQL queries for clear and structured reporting.  

---

### 🚀 Possible Future Enhancements

- Add a **web or GUI interface** for better accessibility and usability.  
- Integrate **email/SMS notifications** for approval and reminder alerts.  
- Implement **role-based access control** for enhanced security and permissions.  
- Include **data visualization and analytics dashboards** for management insights.  

---

<p align="center">© 2025 Employee Leave Management System | Developed by <b>Nakul Prasath M</b></p>
