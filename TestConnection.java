import java.sql.*;

public class TestConnection {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/student_management";
        String user = "postgres";
        String password = "Sutirtha_05@Postgress"; // Change this to your PostgreSQL password
        
        System.out.println("Testing PostgreSQL connection...");
        
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("SUCCESS: Connected to PostgreSQL database!");
            
            // Test basic query
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT version()");
            if (rs.next()) {
                System.out.println("PostgreSQL Version: " + rs.getString(1));
            }
            
            conn.close();
            System.out.println("Connection closed successfully.");
            
        } catch (ClassNotFoundException e) {
            System.err.println("ERROR: PostgreSQL JDBC Driver not found!");
            System.err.println("Make sure postgresql-42.7.3.jar is in the classpath.");
        } catch (SQLException e) {
            System.err.println("ERROR: Database connection failed!");
            System.err.println("Details: " + e.getMessage());
            System.err.println("\nPossible solutions:");
            System.err.println("1. Make sure PostgreSQL is running");
            System.err.println("2. Check database name: student_management");
            System.err.println("3. Check username and password");
            System.err.println("4. Ensure PostgreSQL accepts connections on localhost:5432");
        }
    }
}
