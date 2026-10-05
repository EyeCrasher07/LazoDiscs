@echo off
node "%~dp0..\tools\build-all.mjs" --loader=neoforge %*
exit /b %ERRORLEVEL%
