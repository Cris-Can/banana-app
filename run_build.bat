@echo off
call gradlew.bat assembleDebug > final_error.log 2>&1
findstr /i "error" final_error.log > filtered_errors.log
findstr /i "[ksp]" final_error.log >> filtered_errors.log
findstr /i "hilt" final_error.log >> filtered_errors.log
findstr /i "unresolved" final_error.log >> filtered_errors.log
