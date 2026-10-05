@echo off
node "%~dp0..\tools\build-all.mjs" --loader=fabric %*
exit /b %ERRORLEVEL%
