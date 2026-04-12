@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Ищем java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%"=="0" goto execute

echo ERROR: JAVA_HOME не задан и 'java' не найден в PATH.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute

echo ERROR: JAVA_HOME указывает на несуществующую папку: %JAVA_HOME%
goto fail

:execute
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
    "-Dorg.gradle.appname=gradlew" ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain %*

:end
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal
