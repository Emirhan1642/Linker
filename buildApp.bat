@echo off
echo [1/2] Uygulama derleniyor ve yukleniyor...

:: 'call' kullanmazsan gradlew bittiginde bat dosyası kapanır ve adb satırına geçmez.
call gradlew installDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Yukleme basarili. Monkey testi baslatiliyor...
    echo [2/2] Monkey komutu calistiriliyor: com.linker.app
    
    :: Monkey testi baslatma
    adb shell monkey -p com.linker.app -v 1
) else (
    echo.
    echo [HATA] Derleme veya yukleme sirasinda bir sorun olustu.
)

pause