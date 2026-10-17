@ECHO OFF
SETLOCAL
SET "MVNW_PROJECT_DIR=%~dp0"
SET "MVNW_VERSION=3.9.16"
IF "%MAVEN_USER_HOME%"=="" SET "MAVEN_USER_HOME=%USERPROFILE%\.m2"
SET "MVNW_CACHE_ROOT=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MVNW_VERSION%"
SET "MVNW_MAVEN_CMD=%MVNW_CACHE_ROOT%\apache-maven-%MVNW_VERSION%\bin\mvn.cmd"

IF NOT EXIST "%MVNW_MAVEN_CMD%" (
  SET "MVNW_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVNW_VERSION%/apache-maven-%MVNW_VERSION%-bin.zip"
  POWERSHELL -NoProfile -ExecutionPolicy Bypass -Command "$target='%MVNW_CACHE_ROOT%'; $zip=[IO.Path]::GetTempFileName()+'.zip'; Invoke-WebRequest -UseBasicParsing '%MVNW_URL%' -OutFile $zip; New-Item -ItemType Directory -Force $target | Out-Null; Expand-Archive $zip $target -Force; Remove-Item $zip"
  IF ERRORLEVEL 1 EXIT /B 1
)

CALL "%MVNW_MAVEN_CMD%" -f "%MVNW_PROJECT_DIR%pom.xml" %*
ENDLOCAL
