# Run Main.java with all dependencies (Interactive Mode)
Write-Host "=== STUDENT DATA GENERATOR (INTERACTIVE MODE) ===" -ForegroundColor Cyan
Write-Host "This will run the application in interactive mode." -ForegroundColor Green
Write-Host "You will be prompted for all inputs including academic results." -ForegroundColor Yellow

# Compile first
Write-Host "`nCompiling Main.java with dependencies..." -ForegroundColor Green
javac -cp "dependencies\*;." Main.java

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful!" -ForegroundColor Green
} else {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit 1
}

# Run Main.java with all dependencies
Write-Host "`nRunning Main application with dependencies..." -ForegroundColor Green
java -cp "dependencies\*;." Main