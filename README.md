# Student Data Generator with PostgreSQL Integration

## 🎥 What it does?
---

##  Demo Video  
[![Watch the Universe From Scratch Demo](./assets/Thumbnail.png)](./assets/Universe_From_Scratch.mp4)

*Click the thumbnail to play the video in your browser.*

---

## 🎯 Overview
This application generates comprehensive student data for multiple schools and stores it in PostgreSQL database with advanced multithreading, dynamic hardware detection, and intelligent academic results generation.

## 📋 Features
- ✅ **Dynamic Hardware Detection** - Automatically detects CPU cores and optimizes performance
- ✅ **Intelligent Thread Allocation** - I/O-bound optimization with 2x CPU core threading
- ✅ **HikariCP Connection Pooling** - Enterprise-grade database connection management
- ✅ **Memory-Efficient LRU Caches** - Prevents memory exhaustion with large datasets
- ✅ **Batch Processing System** - Optimized bulk database operations
- ✅ **Real-time Progress Monitoring** - See generation progress with smart intervals
- ✅ **Automatic Table Creation** - Creates school and student tables automatically
- ✅ **UUID-based Primary Keys** - Unique identifiers for all records
- ✅ **Comprehensive Student Data** - Name, guardian, demographics, medical info, stream assignments
- ✅ **School Distribution** - Distributes students across multiple schools
- ✅ **MinIO Image URLs** - Generates realistic image URLs for each student
- ✅ **Three Academic Results Modes** - Interactive, Automatic, and Skip options
- ✅ **Timeout-based Input Detection** - No more hanging on user input
- ✅ **Multi-term Support** - Supports multiple terms/semesters per academic year
- ✅ **Flexible Subject Configuration** - Customizable subjects with individual full marks
- ✅ **Automatic Academic Progression** - Generates historical academic records
- ✅ **Robust Error Handling** - Comprehensive retry logic and graceful error recovery
- ✅ **Cross-platform Scripts** - PowerShell and Bash scripts for all workflows

## 🛠️ Prerequisites
1. **Java 8 or higher** (Tested with Java 17)
2. **PostgreSQL** installed and running (Local instance)
3. **Dependencies** (All included in `dependencies/` folder):
   - HikariCP 6.0.0 (Connection pooling)
   - SLF4J API 2.0.13 (Logging abstraction)
   - SLF4J Simple 2.0.13 (Logging implementation)
   - PostgreSQL JDBC 42.7.4 (Database driver)

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
private static final String DB_PASSWORD = "Sutirtha_05@Postgress"; // Update with your password
```

## 🚀 How to Run

### Quick Start (Recommended)
Use the provided PowerShell scripts for easy execution:

#### 1. Compile
```powershell
.\compile.ps1
```

#### 2. Run Options
- **Quick Test** (Small dataset): `.\quick_test.ps1`
- **Large Dataset Test**: `.\large_dataset.ps1` 
- **Interactive Mode**: `.\interactive_enhanced.ps1`
- **Full Interactive**: `.\interactive.ps1`

### Manual Compilation and Execution

#### 1. Compile
```powershell
javac -cp "dependencies\*" -d . Main.java
```

#### 2. Run
```powershell
java -cp "dependencies\*;." Main
```

### Three Academic Results Modes

The application now supports three modes for academic results generation:

#### 1. **Interactive Mode**
- Type `y` when prompted for academic results
- Customize class range, terms per year, and session year
```
Do you want to generate academic results? (y/n) [Press Enter for automatic]: y
Enter the classes for which you want to generate results (e.g., '1-3' or '6-8'): 1-5
Enter number of terms per year: 3
Enter session year: 2025
```

#### 2. **Automatic Mode** 
- Press Enter or wait 5 seconds at the academic results prompt
- Uses school structure parameters automatically
- Generates results for all classes with default settings (3 terms, current year)

#### 3. **Skip Mode**
- Type `n` when prompted for academic results
- Skips academic results generation entirely

### 4. Follow Prompts
The application will ask for:
- Number of classes per school (e.g., 12)
- Number of sections per class (e.g., 4)
- Number of students per section (e.g., 30)
- Number of schools to generate (e.g., 10)
- Academic results preference (y/n or wait for automatic)

## 🏗️ System Architecture

### Hardware Detection & Optimization
The application automatically detects your system specifications:
```
=== SYSTEM HARDWARE DETECTION ===
System Information:
  Operating System: Windows 11 10.0
  Java Version: 17.0.2 (Oracle Corporation)
  CPU Cores detected: 12
