@echo off
cd /d "%~dp0"
echo Fermez Novera avant de continuer.
pause
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.fasodev.gestionscolaire.tools.ResetMotDePasse
echo.
pause
