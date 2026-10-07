@echo off
setlocal
set DIRNAME=%~dp0
if exist "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" (
  set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
) else (
  set CLASSPATH=
)
java -jar %CLASSPATH% %*