Thread Allocation:
  Strategy: I/O-bound (database operations)
  Optimal thread count: 24
  Thread pool initialized with 24 threads
Memory Information:
  Max Memory: 4.98 GB
  Total Memory: 320.00 MB
  Free Memory: 317.17 MB
```

### Dynamic Connection Pool
```
=== INITIALIZING DATABASE CONNECTION POOL ===
Connection pool configured dynamically:
  Maximum connections: 29
  Minimum idle connections: 6
  Ratio: 1.2 connections per thread
```

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
    stream VARCHAR(20), -- Science, Commerce, Arts (for classes 11-12)
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

The application now supports intelligent academic results generation with three distinct modes:

### Academic Results Generation Modes

#### 1. **Interactive Mode** (Custom Configuration)
```
Do you want to generate academic results? (y/n) [Press Enter for automatic]: y
Enter the classes for which you want to generate results (e.g., '1-3' or '6-8'): 6-10
Enter number of terms per year: 2
Enter session year: 2025
```

#### 2. **Automatic Mode** (Smart Defaults)
```
Do you want to generate academic results? (y/n) [Press Enter for automatic]: [ENTER]
No input provided within 5 seconds. Generating academic results automatically...
Automatically generating academic results for all 12 classes...
Configuration:
  Class Range: 1-12 (all classes in school structure)
  Terms per Year: 3
  Session Year: 2025
```

#### 3. **Skip Mode** (No Academic Results)
```
Do you want to generate academic results? (y/n) [Press Enter for automatic]: n
Academic results generation skipped by user.
```

### Academic Data Generation Process
- **Timeout Protection**: 5-second timeout prevents hanging
- **Class Selection**: Enter class range (e.g., '1-3' or '6-8') or auto-detect from school structure
- **Term Configuration**: Specify number of terms per academic year (default: 3)
- **Subject Setup**: Automatic subject configuration based on class level
- **Historical Records**: Generates academic progression (Class 3 student gets records for Classes 1, 2, 3)

### Academic Table Naming
- Pattern: `{sanitized_school_name}_class_{class_number}_{session_year}`
- Example: `st_mary_public_school_class_1_2025`

### Subject Configuration by Class Level
- **Classes 1-5**: Bengali, English, Mathematics, Environmental Studies, Arts and Crafts, Physical Education
- **Classes 6-8**: Bengali, English, Mathematics, Science, Social Science, Computer Science, Physical Education
- **Classes 9-10**: Bengali, English, Mathematics, Physical Science, Life Science, Geography, History, Computer Science, Physical Education
- **Classes 11-12**: Stream-based subjects (Science/Commerce/Arts) with advanced curriculum

### Mark Generation Algorithm
- **Realistic Distribution**: 40-100% of full marks with weighted probability
- **Performance Patterns**: Consistent student performance with natural variation
- **Automatic Calculations**: Term totals, percentages, and grand totals
- **Grade Assignment**: Based on percentage ranges
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

### View Academic Results
```sql
-- View academic results for a specific class
SELECT * FROM st_mary_public_school_class_5_2025 LIMIT 10;

-- Student performance analysis
SELECT 
    student_name,
    percentage_grand_total,
    CASE 
        WHEN percentage_grand_total >= 90 THEN 'A+'
        WHEN percentage_grand_total >= 80 THEN 'A'
        WHEN percentage_grand_total >= 70 THEN 'B+'
        WHEN percentage_grand_total >= 60 THEN 'B'
        WHEN percentage_grand_total >= 50 THEN 'C'
        ELSE 'F'
    END as grade
