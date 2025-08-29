# Large Dataset Handler for Student Data Generator
# This script provides safe handling of large datasets with memory monitoring

Write-Host "=== LARGE DATASET STUDENT DATA GENERATOR ===" -ForegroundColor Cyan
Write-Host "This script is optimized for generating large datasets safely." -ForegroundColor Green
Write-Host ""

# Check available system memory
$totalRAM = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2)
Write-Host "System RAM: $totalRAM GB" -ForegroundColor Yellow

# Recommend heap size based on available RAM
$recommendedHeap = [math]::Floor($totalRAM * 0.6)
if ($recommendedHeap -lt 2) { $recommendedHeap = 2 }
if ($recommendedHeap -gt 16) { $recommendedHeap = 16 }

Write-Host "Recommended Java heap size: $recommendedHeap GB" -ForegroundColor Yellow
Write-Host ""

# Ask user for dataset size estimation
Write-Host "Dataset Size Guidelines:" -ForegroundColor Cyan
Write-Host "  Small:  < 100K students   (< 2 GB RAM needed)" -ForegroundColor Green
Write-Host "  Medium: 100K - 1M students (2-4 GB RAM needed)" -ForegroundColor Yellow
Write-Host "  Large:  1M - 10M students (4-8 GB RAM needed)" -ForegroundColor Red
Write-Host "  Huge:   > 10M students    (8+ GB RAM needed)" -ForegroundColor Magenta
Write-Host ""

$response = Read-Host "Do you want to proceed with large dataset generation? (y/n)"
if ($response -ne 'y' -and $response -ne 'yes') {
    Write-Host "Large dataset generation cancelled." -ForegroundColor Yellow
    exit 0
}

# Compile first
Write-Host "`nCompiling Main.java with dependencies..." -ForegroundColor Green
javac -cp "dependencies\*" -d . Main.java

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful!" -ForegroundColor Green
} else {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit 1
}

# Run with optimized JVM settings for large datasets
Write-Host "`nStarting application with optimized settings..." -ForegroundColor Green
Write-Host "Java heap size: $recommendedHeap GB" -ForegroundColor Cyan
Write-Host "Garbage collector: G1 (optimized for large heaps)" -ForegroundColor Cyan
Write-Host "Memory monitoring: Enabled" -ForegroundColor Cyan
Write-Host ""

Write-Host "IMPORTANT TIPS FOR LARGE DATASETS:" -ForegroundColor Yellow
Write-Host "1. Ensure PostgreSQL has sufficient disk space" -ForegroundColor White
Write-Host "2. Monitor memory usage shown in the application" -ForegroundColor White
Write-Host "3. If memory warnings appear, consider reducing dataset size" -ForegroundColor White
Write-Host "4. The application will show progress every 1000 students for large datasets" -ForegroundColor White
Write-Host ""

# Run with optimized JVM parameters
java -Xmx"$($recommendedHeap)g" `
     -Xms"$([math]::Floor($recommendedHeap/2))g" `
     -XX:+UseG1GC `
     -XX:MaxGCPauseMillis=200 `
     -XX:+UnlockExperimentalVMOptions `
     -XX:+UseStringDeduplication `
     -XX:+PrintGCDetails `
     -XX:+PrintGCTimeStamps `
     -cp "dependencies\*;." Main

# Check exit status
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nLarge dataset generation completed successfully!" -ForegroundColor Green
} else {
    Write-Host "`nLarge dataset generation failed or was interrupted." -ForegroundColor Red
    Write-Host "Common causes:" -ForegroundColor Yellow
    Write-Host "  - Insufficient memory (try reducing dataset size)" -ForegroundColor White
    Write-Host "  - Database connection issues (check PostgreSQL)" -ForegroundColor White
    Write-Host "  - Disk space shortage (check available space)" -ForegroundColor White
}