@echo off
title Generador de Android App Bundle (AAB) para Google Play
echo =======================================================================
echo           🍌 GENERANDO AAB DE PRODUCCION PARA +PANORAMAS 🍌
echo =======================================================================
echo.
echo Recuerda haber incrementado el versionCode y versionName en:
echo - app/build.gradle.kts (versionCode actual: 24, versionName actual: 1.5.2)
echo.
echo El proceso limpiara cache y compilara el bundle de produccion firmado.
echo Presiona una tecla para comenzar el build...
pause > nul

echo.
echo [1/2] Limpiando caches y builds previos...
call gradlew.bat clean
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] La limpieza de Gradle fallo.
    echo Revisa los errores anteriores.
    goto end
)

echo.
echo [2/2] Compilando e indexando bundle de produccion firmado (:app:bundleProdRelease)...
call gradlew.bat :app:bundleProdRelease
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] La compilacion del bundle fallo.
    echo Revisa el log de errores arriba.
    goto end
)

echo.
echo =======================================================================
echo                   🚀 BUNDLE GENERADO EXITOSAMENTE 🚀
echo =======================================================================
echo.
echo El archivo .aab se encuentra en la siguiente ruta:
echo - app\build\outputs\bundle\prodRelease\app-prod-release.aab
echo.
echo Abriendo la carpeta contenedora para que lo subas a Google Play Console...
explorer app\build\outputs\bundle\prodRelease\

:end
echo.
echo Presiona cualquier tecla para salir...
pause > nul
