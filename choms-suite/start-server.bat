@echo off
REM Starts the CHOMS website and web app locally and opens the browser.
cd /d "%~dp0"
start "" http://127.0.0.1:8123/index.html
echo Website : http://127.0.0.1:8123/index.html
echo Web app : http://127.0.0.1:8123/app/index.html
echo Keep this window open. Press Ctrl+C to stop.
npx --yes http-server web -p 8123 -c-1
