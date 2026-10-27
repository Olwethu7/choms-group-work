@echo off
REM One-click deploy of the website and web app to Firebase Hosting (project match-choms).
cd /d "%~dp0"
call npm install -g firebase-tools
call firebase login
call firebase deploy --only hosting --project match-choms
echo.
echo Your live links:
echo   https://match-choms.web.app
echo   https://match-choms.web.app/app/index.html
pause
