@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests package
if errorlevel 1 (
  echo Nao foi possivel compilar. Confira se o JDK 17 ou superior esta instalado.
  exit /b 1
)
java -jar target\controle-estoque.jar %*