FROM st_mary_public_school_class_5_2025
ORDER BY percentage_grand_total DESC;
```

### Student Stream Distribution (Classes 11-12)
```sql
-- View stream distribution
SELECT stream, COUNT(*) 
FROM students_st_mary_public_school 
WHERE class_name IN ('Class 11', 'Class 12')
GROUP BY stream;
```

## ⚡ Performance & Optimization

### Hardware Optimization
- **CPU Detection**: Automatically detects available CPU cores
- **Thread Allocation**: Uses 2x CPU cores for I/O-bound database operations
- **Memory Management**: LRU caches with 100K entry limits prevent memory exhaustion
- **Connection Pooling**: HikariCP with dynamic sizing (1.2x thread count)

### Processing Efficiency
- **Batch Processing**: Groups operations for optimal database performance
- **Progress Monitoring**: Smart interval updates (100 for small, 1000 for large datasets)
- **Retry Logic**: Automatic retry with exponential backoff for failed operations
- **Memory Efficient**: Streams and batching prevent OutOfMemoryError

### Scalability Features
- **Large Dataset Support**: Tested with 100K+ students
- **Dynamic Scaling**: Adjusts thread pool and connection pool based on system resources
- **Timeout Management**: Prevents hanging with configurable timeouts
- **Error Recovery**: Graceful handling of database connection issues

## 🎲 Generated Data
- **10,000 unique name combinations** (100 first names × 100 last names)
- **Realistic Indian data**: Names, phone numbers, Aadhar cards
- **Age-appropriate DOB**: Based on class level (6-18 years)
- **Guardian logic**: Same last name, different first name
- **Diverse demographics**: Religion, blood group, medical conditions
- **School distribution**: Even distribution across schools/classes/sections
- **Stream assignment**: Science/Commerce/Arts for classes 11-12
- **Academic progression**: Historical academic records for realistic student journey
- **Performance variation**: Realistic mark distribution with consistent student patterns

## 📜 Available Scripts

### PowerShell Scripts (Windows)
- `compile.ps1` - Compile the application
- `run.ps1` - Standard run with interactive prompts
- `quick_test.ps1` - Quick test with small dataset (2 classes, 1 section, 2 students, 1 school)
- `large_dataset.ps1` - Large dataset test (12 classes, 4 sections, 50 students, 5 schools)
- `interactive.ps1` - Full interactive mode with user prompts
- `interactive_enhanced.ps1` - Enhanced interactive with clear instructions

### Cross-platform Scripts
- `run.sh` - Bash script for Linux/Mac
- `download_dependencies.sh` - Download latest JAR dependencies

### Usage Examples
```powershell
# Quick compilation and test
.\compile.ps1; .\quick_test.ps1

# Large dataset with automatic academic results
.\large_dataset.ps1

# Full interactive experience with guidance
.\interactive_enhanced.ps1
```

## 🐛 Troubleshooting

### Database Connection Issues
1. **PostgreSQL not running**: Start PostgreSQL service
   ```powershell
   # Windows
   net start postgresql-x64-15
   ```
2. **Connection refused**: Check if PostgreSQL is accepting connections
   ```sql
   -- Test connection
   psql -h localhost -p 5432 -U postgres -d student_management
   ```
3. **Authentication failed**: Verify username and password in `Main.java`
4. **Database doesn't exist**: Create the database first
   ```sql
   CREATE DATABASE student_management;
   ```

### Compilation Issues
1. **Java not found**: Install Java and verify installation
   ```powershell
   java -version
   javac -version
   ```
2. **Dependencies missing**: Ensure all JARs are in `dependencies/` folder
   ```
   dependencies/
   ├── HikariCP-6.0.0.jar
   ├── slf4j-api-2.0.13.jar
   ├── slf4j-simple-2.0.13.jar
   └── postgresql-42.7.4.jar
   ```
3. **Classpath issues**: Use the provided PowerShell scripts or correct classpath syntax

### Runtime Issues
1. **Academic results hanging**: Fixed with 5-second timeout mechanism
2. **Memory issues**: Increase heap size for large datasets
   ```powershell
   java -Xmx8g -cp "dependencies\*;." Main
   ```
3. **Too many connections**: Reduce thread count or increase PostgreSQL max_connections
4. **Input encoding issues**: Ensure terminal supports UTF-8

### Performance Issues
1. **Slow generation**: Check system resources and database performance
2. **Connection timeouts**: Increase connection timeout in HikariCP configuration
3. **Large dataset failures**: Use batch processing and monitor memory usage

### Common Error Messages
- **"No suitable driver"**: PostgreSQL JDBC driver missing from classpath
- **"Connection refused"**: PostgreSQL service not running
- **"OutOfMemoryError"**: Increase JVM heap size or reduce dataset size
- **"Scanner closed"**: Timeout mechanism activated, behavior is normal

## 📈 Example Output
```
=== SYSTEM HARDWARE DETECTION ===
System Information:
  Operating System: Windows 11 10.0
  Java Version: 17.0.2 (Oracle Corporation)
  CPU Cores detected: 12
