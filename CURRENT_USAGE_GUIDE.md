# Student Data Generator - Current Usage Guide

## Overview
The application now supports three modes for academic results generation:

## Quick Test Commands

### 1. Automatic Mode (No Academic Results Input)
```powershell
echo "2`n1`n2`n1" | java -cp "dependencies\*;." Main
```
- Generates academic results automatically for all classes in the school structure
- Uses default configuration: 3 terms per year, current year + 1

### 2. Interactive Mode (Custom Academic Results)
```powershell
echo "2`n1`n2`n1`ny`n1-2`n2`n2025" | java -cp "dependencies\*;." Main
```
- Asks if you want to generate academic results (y/n)
- If 'y', allows custom configuration:
  - Class range (e.g., '1-2', '6-8')
  - Number of terms per year
  - Session year

### 3. Skip Academic Results Mode
```powershell
echo "2`n1`n2`n1`nn" | java -cp "dependencies\*;." Main
```
- Asks if you want to generate academic results (y/n)
- If 'n', skips academic results generation entirely

## Input Parameters Explained

### Basic School Structure
1. **Number of classes**: e.g., 12 for classes 1-12
2. **Sections per class**: e.g., 4 for sections A, B, C, D
3. **Students per section**: e.g., 30
4. **Number of schools**: e.g., 5

### Academic Results (Interactive Mode Only)
5. **Generate academic results**: y/n
6. **Class range**: e.g., '1-3' or '6-8'
7. **Terms per year**: e.g., 2 or 3
8. **Session year**: e.g., 2025

## Available Scripts

### Development Scripts
- `compile.ps1` - Compile the application
- `run.ps1` - Run with interactive prompts
- `quick_test.ps1` - Quick test with small dataset
- `large_dataset.ps1` - Test with large dataset
- `interactive.ps1` - Full interactive mode

### Example Usage
```powershell
# Quick compilation and test
.\compile.ps1; .\quick_test.ps1

# Large dataset test (automatic academic results)
.\large_dataset.ps1

# Full interactive experience
.\interactive.ps1
```

## Database Tables Created

### Student Tables
- `school_table` - Contains all schools with UUIDs
- `students_{school_name}` - One table per school with all student data

### Academic Tables (if generated)
- `{school_name}_class_{class_number}_{year}` - Academic results by class and year
- Contains subjects, marks, terms, and student performance data

## System Features

### Hardware Detection
- Automatically detects CPU cores
- Optimizes thread pool based on system capabilities
- Dynamic connection pool sizing

### Performance Optimizations
- Batch processing for database operations
- LRU caches to prevent memory exhaustion
- Progress monitoring for large datasets
- Retry logic for database operations

### Error Handling
- Robust input validation
- Connection pool management
- Graceful error recovery
- Clear error messages

## Troubleshooting

### Common Issues
1. **PostgreSQL not running**: Start PostgreSQL service
2. **Database connection errors**: Check connection parameters in code
3. **Out of memory**: Reduce batch size or dataset size
4. **Compilation errors**: Ensure all JAR dependencies are in `dependencies/` folder

### Performance Tips
- For datasets > 100K students, consider increasing heap size: `java -Xmx8g`
- Monitor progress output for large datasets
- Use automatic mode for fastest generation
- Interactive mode is best for testing specific configurations

## Current Status
✅ All compilation errors fixed
✅ Dynamic thread allocation implemented
✅ Robust error handling active
✅ Three academic results modes working
✅ Performance optimizations in place
✅ Comprehensive testing completed

The application is ready for production use with any dataset size.