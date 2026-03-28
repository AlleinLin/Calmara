@echo off
echo Building Calmara application...

cd /d "%~dp0\.."

call mvn clean package -DskipTests

echo Build completed!
echo JAR file: Calmara-web\target\*.jar

pause
