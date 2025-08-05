# PostgreSQL Setup Instructions

## 1. Download PostgreSQL JDBC Driver
Download the PostgreSQL JDBC driver from: https://jdbc.postgresql.org/download/
Save the `postgresql-42.7.3.jar` file in the project directory.

## 2. Update Database Configuration
In Main.java, update these constants with your PostgreSQL settings:
```java
private static final String DB_URL = "jdbc:postgresql://localhost:5432/student_management";
private static final String DB_USER = "postgres";
private static final String DB_PASSWORD = "your_password_here";
```

## 3. Create Database
Connect to PostgreSQL and create the database:
```sql
CREATE DATABASE student_management;
```

## 4. Compile and Run
```bash
# Compile with PostgreSQL driver
javac -cp ".;postgresql-42.7.3.jar" Main.java

# Run with PostgreSQL driver
java -cp ".;postgresql-42.7.3.jar" Main
```

## 5. Database Structure
The application will create:
- `school_table`: Contains school UUIDs and names
- `students_{school_name}`: Separate table for each school's students

## 6. Features
- ✅ Multithreaded database operations
- ✅ Real-time progress monitoring
- ✅ Automatic table creation
- ✅ UUID-based primary keys
- ✅ Comprehensive student data storage
