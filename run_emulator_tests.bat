@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
set FUNCTIONS_DISCOVERY_TIMEOUT=60000
call npx firebase-tools emulators:exec --project demo-fort-chat --only firestore,functions,auth "node test_emulator_rules.js"
