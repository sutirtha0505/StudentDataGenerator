import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class Main {
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/student_management";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "Sutirtha_05@Postgress"; // Change this to your PostgreSQL password
    
    // Connection pool for database operations
    private static HikariDataSource dataSource;
    
    // System hardware detection and thread allocation
    private static int systemCores;
    private static int optimalThreadCount;
    
    // Thread pool for database operations - dynamically allocated based on system specs
    private static ExecutorService executorService;
    private static AtomicInteger processedStudents = new AtomicInteger(0);
    private static AtomicInteger processedSchools = new AtomicInteger(0);
    private static volatile boolean processingComplete = false;
    
    // Batch processing configuration
    private static final int BATCH_SIZE = 1000; // Process students in batches of 1000
    private static final java.util.concurrent.BlockingQueue<StudentData> studentBatchQueue = new LinkedBlockingQueue<>();
    private static volatile boolean batchProcessingActive = false;
    
    // Memory-efficient LRU cache for tracking used values
    private static final int MAX_CACHE_SIZE = 100000; // Limit cache size to prevent memory exhaustion
    
    // Simple LRU Cache implementation
    static class LRUCache<T> {
        private final int maxSize;
        private final java.util.LinkedHashMap<T, Boolean> cache;
        
        public LRUCache(int maxSize) {
            this.maxSize = maxSize;
            this.cache = new java.util.LinkedHashMap<T, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<T, Boolean> eldest) {
                    return size() > LRUCache.this.maxSize;
                }
            };
        }
        
        public synchronized boolean contains(T key) {
            return cache.containsKey(key);
        }
        
        public synchronized void add(T key) {
            cache.put(key, Boolean.TRUE);
        }
        
        public synchronized int size() {
            return cache.size();
        }
    }
    
    // Method to detect system hardware and initialize optimal thread allocation
    private static void initializeSystemDetectionAndThreadPool() {
        System.out.println("=== SYSTEM HARDWARE DETECTION ===");
        
        // Get system cores (physical + logical)
        systemCores = Runtime.getRuntime().availableProcessors();
        
        // Get additional system information
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        
        System.out.println("System Information:");
        System.out.println("  Operating System: " + osName + " " + osVersion);
        System.out.println("  Java Version: " + javaVersion + " (" + javaVendor + ")");
        System.out.println("  CPU Cores detected: " + systemCores);
        
        // For this application, we'll use a conservative approach:
        // - For CPU-bound tasks: use number of cores
        // - For I/O-bound tasks (database operations): use cores * 2
        // - Cap at reasonable maximum to prevent resource exhaustion
        
        // Database operations are I/O-bound, so we can use more threads than cores
        int maxThreads = systemCores * 2;
        
        // Set reasonable bounds: minimum 2, maximum 50
        optimalThreadCount = Math.max(2, Math.min(maxThreads, 50));
        
        // Initialize the thread pool with optimal count
        executorService = Executors.newFixedThreadPool(optimalThreadCount);
        
        System.out.println("Thread Allocation:");
        System.out.println("  Strategy: I/O-bound (database operations)");
        System.out.println("  Optimal thread count: " + optimalThreadCount);
        System.out.println("  Thread pool initialized with " + optimalThreadCount + " threads");
        
        // Additional system information
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        
        System.out.println("Memory Information:");
        System.out.println("  Max Memory: " + formatBytes(maxMemory));
        System.out.println("  Total Memory: " + formatBytes(totalMemory));
        System.out.println("  Free Memory: " + formatBytes(freeMemory));
        System.out.println("=====================================");
    }
    
    // Helper method to format bytes in human-readable format
    private static String formatBytes(long bytes) {
        if (bytes == Long.MAX_VALUE) return "Unlimited";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }
    
    // Method to initialize database connection pool
    private static void initializeConnectionPool() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setDriverClassName("org.postgresql.Driver");
            
            // Dynamic connection pool settings based on system capabilities
            // Generally, connection pool size should be slightly larger than thread count
            int maxPoolSize = Math.max(optimalThreadCount + 5, 10); // At least 10, or threads + 5
            int minIdle = Math.max(optimalThreadCount / 4, 2);       // 25% of threads, minimum 2
            
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(60000);     // 60 seconds (increased from 10)
            config.setIdleTimeout(300000);          // 5 minutes
            config.setMaxLifetime(1800000);         // 30 minutes
            config.setLeakDetectionThreshold(60000); // 1 minute
            
            System.out.println("Connection pool configured dynamically:");
            System.out.println("  Maximum connections: " + maxPoolSize);
            System.out.println("  Minimum idle connections: " + minIdle);
            System.out.println("  Ratio: " + String.format("%.1f", (double)maxPoolSize / optimalThreadCount) + " connections per thread");
            
            // Performance settings
            config.addDataSourceProperty("socketTimeout", "300"); // 5 minutes (increased from 30 seconds)
            config.addDataSourceProperty("loginTimeout", "60");   // 60 seconds (increased from 10)
            config.addDataSourceProperty("connectTimeout", "30"); // 30 seconds (increased from 10)
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.addDataSourceProperty("prepareThreshold", "1"); // Enable prepared statement caching
            config.addDataSourceProperty("preparedStatementCacheQueries", "256");
            config.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
            
            dataSource = new HikariDataSource(config);
            System.out.println("Connection pool initialized successfully with " + config.getMaximumPoolSize() + " max connections");
            
        } catch (Exception e) {
            System.err.println("Failed to initialize connection pool: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    // Method to establish database connection from pool
    private static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initializeConnectionPool();
        }
        return dataSource.getConnection();
    }
    
    // Method to close connection pool and executor service (call at end of program)
    private static void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Connection pool closed successfully");
        }
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                // Wait for existing tasks to complete
                if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    System.out.println("Executor service force shutdown");
                } else {
                    System.out.println("Executor service closed successfully");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                System.out.println("Executor service shutdown interrupted");
            }
        }
    }
    
    // Method to start batch processor
    private static void startBatchProcessor() {
        batchProcessingActive = true;
        executorService.submit(() -> {
            java.util.List<StudentData> batch = new java.util.ArrayList<>();
            int consecutiveErrors = 0;
            int maxConsecutiveErrors = 10;
            
            while (batchProcessingActive || !studentBatchQueue.isEmpty()) {
                try {
                    // Try to get a student with timeout
                    StudentData student = studentBatchQueue.poll(1, TimeUnit.SECONDS);
                    
                    if (student != null) {
                        batch.add(student);
                        
                        // Process batch when it reaches the size limit or we have a timeout
                        if (batch.size() >= BATCH_SIZE) {
                            try {
                                processBatch(batch);
                                batch.clear();
                                consecutiveErrors = 0; // Reset error counter on success
                            } catch (Exception e) {
                                consecutiveErrors++;
                                System.err.println("Batch processing error (attempt " + consecutiveErrors + "/" + maxConsecutiveErrors + "): " + e.getMessage());
                                
                                if (consecutiveErrors >= maxConsecutiveErrors) {
                                    System.err.println("CRITICAL: Too many consecutive batch processing errors. Stopping batch processor.");
                                    System.err.println("Current batch size: " + batch.size() + " students");
                                    System.err.println("Queue size: " + studentBatchQueue.size() + " students");
                                    batchProcessingActive = false;
                                    break;
                                }
                                
                                // Wait before retry
                                Thread.sleep(2000 * consecutiveErrors);
                            }
                        }
                    } else if (!batch.isEmpty()) {
                        // Process remaining batch after timeout (for final cleanup)
                        try {
                            processBatch(batch);
                            batch.clear();
                            consecutiveErrors = 0;
                        } catch (Exception e) {
                            System.err.println("Error processing final batch: " + e.getMessage());
                        }
                    }
                    
                } catch (InterruptedException e) {
                    // Process any remaining batch before exiting
                    if (!batch.isEmpty()) {
                        try {
                            processBatch(batch);
                        } catch (Exception ex) {
                            System.err.println("Error processing batch during shutdown: " + ex.getMessage());
                        }
                    }
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Process any final remaining batch
            if (!batch.isEmpty()) {
                processBatch(batch);
            }
            
            System.out.println("Batch processor stopped");
        });
    }
    
    // Method to stop batch processor
    private static void stopBatchProcessor() {
        batchProcessingActive = false;
    }
    
    // Method to process a batch of students
    private static void processBatch(java.util.List<StudentData> students) {
        if (students.isEmpty()) return;
        
        // Group students by table (school)
        java.util.Map<String, java.util.List<StudentData>> groupedBySchool = new java.util.HashMap<>();
        for (StudentData student : students) {
            String tableName = getStudentTableName(student.schoolName);
            groupedBySchool.computeIfAbsent(tableName, k -> new java.util.ArrayList<>()).add(student);
        }
        
        // Process each school's students in a batch
        for (java.util.Map.Entry<String, java.util.List<StudentData>> entry : groupedBySchool.entrySet()) {
            String tableName = entry.getKey();
            java.util.List<StudentData> schoolStudents = entry.getValue();
            insertStudentBatch(tableName, schoolStudents);
        }
    }
    
    // Method to insert a batch of students for a specific school
    private static void insertStudentBatch(String tableName, java.util.List<StudentData> students) {
        String sql = String.format("""
            INSERT INTO %s (student_uuid, full_name, guardian_name, gender, blood_group, 
            birth_date, aadhar_card, class_name, section, roll_no, religion, 
            parent_occupation, concession_needed, concession_type, medical_condition, 
            student_phone, guardian_phone, image_url, stream) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName);
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                // Disable auto-commit for batch processing
                conn.setAutoCommit(false);
                
                for (StudentData student : students) {
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
                    pstmt.setString(19, student.stream);
                    
                    pstmt.addBatch();
                }
                
                // Execute the batch
                int[] results = pstmt.executeBatch();
                conn.commit();
                
                // Update processed count
                int processed = processedStudents.addAndGet(results.length);
                
                // Show progress every 1000 students for large datasets, 100 for smaller ones
                int progressInterval = processed > 1000000 ? 1000 : 100;
                if (processed % progressInterval == 0) {
                    System.out.println("Students processed: " + processed);
                }
                
                return; // Success, exit retry loop
                
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    System.err.println("Failed to insert batch of " + students.size() + " students after " + maxRetries + " attempts: " + e.getMessage());
                    // Skip this batch and continue with next one
                    return;
                } else {
                    System.err.println("Batch insert failed, retrying... (" + retryCount + "/" + maxRetries + ")");
                    try {
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
    
    // Method to queue student for batch processing
    private static void queueStudentForBatch(StudentData student) {
        try {
            studentBatchQueue.put(student);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Fall back to individual insertion if queue is interrupted
            insertStudent(student);
        }
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
                    stream VARCHAR(20), -- Added stream column for classes 11 and 12
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
    
    // Method to insert student data with retry logic
    private static void insertStudent(StudentData student) {
        String tableName = getStudentTableName(student.schoolName);
        String sql = String.format("""
            INSERT INTO %s (student_uuid, full_name, guardian_name, gender, blood_group, 
            birth_date, aadhar_card, class_name, section, roll_no, religion, 
            parent_occupation, concession_needed, concession_type, medical_condition, 
            student_phone, guardian_phone, image_url, stream) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, tableName);
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
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
                pstmt.setString(19, student.stream);
                
                pstmt.executeUpdate();
                
                int processed = processedStudents.incrementAndGet();
                // Show progress every 1000 students for large datasets, 100 for smaller ones
                int progressInterval = processed > 1000000 ? 1000 : 100;
                if (processed % progressInterval == 0) {
                    System.out.println("Students processed: " + processed);
                }
                return; // Success, exit retry loop
                
            } catch (SQLException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    System.err.println("Failed to insert student " + student.fullName + " after " + maxRetries + " attempts: " + e.getMessage());
                } else {
                    // Wait before retry
                    try {
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
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
        String schoolName, imageUrl, stream; // Added stream field
        int rollNo;
        
        StudentData(String studentUUID, String fullName, String guardianName, String gender, 
                   String bloodGroup, String birthDate, String aadharNumber, String className, 
                   String section, int rollNo, String schoolName, String religion, 
                   String parentOccupation, String concessionNeeded, String concessionType, 
                   String medicalCondition, String studentPhone, String guardianPhone, String imageUrl,
                   String stream) { // Added stream parameter
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
            this.stream = stream; // Added stream assignment
        }
    }
    
    // Method to generate unique Indian phone numbers
    private static String generateUniquePhoneNumber(LRUCache<String> usedNumbers) {
        String phoneNumber;
        int attempts = 0;
        do {
            // Generate 10 digit number starting with 6, 7, 8, or 9
            int firstDigit = 6 + (int) (Math.random() * 4); // Random number between 6-9
            StringBuilder number = new StringBuilder("+91" + firstDigit);
            
            // Generate remaining 9 digits
            for (int i = 0; i < 9; i++) {
                number.append((int) (Math.random() * 10));
            }
            
            phoneNumber = number.toString();
            attempts++;
            
            // Prevent infinite loops in case cache is full
            if (attempts > 1000) {
                break;
            }
        } while (usedNumbers.contains(phoneNumber));
        
        usedNumbers.add(phoneNumber);
        return phoneNumber;
    }
    
    // Method to generate unique school names
    private static String generateUniqueSchoolName(java.util.Set<String> usedSchoolNames) {
        // Expanded prefixes (200+ options)
        String[] schoolPrefixes = {
            "St.", "Holy", "Sacred", "Divine", "Blessed", "Mount", "Little", "Bright", "Golden", "Silver",
            "Green", "Blue", "Red", "New", "Modern", "Progressive", "Advanced", "Premier", "Elite", "Excellence",
            "Victory", "Success", "Wisdom", "Knowledge", "Learning", "Future", "Hope", "Dream", "Star", "Sun",
            "Moon", "Rainbow", "Crystal", "Diamond", "Pearl", "Emerald", "Ruby", "Sapphire", "Lotus", "Rose",
            "Noble", "Royal", "Grand", "Great", "Glorious", "Magnificent", "Majestic", "Supreme", "Ultimate", "Perfect",
            "Ideal", "Prime", "Pure", "True", "Real", "Genuine", "Authentic", "Original", "Classic", "Eternal",
            "Infinite", "Universal", "Global", "International", "National", "Regional", "Central", "Metropolitan", "Urban", "Rural",
            "Eastern", "Western", "Northern", "Southern", "Coastal", "Highland", "Valley", "Mountain", "River", "Forest",
            "Garden", "Park", "Grove", "Field", "Meadow", "Prairie", "Desert", "Ocean", "Lake", "Stream",
            "Dawn", "Dusk", "Morning", "Evening", "Noon", "Midnight", "Spring", "Summer", "Autumn", "Winter",
            "Pioneer", "Leader", "Champion", "Winner", "Master", "Expert", "Skilled", "Talented", "Gifted", "Brilliant",
            "Bright", "Shining", "Glowing", "Radiant", "Luminous", "Sparkling", "Dazzling", "Gleaming", "Twinkling", "Flickering",
            "Ancient", "Historic", "Traditional", "Cultural", "Heritage", "Legacy", "Memorial", "Tribute", "Honor", "Glory",
            "Peace", "Harmony", "Unity", "Brotherhood", "Fellowship", "Community", "Society", "Foundation", "Trust", "Mission",
            "Vision", "Innovation", "Creation", "Discovery", "Exploration", "Adventure", "Journey", "Quest", "Path", "Way",
            "Light", "Beacon", "Guide", "Mentor", "Teacher", "Educator", "Scholar", "Academic", "Intellectual", "Thoughtful",
            "Creative", "Artistic", "Musical", "Literary", "Poetic", "Dramatic", "Scientific", "Technical", "Digital", "Cyber",
            "Cosmic", "Stellar", "Galactic", "Universal", "Planetary", "Solar", "Lunar", "Celestial", "Heavenly", "Angelic",
            "Alpha", "Beta", "Gamma", "Delta", "Omega", "Prime", "First", "Second", "Third", "Final",
            "Neo", "Ultra", "Super", "Mega", "Macro", "Micro", "Mini", "Maxi", "Multi", "Poly"
        };
        
        // Expanded middle names (300+ options)
        String[] schoolMiddles = {
            "Angels", "Mary", "Xavier", "Francis", "Joseph", "Michael", "Gabriel", "Paul", "Peter", "John",
            "Thomas", "Anthony", "Stephen", "Lawrence", "Vincent", "Augustine", "Benedict", "Dominic", "Carmel",
            "Teresa", "Agnes", "Catherine", "Margaret", "Elizabeth", "Anne", "Grace", "Faith", "Hope", "Joy",
            "Peace", "Light", "Dawn", "Morning", "Evening", "Spring", "Summer", "Autumn", "Winter", "Valley",
            "Hills", "Heights", "Gardens", "Park", "Grove", "Woods", "Forest", "River", "Lake", "Ocean",
            "Matthew", "Mark", "Luke", "James", "Andrew", "Philip", "Bartholomew", "Timothy", "Christopher", "Nicholas",
            "David", "Daniel", "Samuel", "Nathan", "Joshua", "Caleb", "Benjamin", "Isaac", "Jacob", "Aaron",
            "Moses", "Abraham", "Noah", "Adam", "Solomon", "Elijah", "Isaiah", "Jeremiah", "Ezekiel", "Hosea",
            "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
            "Malachi", "Raphael", "Uriel", "Seraphim", "Cherubim", "Throne", "Dominion", "Virtue", "Power", "Principality",
            "Sarah", "Rebecca", "Rachel", "Leah", "Ruth", "Esther", "Judith", "Deborah", "Miriam", "Hannah",
            "Abigail", "Bathsheba", "Tamar", "Gomer", "Hosanna", "Alleluia", "Gloria", "Magnificat", "Sanctus", "Benedictus",
            "Agnus", "Dei", "Kyrie", "Eleison", "Christe", "Pax", "Vobiscum", "Dominus", "Tecum", "Spiritus",
            "Krishnan", "Govind", "Madhav", "Keshav", "Narayan", "Vishnu", "Shiva", "Brahma", "Indra", "Varuna",
            "Agni", "Vayu", "Surya", "Chandra", "Prithvi", "Akash", "Jal", "Tej", "Vaayu", "Bhumi",
            "Ganga", "Yamuna", "Saraswati", "Narmada", "Kaveri", "Godavari", "Krishna", "Tapti", "Mahanadi", "Brahmaputra",
            "Himalaya", "Vindhya", "Sahyadri", "Aravalli", "Nilgiri", "Satpura", "Kanchenjunga", "Everest", "Annapurna", "Dhaulagiri",
            "Gandhi", "Nehru", "Patel", "Bose", "Tilak", "Gokhale", "Rao", "Tagore", "Vivekananda", "Aurobindo",
            "Ramakrishna", "Dayananda", "Kabir", "Tulsidas", "Surdas", "Mirabai", "Rahim", "Raskhan", "Bihari", "Bharatendu",
            "Premchand", "Prasad", "Pant", "Nirala", "Mahadevi", "Ajneya", "Dinkar", "Bachchan", "Shukla", "Gupta",
            "Sharma", "Verma", "Agarwal", "Bansal", "Mittal", "Jain", "Shah", "Mehta", "Chandra", "Kumar",
            "Devi", "Mata", "Bai", "Kumari", "Sundari", "Lakshmi", "Durga", "Kali", "Parvati", "Radha",
            "Sita", "Gita", "Yamuna", "Ganga", "Saraswati", "Gayatri", "Savitri", "Sandhya", "Usha", "Asha",
            "Nisha", "Disha", "Risha", "Priya", "Maya", "Lila", "Kala", "Nila", "Hira", "Sona",
            "Chandi", "Moti", "Panna", "Manik", "Ratna", "Mukta", "Swarna", "Rajat", "Tamra", "Loha",
            "Veda", "Purana", "Upanishad", "Gita", "Ramayana", "Mahabharata", "Bhagavata", "Devi", "Vishnu", "Shiva",
            "Shakti", "Shanti", "Mukti", "Bhakti", "Prema", "Karuna", "Daya", "Kshama", "Satya", "Dharma",
            "Artha", "Kama", "Moksha", "Yoga", "Meditation", "Pranayama", "Asana", "Mudra", "Mantra", "Yantra",
            "Mandala", "Chakra", "Kundalini", "Samadhi", "Nirvana", "Enlightenment", "Awakening", "Consciousness", "Awareness", "Mindfulness"
        };
        
        // Expanded suffixes (100+ options)
        String[] schoolSuffixes = {
            "Public School", "Higher Secondary School", "High School", "Senior Secondary School", 
            "English Medium School", "Model School", "International School", "Academy", "Institute",
            "Educational Institute", "Learning Center", "Study Center", "Knowledge Hub", "Vidyalaya",
            "Vidya Mandir", "Vidya Niketan", "Vidya Bhawan", "Vidya Kendra", "Shiksha Niketan",
            "Bal Vidyalaya", "Convent School", "Mission School", "Memorial School", "Foundation School",
            "Central School", "Kendriya Vidyalaya", "Jawahar Navodaya Vidyalaya", "Government School", "Municipal School",
            "Corporation School", "Zilla Panchayat School", "Block School", "Gram Panchayat School", "Aided School",
            "Unaided School", "Minority School", "Linguistic Minority School", "Religious Minority School", "Special School",
            "Inclusive School", "Integrated School", "Composite School", "Senior School", "Junior School",
            "Primary School", "Elementary School", "Middle School", "Secondary School", "Preparatory School",
            "Kindergarten", "Pre-School", "Nursery School", "Play School", "Day Care Center",
            "Boarding School", "Residential School", "Hostel School", "Day School", "Co-Educational School",
            "Boys School", "Girls School", "Women's College", "Men's College", "Unisex Institute",
            "Technical Institute", "Polytechnic", "Engineering College", "Medical College", "Dental College",
            "Pharmacy College", "Nursing College", "Teacher Training College", "B.Ed College", "D.Ed College",
            "Arts College", "Science College", "Commerce College", "Management Institute", "Business School",
            "Law College", "Agricultural College", "Veterinary College", "Forestry College", "Fisheries College",
            "Technology Institute", "Computer Institute", "IT Academy", "Software Academy", "Hardware Institute",
            "Electronics Institute", "Telecommunications Academy", "Robotics Academy", "AI Institute", "Data Science Academy",
            "Research Institute", "Innovation Center", "Incubation Center", "Startup Academy", "Entrepreneurship Center",
            "Leadership Academy", "Excellence Center", "Quality Institute", "Standards Academy", "Certification Center",
            "Training Academy", "Skill Development Center", "Vocational Institute", "Professional Academy", "Career Center",
            "Finishing School", "Personality Development Center", "Soft Skills Academy", "Communication Center", "Language Institute"
        };
        
        // Additional components for more variation
        String[] numericSuffixes = {
            "1", "2", "3", "4", "5", "I", "II", "III", "IV", "V",
            "Alpha", "Beta", "Gamma", "Delta", "Omega", "Prime", "Plus", "Pro", "Max", "Elite"
        };
        
        String[] locationPrefixes = {
            "East", "West", "North", "South", "Central", "Greater", "New", "Old", "Upper", "Lower"
        };
        
        String schoolName;
        int attempts = 0;
        final int MAX_ATTEMPTS = 1000; // Prevent infinite loops
        
        do {
            attempts++;
            if (attempts > MAX_ATTEMPTS) {
                // If we can't find a unique name after many attempts, add a random number
                schoolName = generateBasicSchoolName(schoolPrefixes, schoolMiddles, schoolSuffixes) + " " + 
                           (1000000 + (int)(Math.random() * 9000000)); // 7-digit random number
                break;
            }
            
            double pattern = Math.random();
            
            if (pattern < 0.15) {
                // Pattern 1: Location + Prefix + Middle + Suffix (15%)
                String location = locationPrefixes[(int) (Math.random() * locationPrefixes.length)];
                String prefix = schoolPrefixes[(int) (Math.random() * schoolPrefixes.length)];
                String middle = schoolMiddles[(int) (Math.random() * schoolMiddles.length)];
                String suffix = schoolSuffixes[(int) (Math.random() * schoolSuffixes.length)];
                schoolName = location + " " + prefix + " " + middle + " " + suffix;
            } else if (pattern < 0.30) {
                // Pattern 2: Prefix + Middle + Suffix + Numeric (15%)
                String prefix = schoolPrefixes[(int) (Math.random() * schoolPrefixes.length)];
                String middle = schoolMiddles[(int) (Math.random() * schoolMiddles.length)];
                String suffix = schoolSuffixes[(int) (Math.random() * schoolSuffixes.length)];
                String numeric = numericSuffixes[(int) (Math.random() * numericSuffixes.length)];
                schoolName = prefix + " " + middle + " " + suffix + " " + numeric;
            } else if (pattern < 0.45) {
                // Pattern 3: Two Middles + Suffix (15%)
                String prefix = schoolPrefixes[(int) (Math.random() * schoolPrefixes.length)];
                String middle1 = schoolMiddles[(int) (Math.random() * schoolMiddles.length)];
                String middle2 = schoolMiddles[(int) (Math.random() * schoolMiddles.length)];
                String suffix = schoolSuffixes[(int) (Math.random() * schoolSuffixes.length)];
                if (!middle1.equals(middle2)) {
                    schoolName = prefix + " " + middle1 + " " + middle2 + " " + suffix;
                } else {
                    schoolName = prefix + " " + middle1 + " " + suffix;
                }
            } else if (pattern < 0.55) {
                // Pattern 4: Prefix + Suffix only (10%)
                String prefix = schoolPrefixes[(int) (Math.random() * schoolPrefixes.length)];
                String suffix = schoolSuffixes[(int) (Math.random() * schoolSuffixes.length)];
                schoolName = prefix + " " + suffix;
            } else if (pattern < 0.70) {
                // Pattern 5: Location + Prefix + Suffix (15%)
                String location = locationPrefixes[(int) (Math.random() * locationPrefixes.length)];
                String prefix = schoolPrefixes[(int) (Math.random() * schoolPrefixes.length)];
                String suffix = schoolSuffixes[(int) (Math.random() * schoolSuffixes.length)];
                schoolName = location + " " + prefix + " " + suffix;
            } else {
                // Pattern 6: Traditional format (30%)
                schoolName = generateBasicSchoolName(schoolPrefixes, schoolMiddles, schoolSuffixes);
            }
            
        } while (usedSchoolNames.contains(schoolName));
        
        usedSchoolNames.add(schoolName);
        return schoolName;
    }
    
    // Helper method for basic school name generation
    private static String generateBasicSchoolName(String[] prefixes, String[] middles, String[] suffixes) {
        String prefix = prefixes[(int) (Math.random() * prefixes.length)];
        String middle = middles[(int) (Math.random() * middles.length)];
        String suffix = suffixes[(int) (Math.random() * suffixes.length)];
        
        // Sometimes skip the middle part for variation
        if (Math.random() < 0.3) {
            return prefix + " " + suffix;
        } else {
            return prefix + " " + middle + " " + suffix;
        }
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
    private static String generateAadharNumber(LRUCache<String> usedAadhars) {
        String aadharNumber;
        int attempts = 0;
        do {
            StringBuilder aadhar = new StringBuilder();
            // Generate 12 digit number
            for (int i = 0; i < 12; i++) {
                aadhar.append((int) (Math.random() * 10));
            }
            aadharNumber = aadhar.toString();
            attempts++;
            
            // Prevent infinite loops in case cache is full
            if (attempts > 1000) {
                break;
            }
        } while (usedAadhars.contains(aadharNumber));
        
        usedAadhars.add(aadharNumber);
        // Format as XXXX XXXX XXXX
        return aadharNumber.substring(0, 4) + " " + aadharNumber.substring(4, 8) + " " + aadharNumber.substring(8, 12);
    }
    
    public static void main(String[] args) {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        
        // Initialize system detection and thread pool first
        initializeSystemDetectionAndThreadPool();
        
        // Initialize connection pool
        System.out.println("=== INITIALIZING DATABASE CONNECTION POOL ===");
        try {
            initializeConnectionPool();
        } catch (Exception e) {
            System.err.println("Failed to initialize connection pool: " + e.getMessage());
            scanner.close();
            return;
        }
        
        // Database connection test
        System.out.println("=== TESTING DATABASE CONNECTION ===");
        try (Connection conn = getConnection()) {
            System.out.println("Successfully connected to PostgreSQL database!");
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            System.err.println("Please make sure PostgreSQL is running and credentials are correct.");
            closeConnectionPool();
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
        
        System.out.print("Enter number of schools to generate: ");
        int numSchools = scanner.nextInt();
        
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
        
        // Dataset size warnings and recommendations
        if (totalStudents > 10000000) {
            System.out.println("\n*** LARGE DATASET WARNING ***");
            System.out.println("You are about to generate " + String.format("%,d", totalStudents) + " students.");
            System.out.println("This is a very large dataset that may require:");
            System.out.println("  - High memory allocation: java -Xmx16g (or higher)");
            System.out.println("  - Significant processing time: 30+ minutes");
            System.out.println("  - Large database storage: 10+ GB disk space");
            System.out.println("  - PostgreSQL tuning for high connection load");
            System.out.println("\nRecommendation: Start with a smaller dataset for testing.");
        } else if (totalStudents > 1000000) {
            System.out.println("\n*** MEDIUM DATASET NOTICE ***");
            System.out.println("Generating " + String.format("%,d", totalStudents) + " students.");
            System.out.println("Estimated time: 10-20 minutes");
            System.out.println("Recommended memory: java -Xmx8g");
        } else if (totalStudents > 100000) {
            System.out.println("\n*** DATASET INFO ***");
            System.out.println("Generating " + String.format("%,d", totalStudents) + " students.");
            System.out.println("Estimated time: 2-5 minutes");
        }
        
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
        // Memory-efficient LRU caches to track used values (prevents memory exhaustion)
        LRUCache<String> usedPhoneNumbers = new LRUCache<>(MAX_CACHE_SIZE);
        LRUCache<String> usedAadhars = new LRUCache<>(MAX_CACHE_SIZE);
        LRUCache<String> usedNameCombinations = new LRUCache<>(MAX_CACHE_SIZE);
        
        System.out.println("=== GENERATING AND SAVING STUDENT DATA ===");
        System.out.println("Total students to generate: " + totalStudents);
        System.out.println("Using memory-efficient LRU caches (max " + MAX_CACHE_SIZE + " entries each) to prevent memory exhaustion");
        
        // Start batch processor for efficient database operations
        System.out.println("Starting batch processor for efficient database operations...");
        startBatchProcessor();
        
        // For very large datasets (>1M students), use synchronous processing
        boolean useSyncProcessing = totalStudents > 1000000;
        if (useSyncProcessing) {
            System.out.println("Large dataset detected (>1M students). Using batch processing for optimal performance.");
            System.out.println("Progress will be shown every 1000 students...\n");
        } else {
            System.out.println("Progress will be shown every 100 students...\n");
        }
        
        // Start progress monitoring with proper termination (only for async processing)
        if (!useSyncProcessing) {
            executorService.submit(() -> {
                int lastReported = 0;
                int stuckCounter = 0;
                while (processedStudents.get() < totalStudents && !processingComplete) {
                    try {
                        Thread.sleep(3000); // Update every 3 seconds
                        int current = processedStudents.get();
                        
                        // Check if processing is stuck
                        if (current == lastReported) {
                            stuckCounter++;
                            if (stuckCounter > 100) { // If stuck for 5 minutes (increased from 30 seconds)
                                System.out.println("WARNING: Processing appears stuck at " + current + " students");
                                System.out.println("This may be due to database connection issues with large datasets");
                                break;
                            }
                        } else {
                            stuckCounter = 0;
                            lastReported = current;
                        }
                        
                        double percentage = (current * 100.0) / totalStudents;
                        System.out.printf("Progress: %d/%d students (%.1f%%) processed\n", 
                            current, totalStudents, percentage);
                            
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                System.out.println("Progress monitoring stopped.");
            });
        } else {
            System.out.println("Synchronous processing mode - progress will be shown with student insertions.");
        }

        int currentSchoolIndex = 0;
        int currentClass = 1;
        char currentSection = 'A';
        int currentRollNo = 1;
        
        for (int studentIndex = 0; studentIndex < totalStudents; studentIndex++) {
            // Memory monitoring for large datasets
            if (studentIndex % 10000 == 0) { // Check every 10,000 students
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long maxMemory = runtime.maxMemory();
                double memoryUsagePercent = (usedMemory * 100.0) / maxMemory;
                
                if (memoryUsagePercent > 85) {
                    System.out.printf("WARNING: Memory usage at %.1f%% (%s/%s). Running garbage collection...\n", 
                        memoryUsagePercent, formatBytes(usedMemory), formatBytes(maxMemory));
                    System.gc(); // Force garbage collection
                    
                    // Wait a bit for GC to complete
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Check memory again after GC
                    usedMemory = runtime.totalMemory() - runtime.freeMemory();
                    memoryUsagePercent = (usedMemory * 100.0) / maxMemory;
                    
                    if (memoryUsagePercent > 90) {
                        System.err.printf("CRITICAL: Memory usage still at %.1f%% after GC. Consider increasing heap size with -Xmx flag.\n", memoryUsagePercent);
                        System.err.println("Current student index: " + studentIndex + "/" + totalStudents);
                        System.err.println("Recommendation: Use java -Xmx8g or higher for large datasets");
                    }
                }
            }
            
            // Generate unique name combination
            String fullName, firstName, lastName;
            int nameAttempts = 0;
            do {
                firstName = firstNames[(int) (Math.random() * firstNames.length)];
                lastName = lastNames[(int) (Math.random() * lastNames.length)];
                fullName = firstName + " " + lastName;
                nameAttempts++;
                
                // Prevent infinite loops in case cache is full
                if (nameAttempts > 1000) {
                    break;
                }
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
            
            // Handle stream-based section assignment for classes 11 and 12
            String sectionStr;
            String studentStream;
            
            if (currentClass == 11 || currentClass == 12) {
                // For classes 11 and 12, assign stream-based sections
                StreamSectionAssignment assignment = getStreamBasedSection(currentSection, numSections, currentRollNo, studentsPerSection);
                sectionStr = String.valueOf(assignment.section);
                studentStream = assignment.stream;
            } else {
                // For other classes, use regular section assignment
                sectionStr = String.valueOf(currentSection);
                studentStream = null; // No stream for classes 1-10
            }
            
            // Generate MinIO URL for student image
            String schoolNameEncoded = schoolName.replace(" ", "%20").replace(".", "").replace("'", "");
            String imageUrl = "minio.studentdata.tech/" + schoolNameEncoded + "/" + studentUUID + "/profile.png";
            
            // Create student data object
            StudentData student = new StudentData(
                studentUUID, fullName, guardianName, gender, bloodGroup, dateOfBirth,
                aadharNumber, classStr, sectionStr, currentRollNo, schoolName, religion,
                parentOccupation, concessionNeeded, concessionType, medicalCondition,
                studentPhone, guardianPhone, imageUrl, studentStream
            );
            
            // Use batch processing for all dataset sizes (more efficient)
            queueStudentForBatch(student);
            
            // Reduced delays since batch processing is more efficient
            if ((studentIndex + 1) % 1000 == 0) {
                try {
                    Thread.sleep(10); // Small pause every 1000 students to prevent overwhelming the queue
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
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

        // Stop batch processor and wait for all batches to complete
        System.out.println("\n=== STOPPING BATCH PROCESSOR ===");
        stopBatchProcessor();
        
        // Wait a bit for final batches to process
        try {
            Thread.sleep(5000); // Wait 5 seconds for final batch processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Signal processing completion and wait for all tasks
        processingComplete = true;
        executorService.shutdown();
        
        System.out.println("\n=== WAITING FOR DATABASE OPERATIONS TO COMPLETE ===");
        try {
            // Wait longer for large datasets
            long timeoutMinutes = Math.max(5, totalStudents / 10000); // 1 minute per 10k students, minimum 5 minutes
            System.out.println("Timeout set to " + timeoutMinutes + " minutes for " + totalStudents + " students");
            
            if (!executorService.awaitTermination(timeoutMinutes, TimeUnit.MINUTES)) {
                System.out.println("Timeout reached. Forcing shutdown...");
                executorService.shutdownNow();
                
                // Wait a bit more for forced shutdown
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.out.println("Some database operations may not have completed");
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted. Forcing shutdown...");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
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
        
        // Generate Academic Results after student data generation is complete
        System.out.println("\n=== GENERATING ACADEMIC RESULTS ===");
        
        // Always ask the user if they want to generate academic results
        System.out.print("Do you want to generate academic results? (y/n) [Press Enter for automatic]: ");
        
        try {
            // Use a timeout to detect if user provides input
            String response = "";
            boolean inputProvided = false;
            
            // Check if input is available within a reasonable timeout
            long startTime = System.currentTimeMillis();
            long timeoutMs = 5000; // 5 seconds timeout
            
            while ((System.currentTimeMillis() - startTime) < timeoutMs) {
                if (System.in.available() > 0) {
                    response = scanner.nextLine().trim().toLowerCase();
                    inputProvided = true;
                    break;
                }
                Thread.sleep(100);
            }
            
            if (inputProvided) {
                if (response.equals("y") || response.equals("yes")) {
                    System.out.println("Generating academic results with custom configuration...");
                    generateAcademicResults(scanner, schoolNames);
                } else if (response.equals("n") || response.equals("no")) {
                    System.out.println("Academic results generation skipped by user.");
                } else {
                    System.out.println("Invalid input. Generating academic results automatically...");
                    generateAutomaticAcademicResults(numClasses, schoolNames);
                }
            } else {
                System.out.println("\nNo input provided within 5 seconds. Generating academic results automatically...");
                generateAutomaticAcademicResults(numClasses, schoolNames);
            }
        } catch (Exception e) {
            System.out.println("Error during input detection. Generating academic results automatically...");
            generateAutomaticAcademicResults(numClasses, schoolNames);
        }
        
        // Generate Attendance Records after academic results
        System.out.println("\n=== GENERATING ATTENDANCE RECORDS ===");
        
        // Always ask the user if they want to generate attendance records
        System.out.print("Do you want to generate attendance records? (y/n) [Press Enter for automatic]: ");
        
        try {
            // Use a timeout to detect if user provides input
            String attendanceResponse = "";
            boolean attendanceInputProvided = false;
            
            // Check if input is available within a reasonable timeout
            long attendanceStartTime = System.currentTimeMillis();
            long attendanceTimeoutMs = 5000; // 5 seconds timeout
            
            while ((System.currentTimeMillis() - attendanceStartTime) < attendanceTimeoutMs) {
                if (System.in.available() > 0) {
                    attendanceResponse = scanner.nextLine().trim().toLowerCase();
                    attendanceInputProvided = true;
                    break;
                }
                Thread.sleep(100);
            }
            
            if (attendanceInputProvided) {
                if (attendanceResponse.equals("y") || attendanceResponse.equals("yes")) {
                    System.out.println("Generating attendance records with custom configuration...");
                    generateAttendanceRecords(scanner, schoolNames, numClasses);
                } else if (attendanceResponse.equals("n") || attendanceResponse.equals("no")) {
                    System.out.println("Attendance records generation skipped by user.");
                } else {
                    System.out.println("Invalid input. Generating attendance records automatically...");
                    generateAutomaticAttendanceRecords(numClasses, schoolNames);
                }
            } else {
                System.out.println("\nNo input provided within 5 seconds. Generating attendance records automatically...");
                generateAutomaticAttendanceRecords(numClasses, schoolNames);
            }
        } catch (Exception e) {
            System.out.println("Error during attendance input detection. Generating attendance records automatically...");
            generateAutomaticAttendanceRecords(numClasses, schoolNames);
        }
        
        // Generate homework records
        try {
            System.out.print("\nDo you want to generate homework records? (y/n) [5 second timeout]: ");
            String homeworkResponse = "";
            boolean homeworkInputProvided = false;
            
            for (int i = 0; i < 50; i++) { // 5 seconds timeout
                if (System.in.available() > 0) {
                    homeworkResponse = scanner.nextLine().trim().toLowerCase();
                    homeworkInputProvided = true;
                    break;
                }
                Thread.sleep(100);
            }
            
            if (homeworkInputProvided) {
                if (homeworkResponse.equals("y") || homeworkResponse.equals("yes")) {
                    System.out.println("Generating homework records with custom configuration...");
                    generateHomeworkRecords(scanner, schoolNames, numClasses);
                } else if (homeworkResponse.equals("n") || homeworkResponse.equals("no")) {
                    System.out.println("Homework records generation skipped by user.");
                } else {
                    System.out.println("Invalid input. Generating homework records automatically...");
                    generateAutomaticHomeworkRecords(numClasses, schoolNames);
                }
            } else {
                System.out.println("\nNo input provided within 5 seconds. Generating homework records automatically...");
                generateAutomaticHomeworkRecords(numClasses, schoolNames);
            }
        } catch (Exception e) {
            System.out.println("Error during homework input detection. Generating homework records automatically...");
            generateAutomaticHomeworkRecords(numClasses, schoolNames);
        }
        
        // Generate project records
        try {
            System.out.print("\nDo you want to generate project records? (y/n) [5 second timeout]: ");
            String projectResponse = "";
            boolean projectInputProvided = false;
            
            for (int i = 0; i < 50; i++) { // 5 seconds timeout
                if (System.in.available() > 0) {
                    projectResponse = scanner.nextLine().trim().toLowerCase();
                    projectInputProvided = true;
                    break;
                }
                Thread.sleep(100);
            }
            
            if (projectInputProvided) {
                if (projectResponse.equals("y") || projectResponse.equals("yes")) {
                    System.out.println("Generating project records with custom configuration...");
                    generateProjectRecords(scanner, schoolNames, numClasses);
                } else if (projectResponse.equals("n") || projectResponse.equals("no")) {
                    System.out.println("Project records generation skipped by user.");
                } else {
                    System.out.println("Invalid input. Generating project records automatically...");
                    generateAutomaticProjectRecords(numClasses, schoolNames);
                }
            } else {
                System.out.println("\nNo input provided within 5 seconds. Generating project records automatically...");
                generateAutomaticProjectRecords(numClasses, schoolNames);
            }
        } catch (Exception e) {
            System.out.println("Error during project input detection. Generating project records automatically...");
            generateAutomaticProjectRecords(numClasses, schoolNames);
        }
        
        // Close connection pool before closing scanner
        closeConnectionPool();
        scanner.close();
    }
    
    // Method to generate academic results for students
    private static void generateAcademicResults(java.util.Scanner scanner, java.util.List<String> schoolNames) {
        System.out.println("Now generating academic results for all students...\n");
        
        try {
            // Get user input for academic structure
            System.out.print("Enter the classes for which you want to generate results (e.g., '1-3' or '6-8'): ");
            String classRange = scanner.next();
            
            // Parse class range
            java.util.List<Integer> classes = parseClassRange(classRange);
            System.out.println("Selected classes: " + classes);
            
            System.out.print("Enter number of terms per year: ");
            int numTerms = scanner.nextInt();
        
        // Handle different subject combinations based on classes
        java.util.Map<Integer, java.util.List<SubjectInfo>> classSubjects = new java.util.HashMap<>();
        
        for (int currentClass : classes) {
            if (currentClass <= 9) {
                // For classes 1-9, use automated subject configuration
                if (!classSubjects.containsKey(currentClass)) {
                    System.out.println("\n=== AUTO-CONFIGURING SUBJECTS FOR CLASS " + currentClass + " ===");
                    java.util.List<SubjectInfo> subjects = getAutomatedSubjects(currentClass);
                    classSubjects.put(currentClass, subjects);
                    
                    // Display configured subjects
                    System.out.println("Subjects configured for Class " + currentClass + ":");
                    for (SubjectInfo subject : subjects) {
                        System.out.println("  - " + subject.name + " (Full Marks: " + subject.fullMarks + ")");
                    }
                }
            } else if (currentClass == 10) {
                // Class 10 - Fixed WBBSE subjects
                if (!classSubjects.containsKey(10)) {
                    System.out.println("\n=== CLASS 10 (WBBSE MADHYAMIK) - FIXED SUBJECTS ===");
                    java.util.List<SubjectInfo> class10Subjects = getClass10Subjects(scanner);
                    classSubjects.put(10, class10Subjects);
                }
            } else if (currentClass == 11 || currentClass == 12) {
                // Classes 11-12 - Stream-based subjects
                if (!classSubjects.containsKey(11)) {
                    System.out.println("\n=== CLASSES 11-12 (WBCHSE HIGHER SECONDARY) - STREAM-BASED SUBJECTS ===");
                    java.util.List<SubjectInfo> higherSecSubjects = getHigherSecondarySubjects(scanner);
                    classSubjects.put(11, higherSecSubjects);
                    classSubjects.put(12, higherSecSubjects); // Same subjects for both classes
                }
            }
        }
        
        int currentYear = 2025;
        
        // Process each school
        for (String schoolName : schoolNames) {
            System.out.println("\nProcessing academic results for: " + schoolName);
            
            // For each class in the selected range
            for (int currentClass : classes) {
                java.util.List<SubjectInfo> subjects = classSubjects.get(currentClass);
                
                // Generate marksheets for current and previous years based on student's progression
                for (int sessionYear = currentYear - currentClass + 1; sessionYear <= currentYear; sessionYear++) {
                    int studentClassInSession = currentClass - (currentYear - sessionYear);
                    if (studentClassInSession >= 1) {
                        // First create regular academic table (for all classes)
                        createAcademicTable(schoolName, studentClassInSession, sessionYear, subjects, numTerms, false);
                        generateAcademicData(schoolName, studentClassInSession, sessionYear, subjects, numTerms, false);
                        
                        // For classes 10 and 12, also create board exam tables
                        if (studentClassInSession == 10 || studentClassInSession == 12) {
                            System.out.println("Creating board exam table for Class " + studentClassInSession);
                            createBoardExamTable(schoolName, studentClassInSession, sessionYear, subjects, numTerms);
                            generateBoardExamData(schoolName, studentClassInSession, sessionYear, subjects, numTerms);
                        }
                    }
                }
            }
        }
        
        System.out.println("\n=== ACADEMIC RESULTS GENERATION COMPLETE ===");
        System.out.println("Academic tables created with format: {school}_class_{class}_{year}_academic");
        
        } catch (java.util.NoSuchElementException e) {
            System.out.println("\n=== INPUT ERROR ===");
            System.out.println("No more input available for academic results generation.");
            System.out.println("Academic results generation skipped.");
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN ACADEMIC RESULTS GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Academic results generation skipped.");
        }
    }
    
    // Method to generate academic results automatically using school structure parameters
    private static void generateAutomaticAcademicResults(int numClasses, java.util.List<String> schoolNames) {
        System.out.println("Automatically generating academic results for all " + numClasses + " classes...");
        
        try {
            // Use default configuration for automatic generation
            String classRange = "1-" + numClasses; // Generate for all classes from 1 to numClasses
            int numTerms = 3; // Default to 3 terms per year
            int sessionYear = 2025; // Current session year
            
            System.out.println("Configuration:");
            System.out.println("  Class Range: " + classRange + " (all classes in school structure)");
            System.out.println("  Terms per Year: " + numTerms);
            System.out.println("  Session Year: " + sessionYear);
            
            // Parse class range
            java.util.List<Integer> classes = parseClassRange(classRange);
            System.out.println("Selected classes: " + classes);
            
            // Handle different subject combinations based on classes
            java.util.Map<Integer, java.util.List<SubjectInfo>> classSubjects = new java.util.HashMap<>();
            
            for (int currentClass : classes) {
                if (currentClass <= 9) {
                    // For classes 1-9, use automated subject configuration
                    if (!classSubjects.containsKey(currentClass)) {
                        System.out.println("Auto-configuring subjects for Class " + currentClass);
                        java.util.List<SubjectInfo> subjects = getAutomatedSubjects(currentClass);
                        classSubjects.put(currentClass, subjects);
                        System.out.println("Class " + currentClass + " subjects: " + subjects.size() + " subjects configured");
                    }
                } else if (currentClass == 10) {
                    // For class 10, use fixed board exam subjects
                    if (!classSubjects.containsKey(currentClass)) {
                        System.out.println("Auto-configuring Class 10 board exam subjects");
                        java.util.List<SubjectInfo> subjects = getAutomatedClass10Subjects();
                        classSubjects.put(currentClass, subjects);
                        System.out.println("Class 10 subjects: " + subjects.size() + " subjects configured (board exam)");
                    }
                } else if (currentClass >= 11 && currentClass <= 12) {
                    // For classes 11-12, use automated higher secondary configuration
                    if (!classSubjects.containsKey(currentClass)) {
                        System.out.println("Auto-configuring Class " + currentClass + " higher secondary subjects");
                        java.util.List<SubjectInfo> subjects = getAutomatedHigherSecondarySubjects();
                        classSubjects.put(currentClass, subjects);
                        System.out.println("Class " + currentClass + " subjects: " + subjects.size() + " subjects configured (higher secondary)");
                    }
                }
            }
            
            // Generate academic data for each school and class
            System.out.println("\n=== GENERATING ACADEMIC DATA ===");
            int totalTables = schoolNames.size() * classes.size();
            int processedTables = 0;
            
            for (String schoolName : schoolNames) {
                System.out.println("Processing school: " + schoolName);
                
                for (int currentClass : classes) {
                    processedTables++;
                    System.out.println("  Generating academic data for Class " + currentClass + " (" + processedTables + "/" + totalTables + ")");
                    
                    java.util.List<SubjectInfo> subjects = classSubjects.get(currentClass);
                    boolean isBoardExam = (currentClass == 10 || currentClass == 12);
                    
                    // Create academic table
                    createAcademicTable(schoolName, currentClass, sessionYear, subjects, numTerms, isBoardExam);
                    
                    // Generate academic data
                    generateAcademicData(schoolName, currentClass, sessionYear, subjects, numTerms, isBoardExam);
                    
                    // For board exam classes, also create board exam table
                    if (isBoardExam) {
                        System.out.println("  Creating board exam table for Class " + currentClass);
                        createBoardExamTable(schoolName, currentClass, sessionYear, subjects, numTerms);
                        generateBoardExamData(schoolName, currentClass, sessionYear, subjects, numTerms);
                    }
                }
            }
            
            System.out.println("\n=== AUTOMATIC ACADEMIC RESULTS GENERATION COMPLETE ===");
            System.out.println("Generated academic results for:");
            System.out.println("  Schools: " + schoolNames.size());
            System.out.println("  Classes: " + classes.size() + " (Classes " + classes.get(0) + " to " + classes.get(classes.size()-1) + ")");
            System.out.println("  Terms per year: " + numTerms);
            System.out.println("  Total academic tables: " + totalTables);
            System.out.println("Academic tables created with format: {school}_class_{class}_{year}_academic");
            
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN AUTOMATIC ACADEMIC RESULTS GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("Automatic academic results generation failed, but student data is still available.");
        }
    }
    
    // Method to get automated Class 10 subjects
    private static java.util.List<SubjectInfo> getAutomatedClass10Subjects() {
        java.util.List<SubjectInfo> subjects = new java.util.ArrayList<>();
        
        // Standard Class 10 CBSE subjects
        subjects.add(new SubjectInfo("Mathematics", 100));
        subjects.add(new SubjectInfo("Science", 100));
        subjects.add(new SubjectInfo("Social Science", 100));
        subjects.add(new SubjectInfo("English", 100));
        subjects.add(new SubjectInfo("Hindi", 100));
        
        return subjects;
    }
    
    // Method to get automated higher secondary subjects  
    private static java.util.List<SubjectInfo> getAutomatedHigherSecondarySubjects() {
        java.util.List<SubjectInfo> subjects = new java.util.ArrayList<>();
        
        // Common higher secondary subjects (will be customized by stream in generation)
        subjects.add(new SubjectInfo("English Core", 100));
        subjects.add(new SubjectInfo("Mathematics", 100));
        subjects.add(new SubjectInfo("Physics", 100));
        subjects.add(new SubjectInfo("Chemistry", 100));
        subjects.add(new SubjectInfo("Biology", 100));
        subjects.add(new SubjectInfo("Computer Science", 100));
        
        return subjects;
    }
    
    // Method to get Class 10 fixed subjects
    private static java.util.List<SubjectInfo> getClass10Subjects(java.util.Scanner scanner) {
        java.util.List<SubjectInfo> subjects = new java.util.ArrayList<>();
        
        System.out.println("Class 10 - WBBSE Madhyamik (Auto-configured):");
        System.out.println("✓ First Language (Bengali)");
        System.out.println("✓ Second Language (English)");
        System.out.println("✓ Mathematics");
        System.out.println("✓ Physical Science");
        System.out.println("✓ Life Science");
        System.out.println("✓ History");
        System.out.println("✓ Geography");
        System.out.println("✓ Optional Subject (Physical Education)");
        
        // Add compulsory subjects with default marks
        subjects.add(new SubjectInfo("First_Language_Bengali", 100));
        subjects.add(new SubjectInfo("Second_Language_English", 100));
        subjects.add(new SubjectInfo("Mathematics", 100));
        subjects.add(new SubjectInfo("Physical_Science", 100));
        subjects.add(new SubjectInfo("Life_Science", 100));
        subjects.add(new SubjectInfo("History", 100));
        subjects.add(new SubjectInfo("Geography", 100));
        
        // Add automated optional subject
        subjects.add(new SubjectInfo("Physical_Education", 100));
        
        return subjects;
    }
    
    // Method to get Classes 11-12 stream-based subjects (auto-configured)
    private static java.util.List<SubjectInfo> getHigherSecondarySubjects(java.util.Scanner scanner) {
        java.util.List<SubjectInfo> subjects = new java.util.ArrayList<>();
        
        System.out.println("Classes 11-12 - WBCHSE Higher Secondary (Auto-configured):");
        System.out.println("✓ First Language");
        System.out.println("✓ Second Language (English)");
        System.out.println("✓ Environmental Education");
        System.out.println("✓ Stream-based Major Subjects (automatically assigned to students)");
        System.out.println("  - Science: Mathematics, Physics, Chemistry, Biology");
        System.out.println("  - Commerce: Economics, Business Studies, Accountancy, Mathematics");
        System.out.println("  - Arts: History, Geography, Political Science, Philosophy");
        
        // Add compulsory subjects for all streams
        subjects.add(new SubjectInfo("First_Language", 100));
        subjects.add(new SubjectInfo("Second_Language_English", 100));
        subjects.add(new SubjectInfo("Environmental_Education", 100));
        
        // Add placeholder subjects (actual subjects will be determined by student's stream)
        subjects.add(new SubjectInfo("Major_Subject_1", 100));
        subjects.add(new SubjectInfo("Major_Subject_2", 100));
        subjects.add(new SubjectInfo("Major_Subject_3", 100));
        subjects.add(new SubjectInfo("Major_Subject_4", 100));
        
        return subjects;
    }
    
    // Method to get automated subjects based on class level
    private static java.util.List<SubjectInfo> getAutomatedSubjects(int classNum) {
        java.util.List<SubjectInfo> subjects = new java.util.ArrayList<>();
        
        if (classNum >= 1 && classNum <= 4) {
            // Classes 1-4: Language (Bengali + English), Math, EVS, Arts, PE
            subjects.add(new SubjectInfo("Bengali", 100));
            subjects.add(new SubjectInfo("English", 100));
            subjects.add(new SubjectInfo("Mathematics", 100));
            subjects.add(new SubjectInfo("Environmental_Studies", 100));
            subjects.add(new SubjectInfo("Arts_and_Crafts", 100));
            subjects.add(new SubjectInfo("Physical_Education", 100));
            
        } else if (classNum >= 5 && classNum <= 8) {
            // Classes 5-8: Languages, Math, Science, History, Geography, Work Ed, Arts, PE
            subjects.add(new SubjectInfo("Bengali", 100));
            subjects.add(new SubjectInfo("English", 100));
            subjects.add(new SubjectInfo("Mathematics", 100));
            subjects.add(new SubjectInfo("Science", 100));
            subjects.add(new SubjectInfo("History", 100));
            subjects.add(new SubjectInfo("Geography", 100));
            subjects.add(new SubjectInfo("Work_Education", 100));
            subjects.add(new SubjectInfo("Arts_and_Crafts", 100));
            subjects.add(new SubjectInfo("Physical_Education", 100));
            
        } else if (classNum == 9) {
            // Class 9: First Lang, Second Lang, Math, Physical Science, Life Science, History, Geography, Optional subject
            subjects.add(new SubjectInfo("First_Language_Bengali", 100));
            subjects.add(new SubjectInfo("Second_Language_English", 100));
            subjects.add(new SubjectInfo("Mathematics", 100));
            subjects.add(new SubjectInfo("Physical_Science", 100));
            subjects.add(new SubjectInfo("Life_Science", 100));
            subjects.add(new SubjectInfo("History", 100));
            subjects.add(new SubjectInfo("Geography", 100));
            subjects.add(new SubjectInfo("Computer_Applications", 100)); // Default optional subject
        }
        
        return subjects;
    }
    
    // Helper method to parse class range
    private static java.util.List<Integer> parseClassRange(String classRange) {
        java.util.List<Integer> classes = new java.util.ArrayList<>();
        
        if (classRange.contains("-")) {
            String[] parts = classRange.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            for (int i = start; i <= end; i++) {
                classes.add(i);
            }
        } else {
            // Single class or comma-separated classes
            String[] parts = classRange.split(",");
            for (String part : parts) {
                classes.add(Integer.parseInt(part.trim()));
            }
        }
        
        return classes;
    }
    
    // Subject information class
    static class SubjectInfo {
        String name;
        int fullMarks;
        
        SubjectInfo(String name, int fullMarks) {
            this.name = name;
            this.fullMarks = fullMarks;
        }
    }
    
    // Stream and section assignment class
    static class StreamSectionAssignment {
        char section;
        String stream;
        
        StreamSectionAssignment(char section, String stream) {
            this.section = section;
            this.stream = stream;
        }
    }
    
    // Method to get stream-based section assignment for classes 11 and 12
    private static StreamSectionAssignment getStreamBasedSection(char currentSection, int numSections, int currentRollNo, int studentsPerSection) {
        String[] streams = {"Science", "Arts", "Commerce"};
        
        if (numSections == 1) {
            // Only one section - distribute streams evenly within the section
            int studentIndex = currentRollNo - 1;
            int streamIndex = studentIndex % 3;
            return new StreamSectionAssignment('A', streams[streamIndex]);
        } else if (numSections == 2) {
            // Two sections: Science in A, Arts and Commerce mixed in B
            if (currentSection == 'A') {
                return new StreamSectionAssignment('A', "Science");
            } else {
                // Alternate between Arts and Commerce in section B
                int streamChoice = (currentRollNo % 2 == 1) ? 1 : 2; // Arts or Commerce
                return new StreamSectionAssignment('B', streams[streamChoice]);
            }
        } else if (numSections == 3) {
            // Three sections: Science in A, Arts in B, Commerce in C
            char[] sectionMap = {'A', 'B', 'C'};
            return new StreamSectionAssignment(sectionMap[currentSection - 'A'], streams[currentSection - 'A']);
        } else if (numSections >= 4) {
            // Four or more sections: Science in A, Arts in B and C, Commerce in D (and beyond if more sections)
            if (currentSection == 'A') {
                return new StreamSectionAssignment('A', "Science");
            } else if (currentSection == 'B' || currentSection == 'C') {
                return new StreamSectionAssignment(currentSection, "Arts");
            } else {
                return new StreamSectionAssignment(currentSection, "Commerce");
            }
        }
        
        // Default fallback
        return new StreamSectionAssignment(currentSection, "Science");
    }
    
    // Method to create academic table for a specific school, class, and year
    private static void createAcademicTable(String schoolName, int classNum, int sessionYear, 
                                          java.util.List<SubjectInfo> subjects, int numTerms, boolean isBoardExam) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        
        String tableName = sanitizedSchoolName + "_class_" + classNum + "_" + sessionYear + "_academic";
        
        try (Connection conn = getConnection()) {
            if (classNum == 11 || classNum == 12) {
                // Special table structure for classes 11 and 12
                createHigherSecondaryTable(conn, tableName, numTerms, isBoardExam);
            } else {
                // Regular table structure for other classes
                createRegularAcademicTable(conn, tableName, subjects, numTerms, isBoardExam);
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating academic table " + tableName + ": " + e.getMessage());
        }
    }
    
    // Method to create regular academic table for classes 1-10
    private static void createRegularAcademicTable(Connection conn, String tableName, 
                                                 java.util.List<SubjectInfo> subjects, int numTerms, boolean isBoardExam) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        createTableSQL.append("student_uuid UUID PRIMARY KEY, ");
        createTableSQL.append("student_name VARCHAR(100), ");
        createTableSQL.append("roll_no INTEGER, ");
        createTableSQL.append("section VARCHAR(5), ");
        
        // Add columns for each subject and term
        for (SubjectInfo subject : subjects) {
            String subjectNameSanitized = subject.name.toLowerCase()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .replaceAll("_{2,}", "_")
                    .replaceAll("^_|_$", "");
            
            for (int term = 1; term <= numTerms; term++) {
                createTableSQL.append(subjectNameSanitized).append("_full_marks_term_").append(term)
                        .append(" INTEGER DEFAULT ").append(subject.fullMarks).append(", ");
                createTableSQL.append(subjectNameSanitized).append("_obtained_marks_term_").append(term)
                        .append(" INTEGER, ");
            }
        }
        
        // Add total marks and percentage columns for each term
        for (int term = 1; term <= numTerms; term++) {
            createTableSQL.append("total_marks_term_").append(term).append(" INTEGER, ");
            createTableSQL.append("percentage_term_").append(term).append(" DECIMAL(5,2), ");
        }
        
        // Add grand total and grand percentage
        createTableSQL.append("grand_total INTEGER, ");
        createTableSQL.append("percentage_grand_total DECIMAL(5,2), ");
        
        // Add board exam indicator
        if (isBoardExam) {
            createTableSQL.append("is_board_exam BOOLEAN DEFAULT TRUE, ");
            createTableSQL.append("board_exam_note VARCHAR(255) DEFAULT 'Grand total calculated from final term only', ");
        }
        
        createTableSQL.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        createTableSQL.append(")");
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL.toString());
            String examType = isBoardExam ? " (Board Exam)" : "";
            System.out.println("Created academic table: " + tableName + examType);
        }
    }
    
    // Method to create higher secondary table for classes 11 and 12
    private static void createHigherSecondaryTable(Connection conn, String tableName, int numTerms, boolean isBoardExam) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        createTableSQL.append("student_uuid UUID PRIMARY KEY, ");
        createTableSQL.append("student_name VARCHAR(100), ");
        createTableSQL.append("roll_no INTEGER, ");
        createTableSQL.append("section VARCHAR(5), ");
        
        // Fixed structure for classes 11 and 12
        // First Language
        for (int term = 1; term <= numTerms; term++) {
            createTableSQL.append("first_language_full_marks_term_").append(term).append(" INTEGER DEFAULT 100, ");
            createTableSQL.append("first_language_obtained_marks_term_").append(term).append(" INTEGER, ");
        }
        
        // Second Language (English)
        for (int term = 1; term <= numTerms; term++) {
            createTableSQL.append("second_language_english_full_marks_term_").append(term).append(" INTEGER DEFAULT 100, ");
            createTableSQL.append("second_language_english_obtained_marks_term_").append(term).append(" INTEGER, ");
        }
        
        // Major subject names (will be populated based on stream)
        createTableSQL.append("major_1_subject_name VARCHAR(100), ");
        createTableSQL.append("major_2_subject_name VARCHAR(100), ");
        createTableSQL.append("major_3_subject_name VARCHAR(100), ");
        createTableSQL.append("major_4_subject_name VARCHAR(100), ");
        
        // Major subjects marks
        for (int majorNum = 1; majorNum <= 4; majorNum++) {
            for (int term = 1; term <= numTerms; term++) {
                createTableSQL.append("major_").append(majorNum).append("_full_marks_term_").append(term).append(" INTEGER DEFAULT 100, ");
                createTableSQL.append("major_").append(majorNum).append("_obtained_marks_term_").append(term).append(" INTEGER, ");
            }
        }
        
        // Total marks and percentages for each term
        for (int term = 1; term <= numTerms; term++) {
            createTableSQL.append("total_marks_term_").append(term).append(" INTEGER, ");
            createTableSQL.append("percentage_term_").append(term).append(" DECIMAL(5,2), ");
        }
        
        // Grand total and grand percentage
        createTableSQL.append("grand_total INTEGER, ");
        createTableSQL.append("percentage_grand_total DECIMAL(5,2), ");
        
        // Board exam indicator
        if (isBoardExam) {
            createTableSQL.append("is_board_exam BOOLEAN DEFAULT TRUE, ");
            createTableSQL.append("board_exam_note VARCHAR(255) DEFAULT 'Grand total calculated from final term only', ");
        } else {
            createTableSQL.append("is_board_exam BOOLEAN DEFAULT FALSE, ");
            createTableSQL.append("board_exam_note VARCHAR(255), ");
        }
        
        createTableSQL.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        createTableSQL.append(")");
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL.toString());
            String examType = isBoardExam ? " (Board Exam)" : "";
            System.out.println("Created higher secondary table: " + tableName + examType);
        }
    }
    
    // Method to generate academic data for students
    private static void generateAcademicData(String schoolName, int classNum, int sessionYear, 
                                           java.util.List<SubjectInfo> subjects, int numTerms, boolean isBoardExam) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        
        String academicTableName = sanitizedSchoolName + "_class_" + classNum + "_" + sessionYear + "_academic";
        String studentTableName = getStudentTableName(schoolName);
        
        try (Connection conn = getConnection()) {
            // Get students from the specific class
            String selectStudentsSQL = "SELECT student_uuid, full_name, roll_no, section, stream FROM " + 
                    studentTableName + " WHERE class_name = ?";
            
            try (PreparedStatement selectStmt = conn.prepareStatement(selectStudentsSQL)) {
                selectStmt.setString(1, "Class " + classNum);
                
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        String studentUuid = rs.getString("student_uuid");
                        String studentName = rs.getString("full_name");
                        int rollNo = rs.getInt("roll_no");
                        String section = rs.getString("section");
                        String stream = rs.getString("stream");
                        
                        // Generate marks for this student
                        if (classNum == 11 || classNum == 12) {
                            insertHigherSecondaryRecord(conn, academicTableName, studentUuid, studentName, 
                                                       rollNo, section, stream, numTerms, isBoardExam);
                        } else {
                            insertAcademicRecord(conn, academicTableName, studentUuid, studentName, 
                                               rollNo, section, subjects, numTerms, isBoardExam);
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating academic data for " + academicTableName + ": " + e.getMessage());
        }
    }
    
    // Method to create board exam table for classes 10 and 12
    private static void createBoardExamTable(String schoolName, int classNum, int sessionYear, 
                                           java.util.List<SubjectInfo> subjects, int numTerms) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        
        String boardTableName = sanitizedSchoolName + "_class_" + classNum + "_" + sessionYear + "_board_exam_academic";
        
        try (Connection conn = getConnection()) {
            if (classNum == 10) {
                createClass10BoardExamTable(conn, boardTableName, subjects);
            } else if (classNum == 12) {
                createClass12BoardExamTable(conn, boardTableName);
            }
        } catch (SQLException e) {
            System.err.println("Error creating board exam table " + boardTableName + ": " + e.getMessage());
        }
    }
    
    // Method to create Class 10 board exam table structure
    private static void createClass10BoardExamTable(Connection conn, String tableName, 
                                                   java.util.List<SubjectInfo> subjects) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        createTableSQL.append("student_uuid UUID PRIMARY KEY, ");
        createTableSQL.append("student_name VARCHAR(100), ");
        createTableSQL.append("roll_no INTEGER, ");
        createTableSQL.append("section VARCHAR(5), ");
        
        // Add columns for each subject (no term numbers for board exam)
        for (SubjectInfo subject : subjects) {
            String subjectNameSanitized = subject.name.toLowerCase()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .replaceAll("_{2,}", "_")
                    .replaceAll("^_|_$", "");
            
            createTableSQL.append(subjectNameSanitized).append("_full_marks INTEGER DEFAULT ")
                    .append(subject.fullMarks).append(", ");
            createTableSQL.append(subjectNameSanitized).append("_obtained_marks INTEGER, ");
        }
        
        // Add total marks and percentage (no term numbers)
        createTableSQL.append("total_marks INTEGER, ");
        createTableSQL.append("percentage DECIMAL(5,2), ");
        createTableSQL.append("grand_total INTEGER, ");
        createTableSQL.append("percentage_grand_total DECIMAL(5,2), ");
        createTableSQL.append("is_board_exam BOOLEAN DEFAULT TRUE, ");
        createTableSQL.append("board_exam_note VARCHAR(255) DEFAULT 'WBBSE Madhyamik Board Examination', ");
        createTableSQL.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        createTableSQL.append(")");
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL.toString());
            System.out.println("Created Class 10 board exam table: " + tableName);
        }
    }
    
    // Method to create Class 12 board exam table structure
    private static void createClass12BoardExamTable(Connection conn, String tableName) throws SQLException {
        StringBuilder createTableSQL = new StringBuilder();
        createTableSQL.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        createTableSQL.append("student_uuid UUID PRIMARY KEY, ");
        createTableSQL.append("student_name VARCHAR(100), ");
        createTableSQL.append("roll_no INTEGER, ");
        createTableSQL.append("section VARCHAR(5), ");
        
        // Simplified structure for Class 12 board exam (no term numbers)
        createTableSQL.append("first_language_full_marks INTEGER DEFAULT 100, ");
        createTableSQL.append("first_language_obtained_marks INTEGER, ");
        createTableSQL.append("second_language_english_full_marks INTEGER DEFAULT 100, ");
        createTableSQL.append("second_language_english_obtained_marks INTEGER, ");
        
        // Major subject names
        createTableSQL.append("major_1_subject_name VARCHAR(100), ");
        createTableSQL.append("major_2_subject_name VARCHAR(100), ");
        createTableSQL.append("major_3_subject_name VARCHAR(100), ");
        createTableSQL.append("major_4_subject_name VARCHAR(100), ");
        
        // Major subjects marks (no term numbers)
        createTableSQL.append("major_1_full_marks INTEGER DEFAULT 100, ");
        createTableSQL.append("major_1_obtained_marks INTEGER, ");
        createTableSQL.append("major_2_full_marks INTEGER DEFAULT 100, ");
        createTableSQL.append("major_2_obtained_marks INTEGER, ");
        createTableSQL.append("major_3_full_marks INTEGER DEFAULT 100, ");
        createTableSQL.append("major_3_obtained_marks INTEGER, ");
        createTableSQL.append("major_4_full_marks INTEGER DEFAULT 100, ");
        createTableSQL.append("major_4_obtained_marks INTEGER, ");
        
        // Total marks and percentages (no term numbers)
        createTableSQL.append("total_marks INTEGER, ");
        createTableSQL.append("percentage DECIMAL(5,2), ");
        createTableSQL.append("grand_total INTEGER, ");
        createTableSQL.append("percentage_grand_total DECIMAL(5,2), ");
        createTableSQL.append("is_board_exam BOOLEAN DEFAULT TRUE, ");
        createTableSQL.append("board_exam_note VARCHAR(255) DEFAULT 'WBCHSE Higher Secondary Board Examination', ");
        createTableSQL.append("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        createTableSQL.append(")");
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL.toString());
            System.out.println("Created Class 12 board exam table: " + tableName);
        }
    }
    
    // Method to generate board exam data
    private static void generateBoardExamData(String schoolName, int classNum, int sessionYear, 
                                            java.util.List<SubjectInfo> subjects, int numTerms) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        
        String boardTableName = sanitizedSchoolName + "_class_" + classNum + "_" + sessionYear + "_board_exam_academic";
        String studentTableName = getStudentTableName(schoolName);
        
        try (Connection conn = getConnection()) {
            // Get students from the specific class
            String selectStudentsSQL = "SELECT student_uuid, full_name, roll_no, section, stream FROM " + 
                    studentTableName + " WHERE class_name = ?";
            
            try (PreparedStatement selectStmt = conn.prepareStatement(selectStudentsSQL)) {
                selectStmt.setString(1, "Class " + classNum);
                
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        String studentUuid = rs.getString("student_uuid");
                        String studentName = rs.getString("full_name");
                        int rollNo = rs.getInt("roll_no");
                        String section = rs.getString("section");
                        String stream = rs.getString("stream");
                        
                        // Generate board exam marks
                        if (classNum == 12) {
                            insertClass12BoardExamRecord(conn, boardTableName, studentUuid, studentName, 
                                                        rollNo, section, stream);
                        } else if (classNum == 10) {
                            insertClass10BoardExamRecord(conn, boardTableName, studentUuid, studentName, 
                                                        rollNo, section, subjects);
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating board exam data for " + boardTableName + ": " + e.getMessage());
        }
    }
    
    // Method to insert Class 10 board exam record
    private static void insertClass10BoardExamRecord(Connection conn, String tableName, String studentUuid, 
                                                    String studentName, int rollNo, String section, 
                                                    java.util.List<SubjectInfo> subjects) {
        try {
            // Build INSERT statement for Class 10 board exam
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO ").append(tableName).append(" (");
            insertSQL.append("student_uuid, student_name, roll_no, section, ");
            
            // Add column names for subjects (no term numbers)
            for (SubjectInfo subject : subjects) {
                String subjectNameSanitized = subject.name.toLowerCase()
                        .replaceAll("[^a-zA-Z0-9]", "_")
                        .replaceAll("_{2,}", "_")
                        .replaceAll("^_|_$", "");
                
                insertSQL.append(subjectNameSanitized).append("_obtained_marks, ");
            }
            
            insertSQL.append("total_marks, percentage, grand_total, percentage_grand_total) VALUES (");
            
            // Add placeholders (4 basic + subjects + 4 totals)
            int totalColumns = 4 + subjects.size() + 4;
            for (int i = 0; i < totalColumns; i++) {
                insertSQL.append("?");
                if (i < totalColumns - 1) insertSQL.append(", ");
            }
            insertSQL.append(")");
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString())) {
                int paramIndex = 1;
                
                // Set basic student info
                pstmt.setObject(paramIndex++, java.util.UUID.fromString(studentUuid));
                pstmt.setString(paramIndex++, studentName);
                pstmt.setInt(paramIndex++, rollNo);
                pstmt.setString(paramIndex++, section);
                
                // Generate and set marks for each subject
                int totalMarks = 0;
                int totalFullMarks = 0;
                
                for (SubjectInfo subject : subjects) {
                    // Generate random marks (40-100% of full marks for board exam)
                    int minMarks = (int) (subject.fullMarks * 0.4); // 40% minimum
                    int maxMarks = subject.fullMarks;
                    int obtainedMarks = minMarks + (int) (Math.random() * (maxMarks - minMarks + 1));
                    
                    pstmt.setInt(paramIndex++, obtainedMarks);
                    totalMarks += obtainedMarks;
                    totalFullMarks += subject.fullMarks;
                }
                
                // Set totals and percentages
                double percentage = (totalMarks * 100.0) / totalFullMarks;
                pstmt.setInt(paramIndex++, totalMarks); // total_marks
                pstmt.setDouble(paramIndex++, Math.round(percentage * 100.0) / 100.0); // percentage
                pstmt.setInt(paramIndex++, totalMarks); // grand_total (same as total for board exam)
                pstmt.setDouble(paramIndex++, Math.round(percentage * 100.0) / 100.0); // percentage_grand_total
                
                pstmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            System.err.println("Error inserting Class 10 board exam record for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to insert Class 12 board exam record
    private static void insertClass12BoardExamRecord(Connection conn, String tableName, String studentUuid, 
                                                    String studentName, int rollNo, String section, String stream) {
        try {
            // Get stream-based subjects
            String[] subjectNames = getStreamSubjects(stream);
            
            // Build INSERT statement for Class 12 board exam
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO ").append(tableName).append(" (");
            insertSQL.append("student_uuid, student_name, roll_no, section, ");
            insertSQL.append("first_language_obtained_marks, ");
            insertSQL.append("second_language_english_obtained_marks, ");
            insertSQL.append("major_1_subject_name, major_2_subject_name, major_3_subject_name, major_4_subject_name, ");
            insertSQL.append("major_1_obtained_marks, major_2_obtained_marks, major_3_obtained_marks, major_4_obtained_marks, ");
            insertSQL.append("total_marks, percentage, grand_total, percentage_grand_total) VALUES (");
            
            // Add placeholders (4 basic + 2 languages + 4 names + 4 majors + 4 totals = 18)
            for (int i = 0; i < 18; i++) {
                insertSQL.append("?");
                if (i < 17) insertSQL.append(", ");
            }
            insertSQL.append(")");
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString())) {
                int paramIndex = 1;
                
                // Set basic student info
                pstmt.setObject(paramIndex++, java.util.UUID.fromString(studentUuid));
                pstmt.setString(paramIndex++, studentName);
                pstmt.setInt(paramIndex++, rollNo);
                pstmt.setString(paramIndex++, section);
                
                // Generate language marks
                int firstLangMarks = 40 + (int) (Math.random() * 61); // 40-100
                int englishMarks = 40 + (int) (Math.random() * 61); // 40-100
                pstmt.setInt(paramIndex++, firstLangMarks);
                pstmt.setInt(paramIndex++, englishMarks);
                
                // Set subject names
                for (int i = 0; i < 4; i++) {
                    pstmt.setString(paramIndex++, subjectNames[i]);
                }
                
                // Generate major subjects marks
                int totalMarks = firstLangMarks + englishMarks;
                for (int majorNum = 1; majorNum <= 4; majorNum++) {
                    int majorMarks = 40 + (int) (Math.random() * 61); // 40-100
                    pstmt.setInt(paramIndex++, majorMarks);
                    totalMarks += majorMarks;
                }
                
                // Set totals and percentages (600 total marks for 6 subjects)
                double percentage = (totalMarks * 100.0) / 600;
                pstmt.setInt(paramIndex++, totalMarks); // total_marks
                pstmt.setDouble(paramIndex++, Math.round(percentage * 100.0) / 100.0); // percentage
                pstmt.setInt(paramIndex++, totalMarks); // grand_total (same as total for board exam)
                pstmt.setDouble(paramIndex++, Math.round(percentage * 100.0) / 100.0); // percentage_grand_total
                
                pstmt.executeUpdate();
            }
            
        } catch (SQLException e) {
            System.err.println("Error inserting Class 12 board exam record for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to insert academic record for a student
    private static void insertAcademicRecord(Connection conn, String tableName, String studentUuid, 
                                           String studentName, int rollNo, String section, 
                                           java.util.List<SubjectInfo> subjects, int numTerms, boolean isBoardExam) {
        try {
            // Build INSERT statement dynamically
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO ").append(tableName).append(" (");
            insertSQL.append("student_uuid, student_name, roll_no, section, ");
            
            // Add column names for subjects and terms
            for (SubjectInfo subject : subjects) {
                String subjectNameSanitized = subject.name.toLowerCase()
                        .replaceAll("[^a-zA-Z0-9]", "_")
                        .replaceAll("_{2,}", "_")
                        .replaceAll("^_|_$", "");
                
                for (int term = 1; term <= numTerms; term++) {
                    insertSQL.append(subjectNameSanitized).append("_obtained_marks_term_").append(term).append(", ");
                }
            }
            
            // Add total marks and percentage columns
            for (int term = 1; term <= numTerms; term++) {
                insertSQL.append("total_marks_term_").append(term).append(", ");
                insertSQL.append("percentage_term_").append(term).append(", ");
            }
            
            insertSQL.append("grand_total, percentage_grand_total) VALUES (");
            
            // Add placeholders
            int totalColumns = 4 + (subjects.size() * numTerms) + (2 * numTerms) + 2;
            for (int i = 0; i < totalColumns; i++) {
                insertSQL.append("?");
                if (i < totalColumns - 1) insertSQL.append(", ");
            }
            insertSQL.append(")");
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString())) {
                int paramIndex = 1;
                
                // Set basic student info
                pstmt.setObject(paramIndex++, java.util.UUID.fromString(studentUuid));
                pstmt.setString(paramIndex++, studentName);
                pstmt.setInt(paramIndex++, rollNo);
                pstmt.setString(paramIndex++, section);
                
                // Generate and set marks for each subject and term
                int[] termTotals = new int[numTerms];
                int[] termFullMarks = new int[numTerms];
                
                for (SubjectInfo subject : subjects) {
                    for (int term = 1; term <= numTerms; term++) {
                        // Generate random marks (40-100% of full marks for realistic distribution)
                        int minMarks = (int) (subject.fullMarks * 0.4); // 40% minimum
                        int maxMarks = subject.fullMarks;
                        int obtainedMarks = minMarks + (int) (Math.random() * (maxMarks - minMarks + 1));
                        
                        pstmt.setInt(paramIndex++, obtainedMarks);
                        termTotals[term - 1] += obtainedMarks;
                        termFullMarks[term - 1] += subject.fullMarks;
                    }
                }
                
                // Set term totals and percentages
                int grandTotal = 0;
                int grandFullMarks = 0;
                
                for (int term = 0; term < numTerms; term++) {
                    pstmt.setInt(paramIndex++, termTotals[term]);
                    double percentage = (termTotals[term] * 100.0) / termFullMarks[term];
                    pstmt.setDouble(paramIndex++, Math.round(percentage * 100.0) / 100.0);
                    
                    // For board exams, only the last term counts for grand total
                    if (isBoardExam) {
                        if (term == numTerms - 1) { // Last term only
                            grandTotal = termTotals[term];
                            grandFullMarks = termFullMarks[term];
                        }
                    } else {
                        // For regular classes, all terms count
                        grandTotal += termTotals[term];
                        grandFullMarks += termFullMarks[term];
                    }
                }
                
                // Set grand total and grand percentage
                pstmt.setInt(paramIndex++, grandTotal);
                double grandPercentage = (grandTotal * 100.0) / grandFullMarks;
                pstmt.setDouble(paramIndex++, Math.round(grandPercentage * 100.0) / 100.0);
                
                pstmt.executeUpdate();
                
            }
            
        } catch (SQLException e) {
            System.err.println("Error inserting academic record for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to insert higher secondary record for classes 11 and 12
    private static void insertHigherSecondaryRecord(Connection conn, String tableName, String studentUuid, 
                                                  String studentName, int rollNo, String section, 
                                                  String stream, int numTerms, boolean isBoardExam) {
        try {
            // Get stream-based subjects
            String[] subjectNames = getStreamSubjects(stream);
            
            // Build INSERT statement for higher secondary structure
            StringBuilder insertSQL = new StringBuilder();
            insertSQL.append("INSERT INTO ").append(tableName).append(" (");
            insertSQL.append("student_uuid, student_name, roll_no, section, ");
            
            // Add first language columns
            for (int term = 1; term <= numTerms; term++) {
                insertSQL.append("first_language_obtained_marks_term_").append(term).append(", ");
            }
            
            // Add second language columns
            for (int term = 1; term <= numTerms; term++) {
                insertSQL.append("second_language_english_obtained_marks_term_").append(term).append(", ");
            }
            
            // Add subject names
            insertSQL.append("major_1_subject_name, major_2_subject_name, major_3_subject_name, major_4_subject_name, ");
            
            // Add major subjects columns
            for (int majorNum = 1; majorNum <= 4; majorNum++) {
                for (int term = 1; term <= numTerms; term++) {
                    insertSQL.append("major_").append(majorNum).append("_obtained_marks_term_").append(term).append(", ");
                }
            }
            
            // Add total marks and percentage columns
            for (int term = 1; term <= numTerms; term++) {
                insertSQL.append("total_marks_term_").append(term).append(", ");
                insertSQL.append("percentage_term_").append(term).append(", ");
            }
            
            insertSQL.append("grand_total, percentage_grand_total) VALUES (");
            
            // Add placeholders (4 basic + 2*numTerms lang + 4 names + 4*numTerms majors + 2*numTerms totals + 2 grand)
            int totalColumns = 4 + (2 * numTerms) + 4 + (4 * numTerms) + (2 * numTerms) + 2;
            for (int i = 0; i < totalColumns; i++) {
                insertSQL.append("?");
                if (i < totalColumns - 1) insertSQL.append(", ");
            }
            insertSQL.append(")");
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL.toString())) {
                int paramIndex = 1;
                
                // Set basic student info
                pstmt.setObject(paramIndex++, java.util.UUID.fromString(studentUuid));
                pstmt.setString(paramIndex++, studentName);
                pstmt.setInt(paramIndex++, rollNo);
                pstmt.setString(paramIndex++, section);
                
                // Initialize arrays for calculations
                int[] termTotals = new int[numTerms];
                int totalFullMarks = 600; // 6 subjects × 100 marks each
                
                // Generate first language marks
                for (int term = 1; term <= numTerms; term++) {
                    int firstLangMarks = 40 + (int) (Math.random() * 61); // 40-100
                    pstmt.setInt(paramIndex++, firstLangMarks);
                    termTotals[term - 1] += firstLangMarks;
                }
                
                // Generate second language (English) marks
                for (int term = 1; term <= numTerms; term++) {
                    int englishMarks = 40 + (int) (Math.random() * 61); // 40-100
                    pstmt.setInt(paramIndex++, englishMarks);
                    termTotals[term - 1] += englishMarks;
                }
                
                // Set subject names
                for (int i = 0; i < 4; i++) {
                    pstmt.setString(paramIndex++, subjectNames[i]);
                }
                
                // Generate major subjects marks
                for (int majorNum = 1; majorNum <= 4; majorNum++) {
                    for (int term = 1; term <= numTerms; term++) {
                        int majorMarks = 40 + (int) (Math.random() * 61); // 40-100
                        pstmt.setInt(paramIndex++, majorMarks);
                        termTotals[term - 1] += majorMarks;
                    }
                }
                
                // Set term totals and percentages
                int grandTotal = 0;
                for (int term = 0; term < numTerms; term++) {
                    pstmt.setInt(paramIndex++, termTotals[term]);
                    double percentage = (termTotals[term] * 100.0) / totalFullMarks;
                    pstmt.setDouble(paramIndex++, Math.round(percentage * 100.0) / 100.0);
                    
                    // For board exams, only the last term counts for grand total
                    if (isBoardExam) {
                        if (term == numTerms - 1) { // Last term only
                            grandTotal = termTotals[term];
                        }
                    } else {
                        // For regular classes, all terms count
                        grandTotal += termTotals[term];
                    }
                }
                
                // Set grand total and grand percentage
                pstmt.setInt(paramIndex++, grandTotal);
                int grandFullMarks = isBoardExam ? totalFullMarks : (totalFullMarks * numTerms);
                double grandPercentage = (grandTotal * 100.0) / grandFullMarks;
                pstmt.setDouble(paramIndex++, Math.round(grandPercentage * 100.0) / 100.0);
                
                pstmt.executeUpdate();
                
            }
            
        } catch (SQLException e) {
            System.err.println("Error inserting higher secondary record for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to get subjects based on stream
    private static String[] getStreamSubjects(String stream) {
        if (stream == null) stream = "Science"; // Default fallback
        
        switch (stream) {
            case "Science":
                return new String[]{"Mathematics", "Physics", "Chemistry", "Biology"};
            case "Commerce":
                return new String[]{"Economics", "Business_Studies", "Accountancy", "Mathematics"};
            case "Arts":
                return new String[]{"History", "Geography", "Political_Science", "Philosophy"};
            default:
                return new String[]{"Mathematics", "Physics", "Chemistry", "Biology"}; // Default to Science
        }
    }
    
    // Method to generate attendance records for students (interactive)
    private static void generateAttendanceRecords(java.util.Scanner scanner, java.util.List<String> schoolNames, int numClasses) {
        System.out.println("Now generating attendance records for all students...\n");
        
        try {
            System.out.print("Enter the classes for which you want to generate attendance (e.g., '1-12' or '6-8'): ");
            String classRange = scanner.next();
            
            // Parse class range
            java.util.List<Integer> classes = parseClassRange(classRange);
            System.out.println("Selected classes: " + classes);
            
            generateAttendanceData(classes, schoolNames);
            
        } catch (java.util.NoSuchElementException e) {
            System.out.println("\n=== INPUT ERROR ===");
            System.out.println("No more input available for attendance generation.");
            System.out.println("Attendance generation skipped.");
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN ATTENDANCE GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Attendance generation skipped.");
        }
    }
    
    // Method to generate attendance records automatically
    private static void generateAutomaticAttendanceRecords(int numClasses, java.util.List<String> schoolNames) {
        System.out.println("Automatically generating attendance records for all " + numClasses + " classes...");
        
        try {
            // Generate for all classes from 1 to numClasses
            java.util.List<Integer> classes = new java.util.ArrayList<>();
            for (int i = 1; i <= numClasses; i++) {
                classes.add(i);
            }
            
            generateAttendanceData(classes, schoolNames);
            
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN AUTOMATIC ATTENDANCE GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Attendance generation skipped.");
        }
    }
    
    // Method to generate attendance data for selected classes and schools
    private static void generateAttendanceData(java.util.List<Integer> classes, java.util.List<String> schoolNames) {
        int currentYear = 2025;
        
        System.out.println("=== ATTENDANCE GENERATION CONFIGURATION ===");
        System.out.println("Logic: ALL students will have attendance records for years they were in school");
        System.out.println("       For example, students currently in Class 5 will have attendance for:");
        System.out.println("       2021 (when in Class 1), 2022 (Class 2), 2023 (Class 3),");
        System.out.println("       2024 (Class 4), 2025 (Class 5)");
        System.out.println("Attendance percentage: 20% to 100% per student");
        System.out.println("Working days: Excludes weekends, national holidays, school breaks");
        System.out.println("===============================================\n");
        
        // Process each school
        for (String schoolName : schoolNames) {
            System.out.println("Processing attendance records for: " + schoolName);
            
            // Determine all years that need attendance tables
            // We need to generate attendance for all years from when the oldest current student was in Class 1
            // up to the current year
            int maxClass = classes.stream().mapToInt(Integer::intValue).max().orElse(12);
            int earliestYear = currentYear - maxClass + 1;
            
            System.out.println("  Years to generate: " + earliestYear + " to " + currentYear);
            
            // Create attendance tables for all needed years
            for (int year = earliestYear; year <= currentYear; year++) {
                createAttendanceTable(schoolName, year);
            }
            
            // Generate attendance data for each year (this will process ALL students for each year)
            for (int year = earliestYear; year <= currentYear; year++) {
                System.out.println("  Generating attendance for year " + year + "...");
                generateAttendanceRecordsForYear(schoolName, year);
            }
        }
        
        System.out.println("\n=== ATTENDANCE RECORDS GENERATION COMPLETE ===");
        System.out.println("Attendance tables created with format: {sanitized_school_name}_attendance_{year}");
        System.out.println("Columns: attendance_id, student_uuid, attendance_date, status, arrival_time, departure_time, remarks, created_at");
        System.out.println("Logic implemented: ALL students have attendance records for years they would have been in school");
        System.out.println("Attendance rates: Randomly distributed between 20% and 100% per student");
        System.out.println("Data volume: Each student will have ~200-250 attendance records per school year");
    }
    
    // Method to create attendance table for a specific year
    private static void createAttendanceTable(String schoolName, int year) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        String tableName = sanitizedSchoolName + "_attendance_" + year;
        
        try (Connection conn = getConnection()) {
            String createTableSQL = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    attendance_id SERIAL PRIMARY KEY,
                    student_uuid UUID NOT NULL,
                    attendance_date DATE NOT NULL,
                    status VARCHAR(20) NOT NULL CHECK (status IN ('Present', 'Absent', 'Late', 'Excused')),
                    arrival_time TIME,
                    departure_time TIME,
                    remarks TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(student_uuid, attendance_date)
                )
                """, tableName);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                System.out.println("    Attendance table created: " + tableName);
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating attendance table " + tableName + ": " + e.getMessage());
        }
    }
    
    // Method to generate attendance records for students in a specific year
    private static void generateAttendanceRecordsForYear(String schoolName, int year) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        String attendanceTableName = sanitizedSchoolName + "_attendance_" + year;
        String studentTableName = getStudentTableName(schoolName);
        
        try {
            int currentYear = 2025;
            java.util.List<String[]> studentsToProcess = new java.util.ArrayList<>();
            
            // First, get the list of students to process
            try (Connection conn = getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement("SELECT student_uuid, full_name, class_name FROM " + studentTableName);
                 ResultSet rs = selectStmt.executeQuery()) {
                
                while (rs.next()) {
                    String studentUuid = rs.getString("student_uuid");
                    String studentName = rs.getString("full_name");
                    String className = rs.getString("class_name");
                    
                    // Extract class number from "Class X" format
                    int currentClass = Integer.parseInt(className.replace("Class ", ""));
                    
                    // Calculate what class this student was in during the attendance year
                    int studentClassDuringYear = currentClass - (currentYear - year);
                    
                    // Only include students who would have been in school (Class 1 or higher) during that year
                    if (studentClassDuringYear >= 1) {
                        studentsToProcess.add(new String[]{studentUuid, studentName, String.valueOf(studentClassDuringYear)});
                    }
                }
            }
            
            if (studentsToProcess.isEmpty()) {
                System.out.println("    No students were in school during " + year + " for " + schoolName);
                return;
            }
            
            System.out.println("    Generating attendance records for " + studentsToProcess.size() + " students in year " + year);
            System.out.println("      (Students who were in Classes 1-" + 
                studentsToProcess.stream()
                    .mapToInt(s -> Integer.parseInt(s[2]))
                    .max().orElse(1) + " during " + year + ")");
            
            // Process students in batches to avoid connection leaks
            int batchSize = 25; // Process 25 students per connection (attendance has more records per student)
            for (int i = 0; i < studentsToProcess.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, studentsToProcess.size());
                java.util.List<String[]> batch = studentsToProcess.subList(i, endIndex);
                
                // Use a new connection for each batch
                try (Connection conn = getConnection()) {
                    for (String[] studentData : batch) {
                        String studentUuid = studentData[0];
                        String studentName = studentData[1];
                        // studentData[2] contains classDuringYear info (for reference only)
                        
                        generateStudentAttendanceForYear(conn, attendanceTableName, studentUuid, studentName, year);
                    }
                    
                    // Report progress
                    System.out.println("      Processed " + Math.min(endIndex, studentsToProcess.size()) + "/" + studentsToProcess.size() + " students for year " + year);
                }
            }
            
            System.out.println("    Completed attendance generation for " + studentsToProcess.size() + " students in year " + year);
            
        } catch (SQLException e) {
            System.err.println("Error generating attendance records for " + schoolName + " Year " + year + ": " + e.getMessage());
        }
    }
    
    // Method to generate attendance records for a specific student for the entire year
    private static void generateStudentAttendanceForYear(Connection conn, String tableName, String studentUuid, String studentName, int year) {
        try {
            // Generate school calendar (excluding national holidays and weekends)
            java.util.List<java.time.LocalDate> schoolDays = generateSchoolCalendar(year);
            
            // Determine attendance percentage for this student (20% to 100%)
            double attendancePercentage = 0.20 + (Math.random() * 0.80); // 20% to 100%
            int totalDaysToAttend = (int) (schoolDays.size() * attendancePercentage);
            
            // Randomly select which days the student will be present
            java.util.Collections.shuffle(schoolDays);
            java.util.List<java.time.LocalDate> presentDays = schoolDays.subList(0, totalDaysToAttend);
            java.util.List<java.time.LocalDate> absentDays = schoolDays.subList(totalDaysToAttend, schoolDays.size());
            
            String insertSQL = String.format("""
                INSERT INTO %s (student_uuid, attendance_date, status, arrival_time, departure_time, remarks)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (student_uuid, attendance_date) DO NOTHING
                """, tableName);
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                // Insert present days
                for (java.time.LocalDate date : presentDays) {
                    pstmt.setObject(1, java.util.UUID.fromString(studentUuid));
                    pstmt.setDate(2, java.sql.Date.valueOf(date));
                    
                    // Determine status for present days
                    double statusRandom = Math.random();
                    String status;
                    java.time.LocalTime arrivalTime = null;
                    java.time.LocalTime departureTime = null;
                    String remarks = null;
                    
                    if (statusRandom < 0.85) { // 85% regular present
                        status = "Present";
                        arrivalTime = generateArrivalTime(false); // Regular arrival
                        departureTime = generateDepartureTime();
                    } else if (statusRandom < 0.95) { // 10% late
                        status = "Late";
                        arrivalTime = generateArrivalTime(true); // Late arrival
                        departureTime = generateDepartureTime();
                        remarks = "Late arrival";
                    } else { // 5% excused
                        status = "Excused";
                        arrivalTime = generateArrivalTime(false);
                        departureTime = generateDepartureTime();
                        remarks = generateExcusedRemark();
                    }
                    
                    pstmt.setString(3, status);
                    pstmt.setTime(4, arrivalTime != null ? java.sql.Time.valueOf(arrivalTime) : null);
                    pstmt.setTime(5, departureTime != null ? java.sql.Time.valueOf(departureTime) : null);
                    pstmt.setString(6, remarks);
                    
                    pstmt.addBatch();
                }
                
                // Insert absent days
                for (java.time.LocalDate date : absentDays) {
                    pstmt.setObject(1, java.util.UUID.fromString(studentUuid));
                    pstmt.setDate(2, java.sql.Date.valueOf(date));
                    pstmt.setString(3, "Absent");
                    pstmt.setTime(4, null);
                    pstmt.setTime(5, null);
                    pstmt.setString(6, generateAbsentRemark());
                    
                    pstmt.addBatch();
                }
                
                // Execute batch insert
                pstmt.executeBatch();
                
                // Log progress for large datasets
                if (Math.random() < 0.01) { // Log 1% of students for progress tracking
                    System.out.println("      Generated attendance for " + studentName + " (" + presentDays.size() + " present, " + absentDays.size() + " absent)");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating attendance for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to generate school calendar (excluding weekends and national holidays)
    private static java.util.List<java.time.LocalDate> generateSchoolCalendar(int year) {
        java.util.List<java.time.LocalDate> schoolDays = new java.util.ArrayList<>();
        
        // Define school session: April to March (Indian academic year)
        java.time.LocalDate startDate = java.time.LocalDate.of(year, 4, 1);
        java.time.LocalDate endDate = java.time.LocalDate.of(year + 1, 3, 31);
        
        // Add summer break (May 15 - June 15)
        java.time.LocalDate summerBreakStart = java.time.LocalDate.of(year, 5, 15);
        java.time.LocalDate summerBreakEnd = java.time.LocalDate.of(year, 6, 15);
        
        // Add winter break (December 25 - January 5)
        java.time.LocalDate winterBreakStart = java.time.LocalDate.of(year, 12, 25);
        java.time.LocalDate winterBreakEnd = java.time.LocalDate.of(year + 1, 1, 5);
        
        // Common Indian national holidays
        java.util.Set<java.time.LocalDate> holidays = java.util.Set.of(
            java.time.LocalDate.of(year, 1, 26),    // Republic Day
            java.time.LocalDate.of(year, 8, 15),    // Independence Day
            java.time.LocalDate.of(year, 10, 2),    // Gandhi Jayanti
            java.time.LocalDate.of(year, 10, 24),   // Dussehra (approximate)
            java.time.LocalDate.of(year, 11, 12),   // Diwali (approximate)
            java.time.LocalDate.of(year + 1, 3, 8)  // Holi (approximate)
        );
        
        java.time.LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Skip weekends
            if (currentDate.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && 
                currentDate.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                
                // Skip holidays
                if (!holidays.contains(currentDate)) {
                    // Skip summer break
                    if (currentDate.isBefore(summerBreakStart) || currentDate.isAfter(summerBreakEnd)) {
                        // Skip winter break
                        if (currentDate.isBefore(winterBreakStart) || currentDate.isAfter(winterBreakEnd)) {
                            schoolDays.add(currentDate);
                        }
                    }
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        
        return schoolDays;
    }
    
    // Method to generate arrival time
    private static java.time.LocalTime generateArrivalTime(boolean isLate) {
        if (isLate) {
            // Late arrival: 8:30 AM to 10:00 AM
            int hour = 8 + (Math.random() < 0.7 ? 0 : 1); // 70% chance 8:xx, 30% chance 9:xx
            int minute = 30 + (int) (Math.random() * 90); // 30-119 minutes past hour
            if (minute >= 60) {
                hour++;
                minute -= 60;
            }
            return java.time.LocalTime.of(Math.min(hour, 10), Math.min(minute, 59));
        } else {
            // Regular arrival: 7:30 AM to 8:15 AM
            int minute = 30 + (int) (Math.random() * 45); // 30-74 minutes past 7
            if (minute >= 60) {
                return java.time.LocalTime.of(8, minute - 60);
            } else {
                return java.time.LocalTime.of(7, minute);
            }
        }
    }
    
    // Method to generate departure time
    private static java.time.LocalTime generateDepartureTime() {
        // School ends around 3:00 PM to 4:00 PM
        int hour = 15 + (int) (Math.random() * 2); // 3 PM or 4 PM
        int minute = (int) (Math.random() * 60);   // 0-59 minutes
        return java.time.LocalTime.of(hour, minute);
    }
    
    // Method to generate excused remarks
    private static String generateExcusedRemark() {
        String[] excusedRemarks = {
            "Medical appointment", "Family emergency", "School event participation",
            "Educational trip", "Sports competition", "Cultural program",
            "Parent-teacher meeting", "Health checkup", "Exam exemption"
        };
        return excusedRemarks[(int) (Math.random() * excusedRemarks.length)];
    }
    
    // Method to generate absent remarks
    private static String generateAbsentRemark() {
        String[] absentRemarks = {
            "Illness", "Fever", "Family function", "Personal reasons",
            "Medical treatment", "Out of station", "Weather conditions",
            "Transportation issues", "Unexcused absence", null
        };
        String remark = absentRemarks[(int) (Math.random() * absentRemarks.length)];
        return remark; // null is acceptable for some absent days
    }
    
    // Method to generate homework records for students (interactive)
    private static void generateHomeworkRecords(java.util.Scanner scanner, java.util.List<String> schoolNames, int numClasses) {
        try {
            System.out.println("\n=== HOMEWORK RECORDS GENERATION ===");
            System.out.print("Do you want to generate homework records? (y/n): ");
            String choice = scanner.nextLine().trim().toLowerCase();
            
            if (!choice.equals("y") && !choice.equals("yes")) {
                System.out.println("Homework records generation skipped.");
                return;
            }
            
            generateAutomaticHomeworkRecords(numClasses, schoolNames);
            
        } catch (java.util.NoSuchElementException e) {
            System.out.println("\n=== INPUT ERROR ===");
            System.out.println("No more input available for homework generation.");
            System.out.println("Homework generation skipped.");
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN HOMEWORK GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Homework generation skipped.");
        }
    }
    
    // Method to generate homework records automatically
    private static void generateAutomaticHomeworkRecords(int numClasses, java.util.List<String> schoolNames) {
        System.out.println("Automatically generating homework records for all " + numClasses + " classes...");
        
        try {
            // Generate for all classes from 1 to numClasses
            java.util.List<Integer> classes = new java.util.ArrayList<>();
            for (int i = 1; i <= numClasses; i++) {
                classes.add(i);
            }
            
            generateHomeworkData(classes, schoolNames);
            
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN AUTOMATIC HOMEWORK GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Homework generation skipped.");
        }
    }
    
    // Method to generate homework data for selected classes and schools
    private static void generateHomeworkData(java.util.List<Integer> classes, java.util.List<String> schoolNames) {
        int currentYear = 2025;
        
        System.out.println("=== HOMEWORK GENERATION CONFIGURATION ===");
        System.out.println("Logic: ALL students will have homework records for years they were in school");
        System.out.println("       For example, students currently in Class 5 will have homework for:");
        System.out.println("       2021 (when in Class 1), 2022 (Class 2), 2023 (Class 3),");
        System.out.println("       2024 (Class 4), 2025 (Class 5)");
        System.out.println("Homework frequency: 2-4 assignments per week per subject");
        System.out.println("Quality scores: 60-100 for submitted assignments");
        System.out.println("Submission rates: 70-98% per student");
        System.out.println("===============================================\n");
        
        // Process each school
        for (String schoolName : schoolNames) {
            System.out.println("Processing homework records for: " + schoolName);
            
            // Determine all years that need homework tables
            int maxClass = classes.stream().mapToInt(Integer::intValue).max().orElse(12);
            int earliestYear = currentYear - maxClass + 1;
            
            System.out.println("  Years to generate: " + earliestYear + " to " + currentYear);
            
            // Create homework tables for all needed years
            for (int year = earliestYear; year <= currentYear; year++) {
                createHomeworkTable(schoolName, year);
            }
            
            // Generate homework data for each year (this will process ALL students for each year)
            for (int year = earliestYear; year <= currentYear; year++) {
                System.out.println("  Generating homework for year " + year + "...");
                generateHomeworkRecordsForYear(schoolName, year);
            }
        }
        
        System.out.println("\n=== HOMEWORK RECORDS GENERATION COMPLETE ===");
        System.out.println("Homework tables created with format: {sanitized_school_name}_homework_{year}");
        System.out.println("Columns: hw_id, student_uuid, subject, assigned_date, due_date, submitted_date, quality_score, status, created_at");
        System.out.println("Logic implemented: ALL students have homework records for years they would have been in school");
        System.out.println("Assignment frequency: 2-4 assignments per week per subject during school days");
        System.out.println("Data volume: Each student will have ~150-300 homework records per school year");
    }
    
    // Method to create homework table for a specific year
    private static void createHomeworkTable(String schoolName, int year) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        String tableName = sanitizedSchoolName + "_homework_" + year;
        
        try (Connection conn = getConnection()) {
            String createTableSQL = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    hw_id SERIAL PRIMARY KEY,
                    student_uuid UUID NOT NULL,
                    subject VARCHAR(50) NOT NULL,
                    assigned_date DATE NOT NULL,
                    due_date DATE NOT NULL,
                    submitted_date DATE,
                    quality_score INTEGER CHECK (quality_score BETWEEN 0 AND 100),
                    status VARCHAR(20) NOT NULL CHECK (status IN ('Assigned', 'Submitted', 'Late', 'Missing')),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(student_uuid, subject, assigned_date)
                )
                """, tableName);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                System.out.println("    Homework table created: " + tableName);
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating homework table " + tableName + ": " + e.getMessage());
        }
    }
    
    // Method to generate homework records for students in a specific year
    private static void generateHomeworkRecordsForYear(String schoolName, int year) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        String homeworkTableName = sanitizedSchoolName + "_homework_" + year;
        String studentTableName = getStudentTableName(schoolName);
        
        try {
            int currentYear = 2025;
            java.util.List<String[]> studentsToProcess = new java.util.ArrayList<>();
            
            // First, get the list of students to process
            try (Connection conn = getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement("SELECT student_uuid, full_name, class_name FROM " + studentTableName);
                 ResultSet rs = selectStmt.executeQuery()) {
                
                while (rs.next()) {
                    String studentUuid = rs.getString("student_uuid");
                    String studentName = rs.getString("full_name");
                    String className = rs.getString("class_name");
                    
                    // Extract class number from "Class X" format
                    int currentClass = Integer.parseInt(className.replace("Class ", ""));
                    
                    // Calculate what class this student was in during the homework year
                    int studentClassDuringYear = currentClass - (currentYear - year);
                    
                    // Only include students who would have been in school (Class 1 or higher) during that year
                    if (studentClassDuringYear >= 1) {
                        studentsToProcess.add(new String[]{studentUuid, studentName, String.valueOf(studentClassDuringYear)});
                    }
                }
            }
            
            if (studentsToProcess.isEmpty()) {
                System.out.println("    No students were in school during " + year + " for " + schoolName);
                return;
            }
            
            System.out.println("    Processing " + studentsToProcess.size() + " students for homework in " + year);
            
            // Process students in batches to avoid connection leaks
            int batchSize = 50; // Process 50 students per connection
            for (int i = 0; i < studentsToProcess.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, studentsToProcess.size());
                java.util.List<String[]> batch = studentsToProcess.subList(i, endIndex);
                
                // Use a new connection for each batch
                try (Connection conn = getConnection()) {
                    for (String[] studentData : batch) {
                        String studentUuid = studentData[0];
                        String studentName = studentData[1];
                        int studentClass = Integer.parseInt(studentData[2]);
                        
                        generateStudentHomeworkForYear(conn, homeworkTableName, studentUuid, studentName, studentClass, year);
                    }
                    
                    // Report progress
                    System.out.println("      Processed " + Math.min(endIndex, studentsToProcess.size()) + "/" + studentsToProcess.size() + " students");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating homework records for year " + year + ": " + e.getMessage());
        }
    }
    
    // Method to generate homework records for a specific student for the entire year
    private static void generateStudentHomeworkForYear(Connection conn, String tableName, String studentUuid, String studentName, int studentClass, int year) {
        try {
            // Get subjects based on class
            String[] subjects = getSubjectsForClass(studentClass);
            
            // Generate school calendar for the year (excluding weekends and holidays)
            java.util.List<java.time.LocalDate> schoolDays = generateSchoolCalendar(year);
            
            // Student's submission rate (70% to 98%)
            double submissionRate = 0.70 + (Math.random() * 0.28);
            
            // Generate homework assignments
            String insertSQL = "INSERT INTO " + tableName + 
                " (student_uuid, subject, assigned_date, due_date, submitted_date, quality_score, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                int assignmentCount = 0;
                
                // For each subject, generate assignments throughout the year
                for (String subject : subjects) {
                    // 2-4 assignments per week per subject
                    int assignmentsPerWeek = 2 + (int) (Math.random() * 3);
                    int daysPerAssignment = 7 / assignmentsPerWeek;
                    
                    for (int i = 0; i < schoolDays.size(); i += daysPerAssignment) {
                        if (i >= schoolDays.size()) break;
                        
                        java.time.LocalDate assignedDate = schoolDays.get(i);
                        
                        // Due date is typically 2-7 days after assignment
                        int daysToComplete = 2 + (int) (Math.random() * 6);
                        java.time.LocalDate dueDate = assignedDate.plusDays(daysToComplete);
                        
                        // Determine if student submitted this assignment
                        boolean submitted = Math.random() < submissionRate;
                        
                        java.time.LocalDate submittedDate = null;
                        Integer qualityScore = null;
                        String status = "Assigned";
                        
                        if (submitted) {
                            // Student submitted - determine when and quality
                            if (Math.random() < 0.85) {
                                // On time submission (85% of submitted assignments)
                                int daysEarly = (int) (Math.random() * (daysToComplete - 1));
                                submittedDate = assignedDate.plusDays(daysToComplete - daysEarly);
                                status = "Submitted";
                            } else {
                                // Late submission (15% of submitted assignments)
                                int daysLate = 1 + (int) (Math.random() * 3);
                                submittedDate = dueDate.plusDays(daysLate);
                                status = "Late";
                            }
                            
                            // Quality score for submitted assignments (60-100)
                            qualityScore = 60 + (int) (Math.random() * 41);
                            
                        } else {
                            // Not submitted
                            status = "Missing";
                        }
                        
                        // Insert the homework record
                        pstmt.setObject(1, java.util.UUID.fromString(studentUuid));
                        pstmt.setString(2, subject);
                        pstmt.setDate(3, java.sql.Date.valueOf(assignedDate));
                        pstmt.setDate(4, java.sql.Date.valueOf(dueDate));
                        pstmt.setDate(5, submittedDate != null ? java.sql.Date.valueOf(submittedDate) : null);
                        pstmt.setObject(6, qualityScore);
                        pstmt.setString(7, status);
                        
                        pstmt.addBatch();
                        assignmentCount++;
                        
                        // Execute batch every 100 records
                        if (assignmentCount % 100 == 0) {
                            pstmt.executeBatch();
                        }
                    }
                }
                
                // Execute remaining batch
                pstmt.executeBatch();
                
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating homework for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to get subjects based on class level
    private static String[] getSubjectsForClass(int classLevel) {
        if (classLevel >= 1 && classLevel <= 5) {
            // Primary classes (1-5)
            return new String[]{"Mathematics", "English", "Hindi", "Environmental Studies", "Art & Craft"};
        } else if (classLevel >= 6 && classLevel <= 8) {
            // Middle classes (6-8)
            return new String[]{"Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science"};
        } else if (classLevel >= 9 && classLevel <= 10) {
            // Secondary classes (9-10)
            return new String[]{"Mathematics", "English", "Hindi", "Science", "Social Studies", "Computer Science", "Physical Education"};
        } else if (classLevel == 11 || classLevel == 12) {
            // Higher secondary - return common subjects (stream-specific logic can be added later)
            return new String[]{"Mathematics", "English", "Physics", "Chemistry", "Biology", "Computer Science"};
        } else {
            // Default subjects
            return new String[]{"Mathematics", "English", "Science"};
        }
    }
    
    // Method to generate project records for students (interactive)
    private static void generateProjectRecords(java.util.Scanner scanner, java.util.List<String> schoolNames, int numClasses) {
        try {
            System.out.println("\n=== PROJECT RECORDS GENERATION ===");
            System.out.print("Do you want to generate project records? (y/n): ");
            String choice = scanner.nextLine().trim().toLowerCase();
            
            if (!choice.equals("y") && !choice.equals("yes")) {
                System.out.println("Project records generation skipped.");
                return;
            }
            
            generateAutomaticProjectRecords(numClasses, schoolNames);
            
        } catch (java.util.NoSuchElementException e) {
            System.out.println("\n=== INPUT ERROR ===");
            System.out.println("No more input available for project generation.");
            System.out.println("Project generation skipped.");
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN PROJECT GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Project generation skipped.");
        }
    }
    
    // Method to generate project records automatically
    private static void generateAutomaticProjectRecords(int numClasses, java.util.List<String> schoolNames) {
        System.out.println("Automatically generating project records for all " + numClasses + " classes...");
        
        try {
            // Generate for all classes from 1 to numClasses
            java.util.List<Integer> classes = new java.util.ArrayList<>();
            for (int i = 1; i <= numClasses; i++) {
                classes.add(i);
            }
            
            generateProjectData(classes, schoolNames);
            
        } catch (Exception e) {
            System.out.println("\n=== ERROR IN AUTOMATIC PROJECT GENERATION ===");
            System.out.println("Error: " + e.getMessage());
            System.out.println("Project generation skipped.");
        }
    }
    
    // Method to generate project data for selected classes and schools
    private static void generateProjectData(java.util.List<Integer> classes, java.util.List<String> schoolNames) {
        int currentYear = 2025;
        
        System.out.println("=== PROJECT GENERATION CONFIGURATION ===");
        System.out.println("Logic: ALL students will have project records for years they were in school");
        System.out.println("       For example, students currently in Class 5 will have projects for:");
        System.out.println("       2021 (when in Class 1), 2022 (Class 2), 2023 (Class 3),");
        System.out.println("       2024 (Class 4), 2025 (Class 5)");
        System.out.println("Project frequency: 1-2 major projects per term per subject");
        System.out.println("Grades: A+ to F for submitted projects");
        System.out.println("Submission rates: 75-95% per student");
        System.out.println("===============================================\n");
        
        // Process each school
        for (String schoolName : schoolNames) {
            System.out.println("Processing project records for: " + schoolName);
            
            // Determine all years that need project tables
            int maxClass = classes.stream().mapToInt(Integer::intValue).max().orElse(12);
            int earliestYear = currentYear - maxClass + 1;
            
            System.out.println("  Years to generate: " + earliestYear + " to " + currentYear);
            
            // Create project tables for all needed years
            for (int year = earliestYear; year <= currentYear; year++) {
                createProjectTable(schoolName, year);
            }
            
            // Generate project data for each year (this will process ALL students for each year)
            for (int year = earliestYear; year <= currentYear; year++) {
                System.out.println("  Generating projects for year " + year + "...");
                generateProjectRecordsForYear(schoolName, year);
            }
        }
        
        System.out.println("\n=== PROJECT RECORDS GENERATION COMPLETE ===");
        System.out.println("Project tables created with format: {sanitized_school_name}_projects_{year}");
        System.out.println("Columns: project_id, student_uuid, title, subject, assigned_date, due_date, submitted_date, grade, status, remarks, created_at");
        System.out.println("Logic implemented: ALL students have project records for years they would have been in school");
        System.out.println("Project frequency: 1-2 major projects per term per subject during school year");
        System.out.println("Data volume: Each student will have ~10-25 project records per school year");
    }
    
    // Method to create project table for a specific year
    private static void createProjectTable(String schoolName, int year) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        String tableName = sanitizedSchoolName + "_projects_" + year;
        
        try (Connection conn = getConnection()) {
            String createTableSQL = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    project_id SERIAL PRIMARY KEY,
                    student_uuid UUID NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    subject VARCHAR(50) NOT NULL,
                    assigned_date DATE NOT NULL,
                    due_date DATE NOT NULL,
                    submitted_date DATE,
                    grade VARCHAR(5) CHECK (grade IN ('A+', 'A', 'A-', 'B+', 'B', 'B-', 'C+', 'C', 'C-', 'D+', 'D', 'F', 'I')),
                    status VARCHAR(20) NOT NULL CHECK (status IN ('Assigned', 'In Progress', 'Submitted', 'Late', 'Missing', 'Graded')),
                    remarks TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(student_uuid, title, assigned_date)
                )
                """, tableName);
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                System.out.println("    Project table created: " + tableName);
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating project table " + tableName + ": " + e.getMessage());
        }
    }
    
    // Method to generate project records for students in a specific year
    private static void generateProjectRecordsForYear(String schoolName, int year) {
        String sanitizedSchoolName = schoolName.toLowerCase()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        String projectTableName = sanitizedSchoolName + "_projects_" + year;
        String studentTableName = getStudentTableName(schoolName);
        
        try {
            int currentYear = 2025;
            java.util.List<String[]> studentsToProcess = new java.util.ArrayList<>();
            
            // First, get the list of students to process
            try (Connection conn = getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement("SELECT student_uuid, full_name, class_name FROM " + studentTableName);
                 ResultSet rs = selectStmt.executeQuery()) {
                
                while (rs.next()) {
                    String studentUuid = rs.getString("student_uuid");
                    String studentName = rs.getString("full_name");
                    String className = rs.getString("class_name");
                    
                    // Extract class number from "Class X" format
                    int currentClass = Integer.parseInt(className.replace("Class ", ""));
                    
                    // Calculate what class this student was in during the project year
                    int studentClassDuringYear = currentClass - (currentYear - year);
                    
                    // Only include students who would have been in school (Class 1 or higher) during that year
                    if (studentClassDuringYear >= 1) {
                        studentsToProcess.add(new String[]{studentUuid, studentName, String.valueOf(studentClassDuringYear)});
                    }
                }
            }
            
            if (studentsToProcess.isEmpty()) {
                System.out.println("    No students were in school during " + year + " for " + schoolName);
                return;
            }
            
            System.out.println("    Processing " + studentsToProcess.size() + " students for projects in " + year);
            
            // Process students in batches to avoid connection leaks
            int batchSize = 75; // Process 75 students per connection (projects have fewer records per student)
            for (int i = 0; i < studentsToProcess.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, studentsToProcess.size());
                java.util.List<String[]> batch = studentsToProcess.subList(i, endIndex);
                
                // Use a new connection for each batch
                try (Connection conn = getConnection()) {
                    for (String[] studentData : batch) {
                        String studentUuid = studentData[0];
                        String studentName = studentData[1];
                        int studentClass = Integer.parseInt(studentData[2]);
                        
                        generateStudentProjectsForYear(conn, projectTableName, studentUuid, studentName, studentClass, year);
                    }
                    
                    // Report progress
                    System.out.println("      Processed " + Math.min(endIndex, studentsToProcess.size()) + "/" + studentsToProcess.size() + " students");
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating project records for year " + year + ": " + e.getMessage());
        }
    }
    
    // Method to generate project records for a specific student for the entire year
    private static void generateStudentProjectsForYear(Connection conn, String tableName, String studentUuid, String studentName, int studentClass, int year) {
        try {
            // Get subjects based on class
            String[] subjects = getSubjectsForClass(studentClass);
            
            // Student's submission rate (75% to 95%)
            double submissionRate = 0.75 + (Math.random() * 0.20);
            
            // Generate project assignments
            String insertSQL = "INSERT INTO " + tableName + 
                " (student_uuid, title, subject, assigned_date, due_date, submitted_date, grade, status, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                int projectCount = 0;
                
                // For each subject, generate 1-2 projects per term (assuming 2-3 terms per year)
                for (String subject : subjects) {
                    int projectsPerYear = 2 + (int) (Math.random() * 4); // 2-5 projects per subject per year
                    
                    for (int projNum = 1; projNum <= projectsPerYear; projNum++) {
                        // Generate project title
                        String projectTitle = generateProjectTitle(subject, studentClass);
                        
                        // Assign projects throughout the year
                        int dayOfYear = (int) (Math.random() * 300) + 1; // Random day in school year
                        java.time.LocalDate assignedDate = java.time.LocalDate.of(year, 1, 1).plusDays(dayOfYear);
                        
                        // Due date is typically 2-4 weeks after assignment
                        int weeksToComplete = 2 + (int) (Math.random() * 3);
                        java.time.LocalDate dueDate = assignedDate.plusWeeks(weeksToComplete);
                        
                        // Determine if student submitted this project
                        boolean submitted = Math.random() < submissionRate;
                        
                        java.time.LocalDate submittedDate = null;
                        String grade = null;
                        String status = "Assigned";
                        String remarks = null;
                        
                        if (submitted) {
                            // Student submitted - determine when and quality
                            if (Math.random() < 0.80) {
                                // On time submission (80% of submitted projects)
                                int daysEarly = (int) (Math.random() * 7); // 0-7 days early
                                submittedDate = dueDate.minusDays(daysEarly);
                                status = "Graded";
                            } else {
                                // Late submission (20% of submitted projects)
                                int daysLate = 1 + (int) (Math.random() * 14); // 1-14 days late
                                submittedDate = dueDate.plusDays(daysLate);
                                status = "Graded";
                                remarks = "Late submission";
                            }
                            
                            // Generate grade for submitted projects
                            grade = generateProjectGrade();
                            
                            // Generate remarks based on grade
                            if (remarks == null) {
                                remarks = generateProjectRemarks(grade);
                            }
                            
                        } else {
                            // Not submitted
                            status = "Missing";
                            grade = "F";
                            remarks = "Project not submitted";
                        }
                        
                        // Insert the project record
                        pstmt.setObject(1, java.util.UUID.fromString(studentUuid));
                        pstmt.setString(2, projectTitle);
                        pstmt.setString(3, subject);
                        pstmt.setDate(4, java.sql.Date.valueOf(assignedDate));
                        pstmt.setDate(5, java.sql.Date.valueOf(dueDate));
                        pstmt.setDate(6, submittedDate != null ? java.sql.Date.valueOf(submittedDate) : null);
                        pstmt.setString(7, grade);
                        pstmt.setString(8, status);
                        pstmt.setString(9, remarks);
                        
                        pstmt.addBatch();
                        projectCount++;
                        
                        // Execute batch every 50 records
                        if (projectCount % 50 == 0) {
                            pstmt.executeBatch();
                        }
                    }
                }
                
                // Execute remaining batch
                pstmt.executeBatch();
                
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating projects for student " + studentName + ": " + e.getMessage());
        }
    }
    
    // Method to generate project titles based on subject and class
    private static String generateProjectTitle(String subject, int classLevel) {
        java.util.Map<String, String[]> projectTitles = new java.util.HashMap<>();
        
        // Mathematics projects
        projectTitles.put("Mathematics", new String[]{
            "Geometry in Architecture", "Statistics Survey Project", "Probability Experiments",
            "Mathematical Patterns in Nature", "Budgeting and Finance", "Measurement Project",
            "Number System Investigation", "Graph Theory Applications", "Trigonometry in Real Life",
            "Area and Volume Calculations", "Mathematical Art Project", "Data Analysis Report"
        });
        
        // Science projects
        projectTitles.put("Science", new String[]{
            "Plant Growth Experiment", "Solar System Model", "Water Cycle Investigation",
            "Chemical Reactions Lab", "Weather Monitoring Project", "Ecosystem Study",
            "Simple Machines Demo", "Light and Shadow Experiments", "Magnetism Investigation",
            "States of Matter Project", "Human Body Systems", "Environmental Conservation"
        });
        
        // English projects
        projectTitles.put("English", new String[]{
            "Creative Writing Portfolio", "Poetry Analysis Project", "Character Study Report",
            "Book Review Assignment", "Drama Performance", "Newspaper Creation",
            "Interview Project", "Story Illustration", "Grammar Games", "Vocabulary Building",
            "Reading Comprehension Study", "Literature Circle Discussion"
        });
        
        // Social Studies projects
        projectTitles.put("Social Studies", new String[]{
            "Historical Timeline Project", "Cultural Heritage Study", "Geography Mapping",
            "Government Systems Research", "Ancient Civilizations", "Community Helpers Study",
            "Festival Celebration Project", "Freedom Fighters Biography", "Map Making Exercise",
            "Constitution Study", "Current Events Analysis", "Local History Investigation"
        });
        
        // Add more subjects
        projectTitles.put("Hindi", new String[]{
            "Poetry Writing", "Story Presentation", "Grammar Project", "Author Study",
            "Language Development", "Cultural Study", "Literature Review", "Drama Performance"
        });
        
        projectTitles.put("Computer Science", new String[]{
            "Programming Fundamentals", "Website Creation", "Database Design", "Algorithm Analysis",
            "Technology Timeline", "Computer Graphics Project", "Digital Presentation", "Coding Challenge"
        });
        
        projectTitles.put("Environmental Studies", new String[]{
            "Pollution Awareness Campaign", "Recycling Project", "Garden Maintenance", "Animal Habitat Study",
            "Climate Change Research", "Water Conservation", "Renewable Energy Project", "Waste Management"
        });
        
        projectTitles.put("Art & Craft", new String[]{
            "Cultural Art Forms", "Handmade Crafts", "Painting Exhibition", "Sculpture Project",
            "Traditional Art Study", "Creative Expression", "Art History Timeline", "Mixed Media Creation"
        });
        
        projectTitles.put("Physical Education", new String[]{
            "Sports Statistics Analysis", "Fitness Plan Development", "Game Rules Study", "Health Awareness Campaign",
            "Exercise Routine Creation", "Sports History Research", "Nutrition Project", "Team Building Activities"
        });
        
        // Default subjects
        projectTitles.put("Physics", new String[]{
            "Motion and Forces", "Electricity and Magnetism", "Wave Properties", "Light Optics",
            "Thermodynamics Study", "Atomic Structure", "Energy Conservation", "Simple Machines"
        });
        
        projectTitles.put("Chemistry", new String[]{
            "Chemical Bonding", "Periodic Table Study", "Reaction Mechanisms", "Organic Compounds",
            "pH and Acids", "Crystallization Project", "Chemical Analysis", "Environmental Chemistry"
        });
        
        projectTitles.put("Biology", new String[]{
            "Cell Structure Study", "Genetics Experiment", "Photosynthesis Investigation", "Human Anatomy",
            "Ecosystem Analysis", "Biodiversity Project", "Evolution Timeline", "Microbiology Study"
        });
        
        String[] titles = projectTitles.getOrDefault(subject, new String[]{"Research Project", "Investigation Study", "Analysis Report"});
        return titles[(int) (Math.random() * titles.length)];
    }
    
    // Method to generate project grades
    private static String generateProjectGrade() {
        String[] grades = {"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "F"};
        double[] probabilities = {0.10, 0.15, 0.15, 0.20, 0.15, 0.10, 0.08, 0.05, 0.02, 0.00, 0.00, 0.00}; // Very few F grades for projects
        
        double random = Math.random();
        double cumulative = 0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (random <= cumulative) {
                return grades[i];
            }
        }
        return "B"; // Default
    }
    
    // Method to generate project remarks based on grade
    private static String generateProjectRemarks(String grade) {
        java.util.Map<String, String[]> gradeRemarks = new java.util.HashMap<>();
        
        gradeRemarks.put("A+", new String[]{
            "Outstanding work!", "Exceptional creativity and effort", "Exceeds all expectations",
            "Excellent research and presentation", "Remarkable attention to detail"
        });
        
        gradeRemarks.put("A", new String[]{
            "Excellent work", "Great effort and creativity", "Well researched and presented",
            "Strong understanding demonstrated", "High quality work"
        });
        
        gradeRemarks.put("A-", new String[]{
            "Very good work", "Good effort shown", "Well organized project",
            "Good understanding of concepts", "Quality presentation"
        });
        
        gradeRemarks.put("B+", new String[]{
            "Good work overall", "Satisfactory effort", "Meets most requirements",
            "Good understanding shown", "Well presented"
        });
        
        gradeRemarks.put("B", new String[]{
            "Satisfactory work", "Adequate effort", "Meets basic requirements",
            "Acceptable understanding", "Could use more detail"
        });
        
        gradeRemarks.put("B-", new String[]{
            "Needs some improvement", "Basic requirements met", "Could be more thorough",
            "Show more effort next time", "Additional research needed"
        });
        
        gradeRemarks.put("C+", new String[]{
            "Meets minimum standards", "More effort required", "Incomplete work",
            "Needs better organization", "Additional work needed"
        });
        
        gradeRemarks.put("C", new String[]{
            "Below expectations", "Incomplete submission", "Needs significant improvement",
            "More research required", "Poorly organized"
        });
        
        gradeRemarks.put("F", new String[]{
            "Not submitted", "Unacceptable work", "Did not meet requirements",
            "No effort shown", "Please resubmit"
        });
        
        String[] remarks = gradeRemarks.getOrDefault(grade, new String[]{"Work reviewed"});
        return remarks[(int) (Math.random() * remarks.length)];
    }
}
