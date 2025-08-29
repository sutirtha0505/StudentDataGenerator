# PowerShell Script to Download Latest Dependencies to C:\jdbc\
# This script downloads the latest versions of HikariCP and SLF4J dependencies

Write-Host "=== Downloading Latest Dependencies to C:\jdbc\ ===" -ForegroundColor Green
Write-Host ""

# Define the target directory
$TargetDir = "C:\jdbc\"

# Ensure target directory exists
if (!(Test-Path -Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    Write-Host "Created directory: $TargetDir" -ForegroundColor Yellow
} else {
    Write-Host "Using existing directory: $TargetDir" -ForegroundColor Cyan
}

# Function to download a file with progress
function Download-File {
    param(
        [string]$Url,
        [string]$OutputPath,
        [string]$FileName
    )
    
    Write-Host "Downloading $FileName..." -ForegroundColor Cyan
    
    try {
        # Check if file already exists
        if (Test-Path $OutputPath) {
            $choice = Read-Host "File $FileName already exists. Overwrite? (y/N)"
            if ($choice -ne 'y' -and $choice -ne 'Y') {
                Write-Host "  [SKIP] Skipped $FileName" -ForegroundColor Yellow
                return $true
            }
        }
        
        # Download with progress bar
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($Url, $OutputPath)
        
        # Get file size for display
        $fileSize = [math]::Round((Get-Item $OutputPath).Length / 1MB, 2)
        Write-Host "  [OK] Downloaded $FileName ($fileSize MB)" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "  [ERROR] Failed to download $FileName : $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
    finally {
        if ($webClient) {
            $webClient.Dispose()
        }
    }
}

# Latest versions (as of 2024)
# Check Maven Central for the latest versions
$dependencies = @(
    @{
        Name = "HikariCP-6.0.0.jar"
        Url = "https://repo1.maven.org/maven2/com/zaxxer/HikariCP/6.0.0/HikariCP-6.0.0.jar"
        Description = "HikariCP - High-performance JDBC connection pool"
    },
    @{
        Name = "slf4j-api-2.0.13.jar"
        Url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar"
        Description = "SLF4J API - Simple Logging Facade for Java"
    },
    @{
        Name = "slf4j-simple-2.0.13.jar"
        Url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar"
        Description = "SLF4J Simple Implementation"
    },
    @{
        Name = "postgresql-42.7.4.jar"
        Url = "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar"
        Description = "PostgreSQL JDBC Driver (bonus - latest version)"
    }
)

Write-Host "Dependencies to download:" -ForegroundColor White
foreach ($dep in $dependencies) {
    Write-Host "  * $($dep.Name) - $($dep.Description)" -ForegroundColor Gray
}
Write-Host ""

# Download each dependency
$successCount = 0
$totalCount = $dependencies.Count

foreach ($dep in $dependencies) {
    $outputPath = Join-Path $TargetDir $dep.Name
    
    if (Download-File -Url $dep.Url -OutputPath $outputPath -FileName $dep.Name) {
        $successCount++
    }
}

Write-Host ""
Write-Host "=== Download Summary ===" -ForegroundColor Green

if ($successCount -eq $totalCount) {
    Write-Host "[SUCCESS] All $totalCount dependencies downloaded successfully!" -ForegroundColor Green
} else {
    Write-Host "[PARTIAL] Downloaded $successCount out of $totalCount dependencies" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[FILES] Files located in: $TargetDir" -ForegroundColor Cyan

# List downloaded files with sizes
if (Test-Path $TargetDir) {
    Write-Host ""
    Write-Host "[LISTING] Downloaded files:" -ForegroundColor White
    Get-ChildItem -Path $TargetDir -Filter "*.jar" | ForEach-Object {
        $sizeKB = [math]::Round($_.Length / 1KB, 1)
        $sizeMB = [math]::Round($_.Length / 1MB, 2)
        if ($sizeMB -gt 1) {
            Write-Host "  * $($_.Name) ($sizeMB MB)" -ForegroundColor Gray
        } else {
            Write-Host "  * $($_.Name) ($sizeKB KB)" -ForegroundColor Gray
        }
    }
}

Write-Host ""
Write-Host "[USAGE] Usage Notes:" -ForegroundColor Cyan
Write-Host "  * These JAR files can be used in your Java classpath" -ForegroundColor Gray
Write-Host "  * For your project, update the classpath to point to C:\jdbc\" -ForegroundColor Gray
Write-Host "  * Example: java -cp `"C:\jdbc\*;.`" YourMainClass" -ForegroundColor Gray

Write-Host ""
Write-Host "[INFO] Version Information:" -ForegroundColor Cyan
Write-Host "  * HikariCP 6.0.0 - Latest stable version with Java 8+ support" -ForegroundColor Gray
Write-Host "  * SLF4J 2.0.13 - Latest version with improved performance" -ForegroundColor Gray
Write-Host "  * PostgreSQL 42.7.4 - Latest JDBC driver with security updates" -ForegroundColor Gray

Write-Host ""
Write-Host "[COMPLETE] Download completed!" -ForegroundColor Green