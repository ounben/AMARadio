@echo off
cd /d "C:\Users\bou\AppData\Local\Android\Sdk\extras\google\auto"

Port 5277 weiterleiten
adb forward tcp:5277 tcp:5277

DHU starten
start "" "desktop-head-unit.exe"