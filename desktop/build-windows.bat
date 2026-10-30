@echo off
rem Reaktiv Control - Windows'da yig'ish: barcha testlar, MSI o'rnatuvchi va o'rnatuvchisiz (portable) dastur papkasi.
rem
rem   build-windows.bat 2.0.<N>      masalan: build-windows.bat 2.0.501
rem
rem Talab: Temurin JDK 17 (x64), JAVA_HOME yoki PATH orqali. WiX'ni Gradle o'zi yuklab oladi (birinchi marta internet kerak).
rem <N> har yig'ishda oldingisidan katta bo'lsin (0..65535): bir xil versiyali MSI o'rnatilgan dasturning ustiga
rem o'rnatilmaydi (Windows Installer xatosi 1638).
rem Izoh: cmd.exe .bat faylni UTF-8 da o'qimaydi - shuning uchun matnda oddiy ' belgisi.

setlocal EnableExtensions
cd /d "%~dp0"

set "VERSION=%~1"
if "%VERSION%"=="" goto usage
echo %VERSION%| findstr /r /x "[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*" >nul || goto usage
for /f "tokens=1-3 delims=." %%a in ("%VERSION%") do (
  if %%a GTR 255 goto usage
  if %%b GTR 255 goto usage
  if %%c GTR 65535 goto usage
)

echo Reaktiv Control %VERSION%: testlar, MSI va portable dastur yig'ilmoqda...
call gradlew.bat --console=plain test packageMsi createDistributable -PappVersion=%VERSION%
if errorlevel 1 goto failed

set "MSI="
for %%F in ("%~dp0build\compose\binaries\main\msi\*%VERSION%.msi") do set "MSI=%%~fF"
set "EXE=%~dp0build\compose\binaries\main\app\ReaktivControl\ReaktivControl.exe"
echo.
echo Tayyor - versiya %VERSION%
rem Qavssiz: papka yo'lida ")" bo'lsa ham (masalan, "Program Files (x86)") buzilmaydi.
if defined MSI echo   MSI o'rnatuvchi:  %MSI%
if not defined MSI echo   MSI o'rnatuvchi:  %~dp0build\compose\binaries\main\msi\
echo   Portable dastur:  %EXE%
echo   Testlar hisoboti: %~dp0build\reports\tests\test\index.html
echo.
echo Portable dastur uchun butun "%~dp0build\compose\binaries\main\app\ReaktivControl" papkasini nusxalang (faqat EXE emas).
echo Tekshirish: "%EXE%" --self-test "%TEMP%\reaktiv-selftest.txt"
endlocal
exit /b 0

:usage
echo Ishlatish: build-windows.bat 2.0.^<N^>    masalan: build-windows.bat 2.0.501
echo ^<N^> har yig'ishda oldingisidan katta bo'lsin (0..65535), aks holda yangi MSI eskisining ustiga o'rnatilmaydi (xato 1638).
endlocal
exit /b 2

:failed
echo.
echo Yig'ish muvaffaqiyatsiz tugadi. Testlar hisoboti: %~dp0build\reports\tests\test\index.html
endlocal
exit /b 1
