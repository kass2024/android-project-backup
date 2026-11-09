@echo off
echo [INFO] install sdk jar.

cd %~dp0
cd ..

set MAVEN_OPTS=%MAVEN_OPTS% -XX:MaxPermSize=128m
call mvn install:install-file -Dfile=./lib/hf-tcp-gateway-1.0.0.jar -DgroupId=com.hfims.boot -DartifactId=hf-tcp-gateway -Dversion=1.0.0 -Dpackaging=jar -DgeneratePom=true

cd bin
pause