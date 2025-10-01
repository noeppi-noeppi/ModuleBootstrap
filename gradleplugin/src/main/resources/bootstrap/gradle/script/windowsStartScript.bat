@echo off
goto main

:die
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
exit /b %EXIT_CODE%

:main
setlocal
set APP_HOME=%~dp0
set APP_HOME=%APP_HOME%\..
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set JAVA_CMD=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_CMD%" goto launch

echo JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo Please set the JAVA_HOME variable in your environment to a valid java installation.
goto die

:launch
"%JAVA_CMD%" --module-path @@@MODULE_PATH@@@ --add-modules ALL-DEFAULT --add-modules ALL-MODULE-PATH @@@JVM_ARGS@@@ "--module" @@@MAIN_MODULE@@@ "$@"
if %ERRORLEVEL% neq 0 goto die
endlocal
