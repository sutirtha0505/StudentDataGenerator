# Student Data Generator with PostgreSQL Integration

## 🎯 Overview
This application generates comprehensive student data for multiple schools and stores it in PostgreSQL database with multithreading support and real-time progress monitoring.

## 📋 Features
- ✅ **Multithreaded Database Operations** - Fast parallel data insertion
- ✅ **Real-time Progress Monitoring** - See generation progress in terminal
- ✅ **Automatic Table Creation** - Creates school and student tables automatically
- ✅ **UUID-based Primary Keys** - Unique identifiers for all records
- ✅ **Comprehensive Student Data** - Name, guardian, demographics, medical info, etc.
- ✅ **School Distribution** - Distributes students across multiple schools
- ✅ **MinIO Image URLs** - Generates image URLs for each student
- ✅ **Academic Results Generation** - Creates marksheets and exam results for students
- ✅ **Multi-term Support** - Supports multiple terms/semesters per academic year
- ✅ **Flexible Subject Configuration** - Customizable subjects with individual full marks
- ✅ **Automatic Calculations** - Computes totals and percentages automatically

## 🛠️ Prerequisites
1. **Java 8 or higher**
2. **PostgreSQL** installed and running
3. **PostgreSQL JDBC Driver** (already included: `postgresql-42.7.3.jar`)

## 📦 Database Setup

### 1. Install PostgreSQL
Download and install PostgreSQL from: https://www.postgresql.org/download/

### 2. Create Database
Connect to PostgreSQL (using psql or pgAdmin) and run:
```sql
CREATE DATABASE student_management;
```

### 3. Update Configuration
Open `Main.java` and update these constants if needed:
```java
private static final String DB_URL = "jdbc:postgresql://localhost:5432/student_management";
private static final String DB_USER = "postgres";
private static final String DB_PASSWORD = "your_password_here";
```

## 🚀 How to Run

### 1. Compile
```bash
javac -cp ".;postgresql-42.7.3.jar" Main.java
```

### 2. Run
```bash
java -cp ".;postgresql-42.7.3.jar" Main
```

### 3. Follow Prompts
The application will ask for:
- Number of classes per school (e.g., 12)
- Number of sections per class (e.g., 4)
- Number of students per section (e.g., 30)
- Number of schools to generate (e.g., 10)

## 📊 Database Structure

### School Table
```sql
CREATE TABLE school_table (
    school_uuid UUID PRIMARY KEY,
    school_name VARCHAR(255) NOT NULL UNIQUE
);
```

### Student Tables (one per school)
```sql
CREATE TABLE students_{school_name} (
    student_uuid UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    guardian_name VARCHAR(100) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    blood_group VARCHAR(5) NOT NULL,
    birth_date DATE NOT NULL,
    aadhar_card VARCHAR(20) NOT NULL UNIQUE,
    class_name VARCHAR(20) NOT NULL,
    section VARCHAR(5) NOT NULL,
    roll_no INTEGER NOT NULL,
    religion VARCHAR(20) NOT NULL,
    parent_occupation VARCHAR(100) NOT NULL,
    concession_needed BOOLEAN NOT NULL,
    concession_type VARCHAR(50),
    medical_condition VARCHAR(50),
    student_phone VARCHAR(15) NOT NULL UNIQUE,
    guardian_phone VARCHAR(15) NOT NULL UNIQUE,
    image_url TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Academic Tables (one per school, class, and year)
```sql
CREATE TABLE {school_name}_class_{class_number}_{session_year} (
    student_uuid UUID PRIMARY KEY,
    student_name VARCHAR(100),
    roll_no INTEGER,
    section VARCHAR(5),
    -- Subject columns (dynamic based on user input)
    {subject_name}_full_marks_term_{term_number} INTEGER DEFAULT {full_marks},
    {subject_name}_obtained_marks_term_{term_number} INTEGER,
    -- Term totals and percentages
    total_marks_term_{term_number} INTEGER,
    percentage_term_{term_number} DECIMAL(5,2),
    -- Grand totals
    grand_total INTEGER,
    percentage_grand_total DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 🎓 Academic Features

After generating student data, the system will automatically proceed to generate academic results:

### Academic Data Generation Process
1. **Class Selection**: Enter class range (e.g., '1-3' or '6-8')
2. **Term Configuration**: Specify number of terms per academic year
3. **Subject Setup**: Define subjects and their full marks
4. **Automatic Generation**: System creates marksheets for all students

### Academic Table Naming
- Pattern: `{sanitized_school_name}_class_{class_number}_{session_year}`
- Example: `st_mary_public_school_class_1_2025`

### Student Progression Logic
- Students get marksheets for their academic journey
- Class 3 student gets: Class 1 (2023), Class 2 (2024), Class 3 (2025)
- Realistic progression based on current year

### Mark Generation
- Realistic mark distribution (40-100% of full marks)
- Automatic calculation of totals and percentages
- Separate tracking for each term
```

## 🔍 Sample SQL Queries

### View All Schools
```sql
SELECT * FROM school_table;
```

### View Students from a Specific School
```sql
SELECT * FROM students_st_mary_public_school LIMIT 10;
```

### Count Students by Gender
```sql
SELECT gender, COUNT(*) 
FROM students_st_mary_public_school 
GROUP BY gender;
```

### Count Students by Class
```sql
SELECT class_name, COUNT(*) 
FROM students_st_mary_public_school 
GROUP BY class_name 
ORDER BY class_name;
```

### Find Students with Medical Conditions
```sql
SELECT full_name, medical_condition 
FROM students_st_mary_public_school 
WHERE medical_condition IS NOT NULL;
```

### Students Needing Concessions
```sql
SELECT full_name, concession_type 
FROM students_st_mary_public_school 
WHERE concession_needed = true;
```

## 💡 Progress Monitoring
The application shows real-time progress:
- Database connection status
- School generation progress
- Student data generation progress (updated every 100 students)
- Final summary with statistics

## ⚡ Performance
- **Multithreading**: Uses 10 parallel threads for database operations
- **Batch Processing**: Optimized for large datasets
- **Progress Updates**: Non-blocking progress monitoring
- **Memory Efficient**: Processes data in streams

## 🎲 Generated Data
- **10,000 unique name combinations** (100 first names × 100 last names)
- **Realistic Indian data**: Names, phone numbers, Aadhar cards
- **Age-appropriate DOB**: Based on class level
- **Guardian logic**: Same last name, different first name
- **Diverse demographics**: Religion, blood group, medical conditions
- **School distribution**: Even distribution across schools/classes/sections

## 🐛 Troubleshooting

### Database Connection Issues
1. Ensure PostgreSQL is running
2. Check database name, username, and password
3. Verify PostgreSQL is accepting connections on localhost:5432

### Compilation Issues
1. Ensure Java is installed: `java -version`
2. Check JDBC driver is present: `postgresql-42.7.3.jar`
3. Use correct classpath: `-cp ".;postgresql-42.7.3.jar"`

### Memory Issues
1. Increase JVM heap size: `java -Xmx2g -cp ".;postgresql-42.7.3.jar" Main`
2. Reduce number of schools or students per batch

## 📈 Example Output
```
=== TESTING DATABASE CONNECTION ===
Successfully connected to PostgreSQL database!

=== SCHOOL MANAGEMENT SYSTEM ===
Enter number of classes in each school (e.g., 12 for classes 1-12): 5
Enter number of sections per class (e.g., 4 for sections A, B, C, D): 2
Enter number of students per section: 25
Enter number of schools to generate (max 10000): 3

=== SCHOOL STRUCTURE ===
Number of Schools: 3
Classes per School: 5 (Class 1 to Class 5)
Sections per Class: 2
Students per Section: 25
Total Students per School: 250
Total Students Across All Schools: 750

=== CREATING DATABASE TABLES ===
School table created/verified successfully

=== GENERATING SCHOOLS ===
Student table created for: St. Mary Public School
Student table created for: Holy Angels High School
Student table created for: Sacred Heart Academy
School generation completed!

=== GENERATING AND SAVING STUDENT DATA ===
Total students to generate: 10000
Progress will be shown every 100 students...

School inserted: St. Mary Public School (1 schools processed)
School inserted: Holy Angels High School (2 schools processed)
School inserted: Sacred Heart Academy (3 schools processed)
Progress: 100/10000 students (1.0%) processed
Students processed: 100
Progress: 200/10000 students (2.0%) processed
Students processed: 200
...
```

This application provides a complete solution for generating realistic student data and storing it efficiently in PostgreSQL with full multithreading support!
