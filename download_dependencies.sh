#!/bin/bash

# Download Script for Student Data Generator Dependencies
# This script downloads all required JAR files for the improved version

echo "=== Downloading Dependencies for Student Data Generator ==="
echo ""

# Base URL for Maven Central Repository
MAVEN_REPO="https://repo1.maven.org/maven2"

# Dependencies to download
declare -A DEPENDENCIES=(
    ["HikariCP-5.0.1.jar"]="com/zaxxer/HikariCP/5.0.1/HikariCP-5.0.1.jar"
    ["postgresql-42.7.3.jar"]="org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar"
    ["slf4j-api-2.0.9.jar"]="org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar"
    ["slf4j-simple-2.0.9.jar"]="org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar"
)

# Function to download a file with retry logic
download_file() {
    local filename="$1"
    local maven_path="$2"
    local url="$MAVEN_REPO/$maven_path"
    
    echo "Downloading $filename..."
    
    if [ -f "$filename" ]; then
        echo "  ✅ $filename already exists, skipping"
        return 0
    fi
    
    # Try downloading with wget first, then curl as fallback
    if command -v wget &> /dev/null; then
        if wget -q --show-progress "$url" -O "$filename"; then
            echo "  ✅ $filename downloaded successfully"
            return 0
        fi
    elif command -v curl &> /dev/null; then
        if curl -L --progress-bar "$url" -o "$filename"; then
            echo "  ✅ $filename downloaded successfully"
            return 0
        fi
    fi
    
    echo "  ❌ Failed to download $filename"
    return 1
}

# Check if wget or curl is available
if ! command -v wget &> /dev/null && ! command -v curl &> /dev/null; then
    echo "❌ Error: Neither wget nor curl is available"
    echo "Please install wget or curl to download dependencies"
    exit 1
fi

# Download all dependencies
echo "Starting downloads..."
echo ""

FAILED_DOWNLOADS=()
for filename in "${!DEPENDENCIES[@]}"; do
    maven_path="${DEPENDENCIES[$filename]}"
    if ! download_file "$filename" "$maven_path"; then
        FAILED_DOWNLOADS+=("$filename")
    fi
done

echo ""
echo "=== Download Summary ==="

if [ ${#FAILED_DOWNLOADS[@]} -eq 0 ]; then
    echo "✅ All dependencies downloaded successfully!"
    echo ""
    echo "Downloaded files:"
    for filename in "${!DEPENDENCIES[@]}"; do
        size=$(ls -lh "$filename" | awk '{print $5}')
        echo "  • $filename ($size)"
    done
    echo ""
    echo "🚀 You can now run the application with: ./run.sh"
    echo "📖 Or compile manually with:"
    echo "   javac -cp \".:HikariCP-5.0.1.jar:postgresql-42.7.3.jar:slf4j-api-2.0.9.jar:slf4j-simple-2.0.9.jar\" Main.java"
else
    echo "❌ Failed to download the following files:"
    for filename in "${FAILED_DOWNLOADS[@]}"; do
        echo "  • $filename"
    done
    echo ""
    echo "🔧 Manual download instructions:"
    echo "You can manually download the missing files from:"
    for filename in "${FAILED_DOWNLOADS[@]}"; do
        maven_path="${DEPENDENCIES[$filename]}"
        echo "  • $filename: $MAVEN_REPO/$maven_path"
    done
    exit 1
fi

echo ""
echo "📚 Next Steps:"
echo "1. Ensure PostgreSQL is running with database 'student_management'"
echo "2. Update database credentials in Main.java if needed"
echo "3. Run: ./run.sh"
echo ""
echo "For troubleshooting, see PERFORMANCE_IMPROVEMENTS.md"