Thread Allocation:
  Strategy: I/O-bound (database operations)
  Optimal thread count: 24
  Thread pool initialized with 24 threads
Memory Information:
  Max Memory: 4.98 GB
  Total Memory: 320.00 MB
  Free Memory: 317.17 MB

=== INITIALIZING DATABASE CONNECTION POOL ===
Connection pool configured dynamically:
  Maximum connections: 29
  Minimum idle connections: 6
  Ratio: 1.2 connections per thread

=== TESTING DATABASE CONNECTION ===
Successfully connected to PostgreSQL database!

=== SCHOOL MANAGEMENT SYSTEM ===
Enter number of classes in each school (e.g., 12 for classes 1-12): 12
Enter number of sections per class (e.g., 4 for sections A, B, C, D): 4
Enter number of students per section: 30
Enter number of schools to generate: 5

=== SCHOOL STRUCTURE ===
Number of Schools: 5
Classes per School: 12 (Class 1 to Class 12)
Sections per Class: 4
Students per Section: 30
Total Students per School: 1440
Total Students Across All Schools: 7200

=== CREATING DATABASE TABLES ===
School table created/verified successfully

=== GENERATING SCHOOLS ===
Student table created for: St. Mary Public School
Student table created for: Holy Angels High School
Student table created for: Sacred Heart Academy
Student table created for: Modern English School
Student table created for: Bright Future Institute
School generation completed!

=== GENERATING AND SAVING STUDENT DATA ===
Total students to generate: 7200
Using memory-efficient LRU caches (max 100000 entries each) to prevent memory exhaustion
Starting batch processor for efficient database operations...
Progress will be shown every 100 students...

Students processed: 1000
Students processed: 2000
Students processed: 3000
...
Students processed: 7200
Progress: 7200/7200 students (100.0%) processed

=== GENERATING ACADEMIC RESULTS ===
Do you want to generate academic results? (y/n) [Press Enter for automatic]: 
No input provided within 5 seconds. Generating academic results automatically...
Automatically generating academic results for all 12 classes...
Configuration:
  Class Range: 1-12 (all classes in school structure)
  Terms per Year: 3
  Session Year: 2025

=== AUTOMATIC ACADEMIC RESULTS GENERATION COMPLETE ===
Generated academic results for:
  Schools: 5
  Classes: 12 (Classes 1 to 12)
  Terms per year: 3
  Total academic tables: 60
Academic tables created with format: {school}_class_{class}_{year}
```

## 🎯 Key Improvements in Current Version

### ✅ **Fixed Issues**
- **No more hanging**: 5-second timeout for academic results input
- **Memory optimization**: LRU caches prevent memory exhaustion
- **Connection pooling**: HikariCP prevents connection leaks
- **Error handling**: Comprehensive retry logic and graceful recovery

### ✅ **New Features**
- **Hardware detection**: Automatic CPU core detection and thread optimization
- **Three academic modes**: Interactive, Automatic, Skip options
- **Stream assignments**: Science/Commerce/Arts for senior classes
- **Enhanced scripts**: Multiple PowerShell scripts for different workflows
- **Progress monitoring**: Smart interval updates based on dataset size

### ✅ **Performance Enhancements**
- **Dynamic threading**: 2x CPU cores for I/O-bound operations
- **Batch processing**: Efficient bulk database operations
- **Connection management**: Dynamic pool sizing based on thread count
- **Memory efficiency**: Streaming and caching strategies

This application provides a complete, production-ready solution for generating realistic student data with academic records, optimized for any system configuration and dataset size!
