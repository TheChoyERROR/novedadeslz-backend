@echo off
setlocal EnableExtensions DisableDelayedExpansion

if not defined JAVA_HOME (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (
        set "JAVA_HOME=%%~fD"
        goto :java_home_found
    )
)

:java_home_found
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if exist ".env" (
    echo Cargando variables locales desde .env...
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /V /R "^#" ".env"`) do (
        if not "%%~A"=="" set "%%~A=%%~B"
    )
)

echo Iniciando backend con perfil local y base H2 en memoria...
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
