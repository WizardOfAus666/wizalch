@echo off
REM Launches the full RuneLite Client in Developer Mode with AlchMate Pro loaded
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.11.9-hotspot\bin\java.exe'; " ^
  "$proj = '%~dp0'; " ^
  "& \"$proj\mvnw.cmd\" test-compile; " ^
  "$jars = Get-ChildItem -Path \"$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\" -Recurse -File -Filter '*.jar' | Where-Object { $_.Name -notmatch '1.13.2-SNAPSHOT' } | Select-Object -ExpandProperty FullName; " ^
  "$cp = \"$proj\target\test-classes;$proj\target\classes;\" + ($jars -join ';'); " ^
  "& $jdk -ea -cp $cp com.alchmatepro.AlchMateProPluginTest --developer-mode --debug"
