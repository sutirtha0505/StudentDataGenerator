import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/student_management";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "Sutirtha_05@Postgress"; // Change this to your PostgreSQL password
    
    // Thread pool for database operations
    private static ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static AtomicInteger processedStudents = new AtomicInteger(0);
    private static AtomicInteger processedSchools = new AtomicInteger(0);
    
    // Method to establish database connection
    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
    
    // Method to create database tables
    private static void createTables() {
        try (Connection conn = getConnection()) {
            // Create school_table
            String createSchoolTable = """
                CREATE TABLE IF NOT EXISTS school_table (
                    school_uuid UUID PRIMARY KEY,
                    school_name VARCHAR(255) NOT NULL UNIQUE
                )
                """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createSchoolTable);
                System.out.println("School table created/verified successfully");
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Method to create student table for a specific school
    private static void createStudentTable(String schoolName) {
        String tableName = getStudentTableName(schoolName);
        
        try (Connection conn = getConnection()) {
            String createStudentTable = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
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
                )
                """, tableName);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createStudentTable);
                System.out.println("Student table created for: " + schoolName);
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating student table for " + schoolName + ": " + e.getMessage());
        }
    }
    
    // Method to get sanitized table name for students
    private static String getStudentTableName(String schoolName) {
        return "students_" + schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
    }
    
    // Method to insert school data
    private static void insertSchool(String schoolUUID, String schoolName) {
        String sql = "INSERT INTO school_table (school_uuid, school_name) VALUES (?, ?) ON CONFLICT (school_name) DO NOTHING";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setObject(1, java.util.UUID.fromString(schoolUUID));
            pstmt.setString(2, schoolName);
            pstmt.executeUpdate();
            
            int processed = processedSchools.incrementAndGet();
            System.out.println("School inserted: " + schoolName + " (" + processed + " schools processed)");
            
        } catch (SQLException e) {
            System.err.println("Error inserting school " + schoolName + ": " + e.getMessage());
        }
    }
    
    // Method to insert student data
    private static void insertStudent(StudentData student) {
        String tableName = getStudentTableName(student.schoolName);
        String sql = String.format("""
            INSERT INTO %s (student_uuid, full_name, guardian_name, gender, blood_group, 
            birth_date, aadhar_card, class_name, section, roll_no, religion, 
            parent_occupation, concession_needed, concession_type, medical_condition, 
            student_phone, guardian_phone, image_url) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setObject(1, java.util.UUID.fromString(student.studentUUID));
            pstmt.setString(2, student.fullName);
            pstmt.setString(3, student.guardianName);
            pstmt.setString(4, student.gender);
            pstmt.setString(5, student.bloodGroup);
            pstmt.setDate(6, parseDate(student.birthDate));
            pstmt.setString(7, student.aadharNumber);
            pstmt.setString(8, student.className);
            pstmt.setString(9, student.section);
            pstmt.setInt(10, student.rollNo);
            pstmt.setString(11, student.religion);
            pstmt.setString(12, student.parentOccupation);
            pstmt.setBoolean(13, student.concessionNeeded.equals("Yes"));
            pstmt.setString(14, student.concessionType.equals("N/A") ? null : student.concessionType);
            pstmt.setString(15, student.medicalCondition.equals("None") ? null : student.medicalCondition);
            pstmt.setString(16, student.studentPhone);
            pstmt.setString(17, student.guardianPhone);
            pstmt.setString(18, student.imageUrl);
            
            pstmt.executeUpdate();
            
            int processed = processedStudents.incrementAndGet();
            if (processed % 100 == 0) {
                System.out.println("Students processed: " + processed);
            }
            
        } catch (SQLException e) {
            System.err.println("Error inserting student " + student.fullName + ": " + e.getMessage());
        }
    }
    
    // Helper method to parse date
    private static Date parseDate(String dateStr) {
        try {
            String[] parts = dateStr.split("/");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            return Date.valueOf(String.format("%d-%02d-%02d", year, month, day));
        } catch (Exception e) {
            return Date.valueOf("2020-01-01"); // Default date
        }
    }
    
    // Student data class
    static class StudentData {
        String studentUUID, fullName, guardianName, gender, bloodGroup, birthDate;
        String aadharNumber, className, section, religion, parentOccupation;
        String concessionNeeded, concessionType, medicalCondition, studentPhone, guardianPhone;
        String schoolName, imageUrl;
        int rollNo;
        
        StudentData(String studentUUID, String fullName, String guardianName, String gender, 
                   String bloodGroup, String birthDate, String aadharNumber, String className, 
                   String section, int rollNo, String schoolName, String religion, 
                   String parentOccupation, String concessionNeeded, String concessionType, 
                   String medicalCondition, String studentPhone, String guardianPhone, String imageUrl) {
            this.studentUUID = studentUUID;
            this.fullName = fullName;
            this.guardianName = guardianName;
            this.gender = gender;
            this.bloodGroup = bloodGroup;
            this.birthDate = birthDate;
            this.aadharNumber = aadharNumber;
            this.className = className;
            this.section = section;
            this.rollNo = rollNo;
            this.schoolName = schoolName;
            this.religion = religion;
            this.parentOccupation = parentOccupation;
            this.concessionNeeded = concessionNeeded;
            this.concessionType = concessionType;
            this.medicalCondition = medicalCondition;
            this.studentPhone = studentPhone;
            this.guardianPhone = guardianPhone;
            this.imageUrl = imageUrl;
        }
    }
    
    // Method to generate unique Indian phone numbers
    private static String generateUniquePhoneNumber(java.util.Set<String> usedNumbers) {
        String phoneNumber;
        do {
            // Generate 10 digit number starting with 6, 7, 8, or 9
            int firstDigit = 6 + (int) (Math.random() * 4); // Random number between 6-9
            StringBuilder number = new StringBuilder("+91" + firstDigit);
            
            // Generate remaining 9 digits
            for (int i = 0; i < 9; i++) {
                number.append((int) (Math.random() * 10));
            }
            
            phoneNumber = number.toString();
        } while (usedNumbers.contains(phoneNumber));
        
        usedNumbers.add(phoneNumber);
        return phoneNumber;
    }
    
    // Method to generate unique school names
    private static String generateUniqueSchoolName(java.util.Set<String> usedSchoolNames) {
        String[] schoolPrefixes = {
            "St.", "Holy", "Sacred", "Divine", "Blessed", "Mount", "Little", "Bright", "Golden", "Silver",
            "Green", "Blue", "Red", "New", "Modern", "Progressive", "Advanced", "Premier", "Elite", "Excellence",
            "Victory", "Success", "Wisdom", "Knowledge", "Learning", "Future", "Hope", "Dream", "Star", "Sun",
            "Moon", "Rainbow", "Crystal", "Diamond", "Pearl", "Emerald", "Ruby", "Sapphire", "Lotus", "Rose"
        };
        
        String[] schoolMiddles = {
            "Angels", "Mary", "Xavier", "Francis", "Joseph", "Michael", "Gabriel", "Paul", "Peter", "John",
            "Thomas", "Anthony", "Stephen", "Lawrence", "Vincent", "Augustine", "Benedict", "Dominic", "Carmel",
            "Teresa", "Agnes", "Catherine", "Margaret", "Elizabeth", "Anne", "Grace", "Faith", "Hope", "Joy",
            "Peace", "Light", "Dawn", "Morning", "Evening", "Spring", "Summer", "Autumn", "Winter", "Valley",
            "Hills", "Heights", "Gardens", "Park", "Grove", "Woods", "Forest", "River", "Lake", "Ocean"
        };
        
        String[] schoolSuffixes = {
            "Public School", "Higher Secondary School", "High School", "Senior Secondary School", 
            "English Medium School", "Model School", "International School", "Academy", "Institute",
            "Educational Institute", "Learning Center", "Study Center", "Knowledge Hub", "Vidyalaya",
            "Vidya Mandir", "Vidya Niketan", "Vidya Bhawan", "Vidya Kendra", "Shiksha Niketan",
            "Bal Vidyalaya", "Convent School", "Mission School", "Memorial School", "Foundation School"
        };
        
        String schoolName;
        do {
            String prefix = schoolPrefixes[(int) (Math.random() * schoolPrefixes.length)];
            String middle = schoolMiddles[(int) (Math.random() * schoolMiddles.length)];
            String suffix = schoolSuffixes[(int) (Math.random() * schoolSuffixes.length)];
            
            // Sometimes skip the middle part for variation
            if (Math.random() < 0.3) {
                schoolName = prefix + " " + suffix;
            } else {
                schoolName = prefix + " " + middle + " " + suffix;
            }
        } while (usedSchoolNames.contains(schoolName));
        
        usedSchoolNames.add(schoolName);
        return schoolName;
    }
    
    // Method to generate date of birth based on class and current year
    private static String generateDateOfBirth(int currentClass) {
        int currentYear = 2025; // Current year
        int baseAge = 5; // Age for Class 1 admission
        int studentAge = baseAge + (currentClass - 1); // Calculate age based on class
        int birthYear = currentYear - studentAge;
        
        // Generate random month (1-12) and day
        int month = 1 + (int) (Math.random() * 12);
        int day;
        
        // Handle different days per month
        if (month == 2) {
            // February - check for leap year
            boolean isLeapYear = (birthYear % 4 == 0 && birthYear % 100 != 0) || (birthYear % 400 == 0);
            day = 1 + (int) (Math.random() * (isLeapYear ? 29 : 28));
        } else if (month == 4 || month == 6 || month == 9 || month == 11) {
            // April, June, September, November have 30 days
            day = 1 + (int) (Math.random() * 30);
        } else {
            // January, March, May, July, August, October, December have 31 days
            day = 1 + (int) (Math.random() * 31);
        }
        
        return String.format("%02d/%02d/%d", day, month, birthYear);
    }
    
    // Method to generate blood group
    private static String generateBloodGroup() {
        String[] bloodGroups = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        // O+ is most common, O- is rarest, adjust probabilities
        double[] probabilities = {0.15, 0.05, 0.15, 0.05, 0.10, 0.02, 0.45, 0.03};
        
        double random = Math.random();
        double cumulative = 0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (random <= cumulative) {
                return bloodGroups[i];
            }
        }
        return "O+"; // Default
    }
    
    // Method to generate religion
    private static String generateReligion() {
        String[] religions = {"Hindu", "Muslim", "Christian", "Sikh", "Buddhist", "Jain", "Other"};
        // Approximate Indian religious demographics
        double[] probabilities = {0.80, 0.14, 0.02, 0.02, 0.008, 0.004, 0.028};
        
        double random = Math.random();
        double cumulative = 0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (random <= cumulative) {
                return religions[i];
            }
        }
        return "Hindu"; // Default
    }
    
    // Method to generate gender
    private static String generateGender() {
        return Math.random() < 0.5 ? "Male" : "Female";
    }
    
    // Method to generate parent occupation
    private static String generateParentOccupation() {
        String[] occupations = {
            "Software Engineer", "Doctor", "Teacher", "Businessman", "Government Employee", 
            "Lawyer", "Engineer", "Accountant", "Banker", "Consultant", "Manager", 
            "Sales Executive", "Marketing Manager", "Farmer", "Police Officer", 
            "Army Officer", "Nurse", "Pharmacist", "Architect", "Civil Engineer",
            "Mechanical Engineer", "Electrical Engineer", "CA", "Professor", "Principal",
            "Shopkeeper", "Contractor", "Real Estate Agent", "Insurance Agent", "Chef",
            "Driver", "Technician", "Artist", "Writer", "Journalist", "Photographer"
        };
        return occupations[(int) (Math.random() * occupations.length)];
    }
    
    // Method to check if concession is needed and generate concession details
    private static String[] generateConcessionDetails() {
        boolean needsConcession = Math.random() < 0.25; // 25% students need concession
        
        if (!needsConcession) {
            return new String[]{"No", "N/A"};
        }
        
        String[] concessionTypes = {
            "SC/ST Quota", "OBC Quota", "EWS Quota", "Physically Disabled", 
            "Single Parent", "Below Poverty Line", "Merit Scholarship", 
            "Sports Quota", "Defence Personnel", "Ex-Serviceman"
        };
        
        String concessionType = concessionTypes[(int) (Math.random() * concessionTypes.length)];
        return new String[]{"Yes", concessionType};
    }
    
    // Method to generate medical condition
    private static String generateMedicalCondition() {
        double hasCondition = Math.random();
        
        if (hasCondition < 0.85) { // 85% have no medical conditions
            return "None";
        }
        
        String[] conditions = {
            "Asthma", "Diabetes Type 1", "Epilepsy", "ADHD", "Dyslexia", 
            "Heart Condition", "Allergies", "Vision Impairment", "Hearing Impairment",
            "Autism Spectrum", "Learning Disability", "Speech Disorder"
        };
        
        return conditions[(int) (Math.random() * conditions.length)];
    }
    
    // Method to generate Aadhar card number
    private static String generateAadharNumber(java.util.Set<String> usedAadhars) {
        String aadharNumber;
        do {
            StringBuilder aadhar = new StringBuilder();
            // Generate 12 digit number
            for (int i = 0; i < 12; i++) {
                aadhar.append((int) (Math.random() * 10));
            }
            aadharNumber = aadhar.toString();
        } while (usedAadhars.contains(aadharNumber));
        
        usedAadhars.add(aadharNumber);
        // Format as XXXX XXXX XXXX
        return aadharNumber.substring(0, 4) + " " + aadharNumber.substring(4, 8) + " " + aadharNumber.substring(8, 12);
    }
    
    public static void main(String[] args) {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        
        // Database connection test
        System.out.println("=== TESTING DATABASE CONNECTION ===");
        try (Connection conn = getConnection()) {
            System.out.println("Successfully connected to PostgreSQL database!");
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            System.err.println("Please make sure PostgreSQL is running and credentials are correct.");
            scanner.close();
            return;
        }
        
        // Get user input for school structure
        System.out.println("\n=== SCHOOL MANAGEMENT SYSTEM ===");
        System.out.print("Enter number of classes in each school (e.g., 12 for classes 1-12): ");
        int numClasses = scanner.nextInt();
        
        System.out.print("Enter number of sections per class (e.g., 4 for sections A, B, C, D): ");
        int numSections = scanner.nextInt();
        
        System.out.print("Enter number of students per section: ");
        int studentsPerSection = scanner.nextInt();
        
        System.out.print("Enter number of schools to generate (max 10000): ");
        int numSchools = Math.min(scanner.nextInt(), 10000);
        
        // Calculate total students
        int totalStudentsPerSchool = numClasses * numSections * studentsPerSection;
        int totalStudents = numSchools * totalStudentsPerSchool;
        
        System.out.println("\n=== SCHOOL STRUCTURE ===");
        System.out.println("Number of Schools: " + numSchools);
        System.out.println("Classes per School: " + numClasses + " (Class 1 to Class " + numClasses + ")");
        System.out.println("Sections per Class: " + numSections);
        System.out.println("Students per Section: " + studentsPerSection);
        System.out.println("Total Students per School: " + totalStudentsPerSchool);
        System.out.println("Total Students Across All Schools: " + totalStudents);
        System.out.println("=====================================\n");
        
        // Create database tables
        System.out.println("=== CREATING DATABASE TABLES ===");
        createTables();
        
        // Generate unique school names and UUIDs
        java.util.Set<String> usedSchoolNames = new java.util.HashSet<>();
        java.util.List<String> schoolNames = new java.util.ArrayList<>();
        java.util.Map<String, String> schoolUUIDs = new java.util.HashMap<>();
        
        System.out.println("\n=== GENERATING SCHOOLS ===");
        for (int i = 0; i < numSchools; i++) {
            String schoolName = generateUniqueSchoolName(usedSchoolNames);
            String schoolUUID = java.util.UUID.randomUUID().toString();
            schoolNames.add(schoolName);
            schoolUUIDs.put(schoolName, schoolUUID);
            
            // Create student table for this school
            createStudentTable(schoolName);
            
            // Insert school data asynchronously
            final String finalSchoolName = schoolName;
            final String finalSchoolUUID = schoolUUID;
            executorService.submit(() -> insertSchool(finalSchoolUUID, finalSchoolName));
        }
        
        System.out.println("School generation completed!\n");
        
        // Generate student data
        String[] firstNames = {
                "Aadhav", "Aadhya", "Aahan", "Aaira", "Aarav", "Aaravi", "Aarya", "Aasha", "Aashna", "Aashvi",
                "Abha", "Abhay", "Abhi", "Abhijit", "Abhijeet", "Abhilash", "Abhinav", "Abhishek", "Adah", "Adarsh",
                "Adesh", "Adhira", "Aditi", "Aditya", "Advika", "Agam", "Ahaana", "Ahana", "Ahir", "Aiden",
                "Aisha", "Ajay", "Ajeet", "Ajit", "Akash", "Akshara", "Akshay", "Alka", "Alok", "Aman",
                "Amar", "Amara", "Amay", "Amber", "Amey", "Amit", "Amita", "Amitabh", "Amol", "Amrita",
                "Amruta", "Anand", "Ananya", "Anil", "Anish", "Anita", "Anjali", "Ankit", "Ankita", "Anmol",
                "Ansh", "Anshu", "Anshul", "Antara", "Anubhav", "Anurag", "Anushka", "Aparna", "Aparajita", "Araha",
                "Arav", "Archana", "Arhan", "Ariana", "Arjun", "Arnav", "Arohi", "Arpit", "Arsh", "Arshia",
                "Arun", "Aryan", "Asha", "Ashish", "Ashok", "Ashwin", "Astha", "Atharv", "Atul", "Avani",
                "Avinash", "Ayaan", "Ayesha", "Ayush", "Bakul", "Balraj", "Banita", "Barkha", "Bharat", "Bharti",
                "Bhavana", "Bhavesh", "Bhavya", "Bhumika", "Bindu", "Chanchal", "Chandan", "Chandra", "Chetan",
                "Chirag",
                "Daksh", "Darshan", "Deepa", "Deepak", "Deepika", "Dev", "Deva", "Devika", "Dhairya", "Dhanush",
                "Dhara", "Dhruv", "Diya", "Divya", "Divyansh", "Ekta", "Esha", "Farah", "Farhan", "Fatima",
                "Gaurav", "Gayatri", "Geet", "Geeta", "Gitika", "Gokul", "Gopal", "Govind", "Gulab", "Gunjan",
                "Hani", "Hanish", "Hansika", "Hardik", "Harini", "Harish", "Harsha", "Harshal", "Harsh", "Hasmukh",
                "Heena", "Hema", "Hemant", "Hina", "Hiral", "Hitesh", "Hrithik", "Ila", "Inaya", "Indira",
                "Indra", "Ira", "Irfan", "Isha", "Ishaan", "Ishita", "Jagan", "Jai", "Jaideep", "Jaidev",
                "Jaimin", "Jainish", "Jalpa", "Jamal", "Janaki", "Janvi", "Jasmin", "Jatin", "Jaya", "Jayant",
                "Jayesh", "Jayshree", "Jeet", "Jignesh", "Jigar", "Jinal", "Jisha", "Jiya", "Jyoti", "Kabir",
                "Kailash", "Kajal", "Kalpana", "Kamal", "Kamya", "Kanchan", "Kapil", "Karan", "Karish", "Kartik",
                "Kashish", "Kavya", "Keshav", "Ketan", "Ketki", "Keyur", "Khushi", "Kiran", "Kirti", "Komal",
                "Krishna", "Kriti", "Krupa", "Kunal", "Kundan", "Lahari", "Laksh", "Lakshmi", "Lalit", "Latika",
                "Lavanya", "Laxmi", "Leela", "Lila", "Madhav", "Madhavi", "Madhur", "Magan", "Maham", "Mahesh",
                "Maira", "Malini", "Manali", "Manas", "Manav", "Mandeep", "Manish", "Manju", "Mansi", "Mayank",
                "Maya", "Mayur", "Medha", "Meera", "Mehul", "Milan", "Milind", "Minal", "Mira", "Mitali",
                "Mithun", "Mohan", "Mohini", "Mukesh", "Mukti", "Naina", "Namita", "Nandini", "Naresh", "Natasha",
                "Nayan", "Neelam", "Neeta", "Neha", "Neil", "Nidhi", "Niharika", "Nikhil", "Nikita", "Nilesh",
                "Niral", "Nirav", "Nisha", "Nishant", "Nishita", "Nitesh", "Nitin", "Niya", "Om", "Ojas",
                "Paavan", "Pahal", "Palak", "Palash", "Pallavi", "Panav", "Paras", "Paresh", "Parth", "Parvati",
                "Pavan", "Payal", "Pooja", "Poonam", "Prachi", "Pradip", "Prakash", "Pranav", "Pranay", "Prasad",
                "Prashant", "Pratik", "Priya", "Priyanka", "Purvi", "Rachana", "Radha", "Radhika", "Rahul", "Raj",
                "Rajan", "Rajat", "Rajesh", "Rajiv", "Rakesh", "Ram", "Ramesh", "Rani", "Ravi", "Rekha",
                "Riddhi", "Ridhima", "Rishi", "Ritu", "Rohan", "Rohit", "Ruchi", "Rudra", "Ruhi", "Rupa",
                "Saanvi", "Sachin", "Sahil", "Saisha", "Sakshi", "Saloni", "Samir", "Sanaya", "Sandeep", "Sandip",
                "Sanjay", "Sanket", "Sanya", "Saral", "Saras", "Sarika", "Sarthak", "Sarvesh", "Satish", "Satyam",
                "Saumya", "Saurabh", "Savita", "Seema", "Sejal", "Shaan", "Shagun", "Shaili", "Shakti", "Shalini",
                "Shambhu", "Shantanu", "Shanti", "Sharad", "Sharan", "Sharath", "Sharma", "Shashank", "Shashi",
                "Shaurya",
                "Sheetal", "Shilpa", "Shital", "Shiv", "Shiva", "Shivam", "Shivangi", "Shivani", "Shobha", "Shreya",
                "Shridhar", "Shrikanth", "Shubham", "Shubhi", "Shuchi", "Siddharth", "Simran", "Sneha", "Soham",
                "Sohil",
                "Sonam", "Sonia", "Srikanth", "Srishti", "Sudarshan", "Sudha", "Sudhir", "Sugandha", "Suhani", "Sujata",
                "Sukanya", "Sumana", "Sumeet", "Sumit", "Sumitra", "Sunil", "Sunita", "Surbhi", "Suresh", "Surya",
                "Sushma", "Swara", "Swati", "Tanay", "Tanisha", "Tanvi", "Tapan", "Tarun", "Tejas", "Tina",
                "Tripti", "Tushar", "Udai", "Uday", "Ujjwal", "Uma", "Umesh", "Urvashi", "Vaibhav", "Vaidehi",
                "Vanita", "Varun", "Vasant", "Vedant", "Veer", "Veera", "Vibha", "Vibhav", "Vidya", "Vihan",
                "Vijay", "Vikas", "Vikram", "Vimal", "Vinay", "Vineet", "Vinita", "Vinod", "Vishal", "Vishnu",
                "Vivaan", "Vivek", "Yash", "Yasmin", "Yogesh", "Yuvraj", "Zara", "Zeenat", "Zoya", "Aadhavan",
                "Aadya", "Aagam", "Aahana", "Aakash", "Aaliya", "Aamara", "Aanand", "Aarathi", "Aarohi", "Aastha",
                "Abhinaya", "Abhiram", "Achal", "Adhvik", "Advait", "Agastya", "Aishwarya", "Ajith", "Akira", "Alisha",
                "Amaan", "Amaira", "Amisha", "Amogh", "Amrita", "Anaya", "Aneesha", "Angana", "Anirudh", "Anmolpreet",
                "Anshumaan", "Apeksha", "Aradhya", "Aranya", "Arijit", "Armaan", "Arshika", "Aryaman", "Ashika",
                "Asmita",
                "Atreya", "Avantika", "Avinav", "Ayaansh", "Ayushi", "Bhavika", "Bhuvan", "Chaitanya", "Charan",
                "Chithra",
                "Daksha", "Darshana", "Devak", "Dhanvi", "Dheeraj", "Dhyana", "Diksha", "Drishti", "Ekansh", "Eshaan",
                "Falak", "Gagan", "Gauranga", "Giriraj", "Haard", "Haridwar", "Harshaali", "Havish", "Hrisha", "Ishan",
                "Ivaan", "Jaanvi", "Jagrit", "Janhvi", "Jasmeet", "Jishan", "Joshan", "Kairav", "Kalash", "Kamakshi",
                "Kanisha", "Kavish", "Keerthi", "Kethan", "Khyati", "Kiaan", "Kimaya", "Kishna", "Krish", "Kshipra",
                "Laavanya", "Lakshay", "Lavish", "Mahika", "Maitreyee", "Mannat", "Manya", "Mayra", "Mehr", "Misha",
                "Moksh", "Myra", "Naira", "Nakul", "Nandana", "Navya", "Nehaa", "Nihaari", "Nirvaan", "Niyati",
                "Oorja", "Paarthav", "Paridhi", "Parnika", "Pihu", "Pranavi", "Pranshi", "Prisha", "Prithvi", "Raanav",
                "Reyansh", "Riaan", "Ridaan", "Rishabh", "Ritvik", "Riyaan", "Ruhanika", "Saanvika", "Saathvik",
                "Sadhvi",
                "Sahana", "Samaira", "Sanchit", "Saranya", "Sarvani", "Satvika", "Saumil", "Sehaj", "Shanaya",
                "Shaurya",
                "Shivansh", "Shivanya", "Shreyash", "Siddhi", "Simran", "Siya", "Smriti", "Srishti", "Suhaani",
                "Tanishka",
                "Teerth", "Tejasvi", "Trisha", "Urvi", "Vaanya", "Vaibhavi", "Vaidik", "Vansh", "Varsha", "Vedika",
                "Vihaan", "Vihaansh", "Viraj", "Vivaan", "Vyom", "Yagnesh", "Yamir", "Aarohan", "Abhas", "Achintya",
                "Advik", "Ahaan", "Aijaz", "Akshit", "Alok", "Amar", "Amrish", "Aniket", "Anmol", "Antariksh",
                "Arham", "Arihant", "Arjit", "Arshdeep", "Arun", "Astitva", "Avyaan", "Ayaan", "Bhargav", "Bhoomi",
                "Chiraag", "Chinmay", "Dakshesh", "Darpan", "Devang", "Dhaval", "Dhruvil", "Divij", "Eklavya", "Eshan",
                "Garvit", "Girik", "Haansh", "Hardik", "Havan", "Hriday", "Ishwar", "Jaideep", "Jairaj", "Janak",
                "Jeevan", "Jihan", "Kabeer", "Kailas", "Karan", "Karthik", "Kavyansh", "Keshav", "Kishan", "Krish",
                "Lagan", "Lakshit", "Lavitra", "Mahir", "Manan", "Manvik", "Mayank", "Mitansh", "Mohak", "Nalin",
                "Naman", "Naveen", "Nilay", "Nirvan", "Ojas", "Omkar", "Paavan", "Paraksh", "Parth", "Payas",
                "Pragun", "Prajwal", "Praneel", "Prateek", "Purab", "Raghav", "Rajveer", "Ranveer", "Rehan", "Rithik",
                "Ronak", "Rudransh", "Samarth", "Samit", "Sanidhya", "Sarth", "Satvir", "Shaan", "Shaurya", "Shiven",
                "Shray", "Shreyas", "Siddharth", "Sneh", "Sourabh", "Svanik", "Tanmay", "Taral", "Teerthankar",
                "Tushar",
                "Uddish", "Ujjval", "Varad", "Vardhan", "Vartik", "Vedansh", "Vihaan", "Vikash", "Vishrut", "Yatharth",
                "Yuvaan", "Aabha", "Aadrika", "Aahna", "Aaloka", "Aamira", "Aanika", "Aarushi", "Aashika", "Aatmika",
                "Aboli", "Achala", "Adishree", "Advika", "Ahaana", "Ahana", "Aishani", "Akanksha", "Alayna", "Alisha",
                "Amayra", "Ambar", "Amika", "Anahita", "Anaira", "Anandita", "Ananya", "Anavi", "Aneesha", "Anisha",
                "Anjika", "Ankita", "Anmita", "Anshika", "Antara", "Anubha", "Anukta", "Anushri", "Apeksha", "Apoorva",
                "Aradhana", "Aranaya", "Arika", "Arisha", "Aroha", "Arshia", "Arunima", "Ashima", "Asmara", "Asmi",
                "Aastha", "Atharva", "Avani", "Avanika", "Avika", "Ayushi", "Bani", "Barkha", "Bhairavi", "Bhanu",
                "Bhumika", "Binal", "Brinda", "Chaitra", "Chandni", "Charvi", "Chhavi", "Chiara", "Chitra", "Dakshita",
                "Damini", "Darshika", "Deeksha", "Devanshi", "Devika", "Dhanya", "Dharini", "Dhrithi", "Diya", "Divija",
                "Divya", "Drishya", "Eeksha", "Ekata", "Esha", "Gauri", "Gayatri", "Geetha", "Grishma", "Gunjan",
                "Hamsika", "Hansika", "Harini", "Harsha", "Harshaali", "Hema", "Hiral", "Hiya", "Inaya", "Indira",
                "Ipshita", "Iraa", "Ishika", "Ishita", "Jaanvi", "Jaisri", "Janavi", "Janya", "Jasmine", "Jaswitha",
                "Jaya", "Jeel", "Jia", "Jinal", "Jiya", "Joshika", "Jui", "Jyoti", "Kaavya", "Kairavi",
                "Kalpana", "Kamya", "Kanishka", "Kanya", "Kashvi", "Kavya", "Keertana", "Keisha", "Khushi", "Kimaya",
                "Kiran", "Kiya", "Komal", "Krisha", "Kriti", "Kshitija", "Laalsa", "Lagna", "Lakshita", "Laasya",
                "Lavanya", "Leela", "Lekha", "Lila", "Madhavi", "Mahika", "Maithili", "Malvika", "Manasvi", "Manya",
                "Maral", "Maya", "Mehak", "Meher", "Mehika", "Mehra", "Menaka", "Mira", "Mishka", "Mitali",
                "Moksha", "Naisha", "Nakshatra", "Nandini", "Nanika", "Nara", "Natasha", "Navika", "Navya", "Neelam",
                "Neeraja", "Neha", "Nethra", "Neya", "Nidhi", "Niharika", "Nikita", "Nilisha", "Nimisha", "Nira",
                "Nisha", "Nishka", "Nishtha", "Nithya", "Niya", "Nysa", "Ojasvi", "Omaira", "Oviya", "Pahal",
                "Paridhi", "Parisha", "Parul", "Pavani", "Pavitra", "Payal", "Pihu", "Poonam", "Poorvi", "Prachi",
                "Pragya", "Pranavi", "Prisha", "Priyanka", "Purvi", "Raahi", "Radhika", "Ragini", "Raisa", "Rajni",
                "Ranbiri", "Rashika", "Rayna", "Reeva", "Renuka", "Rhea", "Riddhima", "Rithika", "Riya", "Roopa",
                "Ruchi", "Ruhi", "Rupali", "Saanvi", "Saara", "Saatvi", "Sachi", "Sadhika", "Sahana", "Saira",
                "Saisha", "Sakshi", "Saloni", "Samara", "Samiksha", "Samira", "Samriddhi", "Sanaya", "Sanchita",
                "Sandhya",
                "Sanjana", "Sanya", "Sarani", "Sarayu", "Sarika", "Saroopa", "Sarvani", "Sasha", "Saumya", "Savi",
                "Savitha", "Sejal", "Serena", "Shaina", "Shaivi", "Shakti", "Shambhavi", "Shanaya", "Shanti", "Sharika",
                "Sharmila", "Sheena", "Shikha", "Shilpa", "Shimona", "Shivani", "Shobha", "Shreya", "Shristi", "Shubhi",
                "Shuchi", "Shweta", "Siddhi", "Simran", "Sinha", "Siya", "Smera", "Smita", "Smriti", "Sneha",
                "Sohini", "Sonam", "Sonali", "Sonia", "Srija", "Sristi", "Srishti", "Stuti", "Sudhira", "Suhana",
                "Suhani", "Sujata", "Sumana", "Sumati", "Sumitra", "Sunaina", "Sunayana", "Surbhi", "Sushma", "Swara",
                "Swathi", "Swati", "Tanishka", "Taniya", "Tanvi", "Tanya", "Tara", "Tarini", "Teja", "Tejaswi",
                "Tisha", "Trisha", "Tulika", "Urja", "Urmila", "Urvashi", "Utkarsha", "Uttara", "Vaani", "Vaidehi",
                "Vaishnavi", "Vanessa", "Vanita", "Varsha", "Varun", "Vedika", "Veena", "Veshali", "Vibha", "Vidhi",
                "Vidya", "Vihana", "Vijaya", "Vimala", "Vinita", "Visaka", "Vrinda", "Yami", "Yashaswini", "Yashika",
                "Yashvi", "Yuvika", "Zara", "Ziya", "Zohra"
        };

        String[] lastNames = {
                "Agarwal", "Agnihotri", "Agrawal", "Ahuja", "Anand", "Arora", "Bajaj", "Bansal", "Bhatia", "Chandra",
                "Chopra", "Dutta", "Garg", "Goyal", "Gupta", "Jain", "Kapoor", "Kumar", "Malhotra", "Mehta",
                "Mittal", "Nair", "Patel", "Rao", "Reddy", "Sharma", "Singh", "Sinha", "Tiwari", "Verma",
                "Yadav", "Acharya", "Adhikari", "Bhardwaj", "Chauhan", "Chowdhury", "Das", "Desai", "Ghosh", "Iyer",
                "Joshi", "Khan", "Kulkarni", "Mishra", "Mukherjee", "Pandey", "Prasad", "Rajan", "Roy", "Saxena",
                "Shah", "Shukla", "Trivedi", "Upadhyay", "Varma", "Aamir", "Abbas", "Abdulla", "Abraham", "Acharjee",
                "Adani", "Aditya", "Agarkar", "Aggarwal", "Ahmad", "Ahmed", "Ahsan", "Ajmera", "Akbar", "Akhtar",
                "Ali", "Amin", "Amrita", "Ananthan", "Anchan", "Aneja", "Angadi", "Antony", "Apte", "Arjun",
                "Asthana", "Athavale", "Awasthi", "Azad", "Babu", "Badami", "Bafna", "Baghel", "Bagwan", "Bahuguna",
                "Baid", "Bakshi", "Balakrishnan", "Balan", "Balasubramanian", "Bamrah", "Banerjee", "Bangera", "Bansod",
                "Barua",
                "Basak", "Basu", "Bathla", "Baveja", "Behl", "Bhandari", "Bharadwaj", "Bharti", "Bhatt", "Bhattacharya",
                "Bhattacharjee", "Bhavsar", "Bhosale", "Bhowmik", "Bhushan", "Biswas", "Bohra", "Borah", "Bose",
                "Burman",
                "Chakrabarti", "Chakraborty", "Chand", "Chandak", "Chandran", "Chatterjee", "Chaturvedi", "Chavan",
                "Chawla", "Chheda",
                "Chhetri", "Chitnis", "Chokshi", "Choudhary", "Chowdhary", "D'Souza", "Daga", "Dalal", "Damani",
                "Dandapat",
                "Dange", "Darji", "Datta", "Dave", "Dayal", "Debnath", "Deka", "Deshpande", "Devi", "Dhawan",
                "Dholakia", "Dhoot", "Dixit", "Doshi", "Dubey", "Dutta", "Dwivedi", "Engineer", "Fernandes", "Gadgil",
                "Gaikwad", "Gandhi", "Ganguly", "Gawai", "George", "Ghai", "Ghanshyam", "Goel", "Goenka", "Gohil",
                "Gokhale", "Golla", "Gopalakrishnan", "Gore", "Goswami", "Goyal", "Grover", "Gulati", "Gundecha",
                "Gupta",
                "Handa", "Harish", "Hegde", "Hiremath", "Hossain", "Husain", "Hussain", "Ibrahim", "Indurkar", "Israni",
                "Jacob", "Jadeja", "Jadhav", "Jaggi", "Jaiswal", "Jamwal", "Janardhanan", "Jangid", "Jayswal", "Jetley",
                "Jha", "Jindal", "John", "Joon", "Joseph", "Juneja", "Kadam", "Kadu", "Kale", "Kalita",
                "Kalla", "Kamat", "Kamble", "Kansal", "Kant", "Kapur", "Kar", "Karan", "Karkera", "Kashyap",
                "Kataria", "Katoch", "Kaul", "Kaushik", "Kelkar", "Khadka", "Khanna", "Khatri", "Khosla", "Khurana",
                "Kidwai", "Kirloskar", "Kohli", "Koirala", "Kotak", "Kothari", "Krishnamurthy", "Krishnan",
                "Kshirsagar", "Kumari",
                "Kundu", "Lal", "Lamba", "Latkar", "Lele", "Limaye", "Mahajan", "Maheshwari", "Maitra", "Majumdar",
                "Makkar", "Malhotra", "Malik", "Mallick", "Mandal", "Mane", "Mangal", "Manjrekar", "Mankar", "Marwah",
                "Mathur", "Meena", "Memon", "Menon", "Mhatre", "Misra", "Mitra", "Modi", "Mohan", "Mohapatra",
                "Mohanty", "Mookherjee", "More", "Mukherji", "Murthy", "Nadar", "Nag", "Nagarajan", "Nagpal", "Naik",
                "Nambiar", "Namdev", "Narang", "Narayana", "Narayan", "Nath", "Natarajan", "Nayak", "Nayar", "Nazir",
                "Nigam", "Nischal", "Ojha", "Pal", "Palaniappan", "Palkar", "Pandit", "Parekh", "Parikh", "Parmar",
                "Parulekar", "Paswan", "Patankar", "Pathak", "Patil", "Patkar", "Pattanaik", "Paul", "Pawar", "Phukan",
                "Pillai", "Poddar", "Prabhu", "Pradhan", "Prakash", "Priya", "Purohit", "Qureshi", "Raghu", "Rahman",
                "Rai", "Raichur", "Raizada", "Ram", "Raman", "Ramesh", "Ramnani", "Rangasamy", "Rathi", "Rathore",
                "Ratnam", "Raut", "Ravindran", "Rawat", "Rawal", "Razdan", "Redkar", "Rizvi", "Sabharwal", "Sachdeva",
                "Sadiq", "Sagar", "Sahay", "Sahni", "Sahu", "Saini", "Saket", "Salve", "Samant", "Samaranayake",
                "Sarangi", "Sarkar", "Sarma", "Sastry", "Sathya", "Satyanarayana", "Savarkar", "Sawant", "Sebastian",
                "Sen",
                "Sengupta", "Sethi", "Sethna", "Shaikh", "Shankar", "Shastri", "Shekar", "Shenoy", "Sheth", "Shinde",
                "Shirke", "Shrivastava", "Siddiqui", "Singhal", "Sinha", "Sivaramakrishnan", "Sobti", "Sood",
                "Srinivasan", "Subramanian",
                "Sud", "Sudarshan", "Sundaram", "Suri", "Swamy", "Tailor", "Talwar", "Tandon", "Tanna", "Thakkar",
                "Thakur", "Thampi", "Thanvi", "Thomas", "Thota", "Tikku", "Tiwary", "Tomar", "Tripathi", "Tyagi",
                "Udeshi", "Unnikrishnan", "Uppal", "Vaidya", "Vaishnav", "Vajpayee", "Vakharia", "Valecha", "Vanzara",
                "Vasudevan",
                "Vats", "Vedpathak", "Veer", "Veeraraghavan", "Velkar", "Venkatesan", "Vijayaraghavan", "Virmani",
                "Vishwanath", "Vohra",
                "Wagh", "Waghmare", "Wagle", "Warrier", "Wilson", "Yadav", "Yajnik", "Yash", "Zaveri", "Ahir",
                "Ajmani", "Akula", "Ambekar", "Andhare", "Ansari", "Arya", "Asrani", "Bakliwal", "Baliga", "Baloda",
                "Bambroo", "Bansal", "Barve", "Bedekar", "Begum", "Bendre", "Bhagat", "Bhalla", "Bhanot", "Bharucha",
                "Bhayani", "Bhojwani", "Bijlani", "Biswas", "Blah", "Bodke", "Bose", "Budhiraja", "Chablani",
                "Chaitanya",
                "Chand", "Chandna", "Chapal", "Chaudhari", "Chawla", "Chillar", "Chima", "Chinoy", "Chopade", "Chougle",
                "Chudgar", "Colaco", "Dabholkar", "Dadlani", "Dalvi", "Damle", "Dandavate", "Dani", "Dasmunshi",
                "Datar",
                "Daware", "Dedhia", "Deoras", "Devkar", "Dhameja", "Dhar", "Dharwadkar", "Dhingra", "Dhulia",
                "Didwania",
                "Doongursee", "Dudeja", "Duggal", "Dungrani", "Dutta", "Engineer", "Falnikar", "Gaba", "Gadkari",
                "Gala",
                "Gambhir", "Gandhi", "Gangadharan", "Garg", "Garware", "Gawde", "Gediya", "Gehlot", "Gera", "Ghag",
                "Ghatpande", "Ghoshal", "Gidwani", "Gilada", "Giri", "Godrej", "Gohel", "Gomez", "Gopal", "Goswami",
                "Goswami", "Gotmare", "Govani", "Govil", "Goyal", "Gulshan", "Gundecha", "Gupte", "Gupta", "Hada",
                "Handa", "Hardikar", "Hariharansadiq", "Hasan", "Heda", "Hegde", "Hemani", "Hete", "Hiremath", "Hooda",
                "Hussain", "Ingale", "Irani", "Ismail", "Iyer", "Jagdale", "Jalan", "Jambunathan", "Jangam",
                "Jaripatke",
                "Jasani", "Jathar", "Jauhari", "Jayswal", "Jeevanlal", "Jethani", "Jha", "Jhunjhunwala", "Jignesh",
                "Jitendra",
                "Jogi", "Jonnalgadda", "Joseph", "Joshi", "Juneja", "Kabra", "Kachru", "Kalgutkar", "Kalita", "Kambli",
                "Kampani", "Kanaujia", "Kandoi", "Kannan", "Kantilal", "Kapadia", "Kardile", "Karishma", "Karkhanis",
                "Karunakaran",
                "Kasbekar", "Kasliwal", "Katrak", "Kaushik", "Kedia", "Keswani", "Khadilkar", "Khalsa", "Khamar",
                "Khanna",
                "Kharkar", "Khatau", "Khemka", "Khilnani", "Khosla", "Khurana", "Kidambi", "Kinariwala", "Kirtikar",
                "Kishan",
                "Kodali", "Kohl", "Kolhatkar", "Kotak", "Kothari", "Krishnakumar", "Krishnan", "Kulkarni", "Kumar",
                "Kumthekar",
                "Kundan", "Kuppuswamy", "Kuthiala", "Ladha", "Lahiri", "Lajwanti", "Lall", "Landge", "Latkar", "Lele",
                "Lodha", "Lokhande", "Lunawat", "Luthra", "Madam", "Madan", "Mahajan", "Maheshwari", "Majithia",
                "Majumdar",
                "Malani", "Mallya", "Malviya", "Mamdani", "Manchanda", "Mane", "Mangal", "Mangeshkar", "Mani",
                "Manjrekar",
                "Mankad", "Mantri", "Marathe", "Marwadi", "Mathew", "Maurya", "Mehrotra", "Menda", "Menon", "Merchant",
                "Mhatre", "Milind", "Mishra", "Mittal", "Modak", "Modi", "Moghe", "Mohite", "Mokashi", "Molugu",
                "Monga", "More", "Morparia", "Mote", "Mudgal", "Mukesh", "Multani", "Munjal", "Muralidharan", "Murthy",
                "Musunuru", "Nadkarni", "Nagarkar", "Nagendra", "Nagpal", "Naidu", "Naikwadi", "Nair", "Namboodiri",
                "Nanavati",
                "Narang", "Narayan", "Narayanan", "Narula", "Nath", "Nayar", "Negi", "Nehru", "Nemade", "Nimbalkar",
                "Nimkar", "Nischal", "Nitin", "Oberoi", "Ojha", "Oke", "Oza", "Padukone", "Pagarani", "Pahuja",
                "Pal", "Palit", "Palkar", "Panagariya", "Pandey", "Pandit", "Panicker", "Parab", "Parajuli", "Paranjpe",
                "Parekh", "Parikh", "Parkar", "Parmar", "Parulekar", "Pasricha", "Patel", "Pathak", "Patil", "Patkar",
                "Patney", "Patra", "Paul", "Pawar", "Pereira", "Phanse", "Pillai", "Pimplikar", "Poddar", "Pokharkar",
                "Prabhu", "Pradhan", "Prakash", "Pramila", "Prasad", "Prashant", "Pratap", "Purohit", "Puri", "Pushpa",
                "Raavi", "Raghavan", "Raghuvir", "Raina", "Raja", "Rajagopalan", "Rajan", "Rajaratnam", "Rajesh",
                "Rajiv",
                "Raju", "Ram", "Raman", "Ramani", "Ramanathan", "Ramaswamy", "Ramesh", "Ranganathan", "Rangwala", "Rao",
                "Rastogi", "Rathi", "Rathod", "Raut", "Ravindra", "Rawat", "Reddy", "Revanna", "Rizvi", "Rohatgi",
                "Roy", "Sabharwal", "Sachdeva", "Sadanandan", "Sagar", "Sahay", "Sahni", "Sahu", "Saini", "Sajjan",
                "Salunkhe", "Samant", "Sampat", "Sandeep", "Sangam", "Sanghvi", "Saniyal", "Sanyal", "Saraf", "Sarangi",
                "Sarawagi", "Sarkar", "Sarma", "Sarode", "Sastry", "Sathaye", "Sathe", "Satra", "Sawant", "Saxena",
                "Sebastian", "Sen", "Sengar", "Sengupta", "Sethi", "Sethna", "Sethuraman", "Setu", "Shah", "Shaikh",
                "Shankar", "Sharma", "Shastri", "Sheikh", "Shenoy", "Sheth", "Shirodkar", "Shivakumar", "Shukla",
                "Siddiqui",
                "Singh", "Singhal", "Singla", "Sinha", "Sivaramakrishnan", "Sobti", "Sodhi", "Sondhi", "Sood",
                "Sreenivasan",
                "Srinivasan", "Subramanian", "Sud", "Sundaram", "Suri", "Suryavanshi", "Swami", "Talwar", "Tandon",
                "Tanna",
                "Tawde", "Thadani", "Thakkar", "Thakur", "Thampi", "Thanvi", "Thomas", "Thota", "Tikku", "Tiwari",
                "Tiwary", "Tomar", "Tripathi", "Trivedi", "Tyagi", "Udeshi", "Unnikrishnan", "Uppal", "Usha", "Vaidya",
                "Vajpayee", "Vakharia", "Valecha", "Vanzara", "Varghese", "Varma", "Vasudevan", "Vats", "Vedpathak",
                "Veer",
                "Veeraraghavan", "Velkar", "Venkatesan", "Verma", "Vij", "Vijayaraghavan", "Virani", "Virmani",
                "Vishwanath", "Vohra",
                "Vora", "Vyas", "Wagh", "Waghmare", "Wagle", "Warrier", "Wilson", "Yadav", "Yajnik", "Yash",
                "Zaveri", "Zutshi"
        };
        // Set to track used phone numbers and Aadhar numbers
        java.util.Set<String> usedPhoneNumbers = new java.util.HashSet<>();
        java.util.Set<String> usedAadhars = new java.util.HashSet<>();
        java.util.Set<String> usedNameCombinations = new java.util.HashSet<>();
        
        System.out.println("=== GENERATING AND SAVING STUDENT DATA ===");
        System.out.println("Total students to generate: " + totalStudents);
        System.out.println("Progress will be shown every 100 students...\n");
        
        // Start progress monitoring
        executorService.submit(() -> {
            while (processedStudents.get() < totalStudents) {
                try {
                    Thread.sleep(2000); // Update every 2 seconds
                    int current = processedStudents.get();
                    double percentage = (current * 100.0) / totalStudents;
                    System.out.printf("Progress: %d/%d students (%.1f%%) processed\n", 
                        current, totalStudents, percentage);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        int currentSchoolIndex = 0;
        int currentClass = 1;
        char currentSection = 'A';
        int currentRollNo = 1;
        
        for (int studentIndex = 0; studentIndex < totalStudents; studentIndex++) {
            // Generate unique name combination
            String fullName, firstName, lastName;
            do {
                firstName = firstNames[(int) (Math.random() * firstNames.length)];
                lastName = lastNames[(int) (Math.random() * lastNames.length)];
                fullName = firstName + " " + lastName;
            } while (usedNameCombinations.contains(fullName));
            
            usedNameCombinations.add(fullName);

            // Guardian gets same last name but different first name
            int guardianFirstNameIndex;
            do {
                guardianFirstNameIndex = (int) (Math.random() * firstNames.length);
            } while (firstNames[guardianFirstNameIndex].equals(firstName));

            String guardianName = firstNames[guardianFirstNameIndex] + " " + lastName;
            
            // Generate UUID v4 for the student
            String studentUUID = java.util.UUID.randomUUID().toString();
            
            // Generate all student details
            String gender = generateGender();
            String bloodGroup = generateBloodGroup();
            String dateOfBirth = generateDateOfBirth(currentClass);
            String aadharNumber = generateAadharNumber(usedAadhars);
            String religion = generateReligion();
            String parentOccupation = generateParentOccupation();
            String[] concessionDetails = generateConcessionDetails();
            String concessionNeeded = concessionDetails[0];
            String concessionType = concessionDetails[1];
            String medicalCondition = generateMedicalCondition();
            
            // Generate unique phone numbers
            String studentPhone = generateUniquePhoneNumber(usedPhoneNumbers);
            String guardianPhone = generateUniquePhoneNumber(usedPhoneNumbers);
            
            // Assign school, class, section, and roll number
            String schoolName = schoolNames.get(currentSchoolIndex);
            String classStr = "Class " + currentClass;
            String sectionStr = String.valueOf(currentSection);
            
            // Generate MinIO URL for student image
            String schoolNameEncoded = schoolName.replace(" ", "%20").replace(".", "").replace("'", "");
            String imageUrl = "minio.studentdata.tech/" + schoolNameEncoded + "/" + studentUUID + "/profile.png";
            
            // Create student data object
            StudentData student = new StudentData(
                studentUUID, fullName, guardianName, gender, bloodGroup, dateOfBirth,
                aadharNumber, classStr, sectionStr, currentRollNo, schoolName, religion,
                parentOccupation, concessionNeeded, concessionType, medicalCondition,
                studentPhone, guardianPhone, imageUrl
            );
            
            // Insert student data asynchronously
            executorService.submit(() -> insertStudent(student));
            
            // Update roll number, section, class, and school
            currentRollNo++;
            if (currentRollNo > studentsPerSection) {
                currentRollNo = 1;
                currentSection++;
                if (currentSection > 'A' + numSections - 1) {
                    currentSection = 'A';
                    currentClass++;
                    if (currentClass > numClasses) {
                        currentClass = 1;
                        currentSchoolIndex++;
                        if (currentSchoolIndex >= numSchools) {
                            currentSchoolIndex = 0; // Wrap around if we have more students than school capacity
                        }
                    }
                }
            }
        }

        // Wait for all tasks to complete
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        
        System.out.println("\n=== GENERATION COMPLETE ===");
        System.out.println("Total students generated: " + totalStudents);
        System.out.println("Total schools generated: " + numSchools);
        System.out.println("All data saved to PostgreSQL database");
        
        // Print database summary
        System.out.println("\n=== DATABASE SUMMARY ===");
        System.out.println("School table: Contains " + numSchools + " schools with UUID and names");
        System.out.println("Student tables: " + numSchools + " separate tables (one per school)");
        System.out.println("Table naming pattern: students_{school_name_sanitized}");
        System.out.println("Each student table contains comprehensive student information");
        
        System.out.println("\n=== SAMPLE SQL QUERIES ===");
        System.out.println("-- View all schools:");
        System.out.println("SELECT * FROM school_table;");
        System.out.println("\n-- View students from a specific school (example):");
        String sampleTableName = getStudentTableName(schoolNames.get(0));
        System.out.println("SELECT * FROM " + sampleTableName + " LIMIT 10;");
        System.out.println("\n-- Count students by gender in a school:");
        System.out.println("SELECT gender, COUNT(*) FROM " + sampleTableName + " GROUP BY gender;");
        
        scanner.close();
    }
}
