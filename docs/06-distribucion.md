# Distribuir la demo

La distribución oficial es **GitHub Releases**: cada tag `vX.Y.Z` publica un APK firmado y la app avisa de
las versiones nuevas ([versiones y actualizaciones](11-versiones-y-actualizaciones.md)). Para instalarla por
primera vez, abrir en el celular https://github.com/L-FER-GT/control_tienda_front/releases/latest, descargar
el APK y permitir la instalación desde el navegador.

Para pruebas internas, el workflow de develop guarda un APK debug como artefacto `apk-demo` de Actions, o se
compila :app:assembleDebug y se comparte app/build/outputs/apk/debug/app-debug.apk. Las variantes debug y
release son aplicaciones distintas y no comparten sesión/caché; debug no busca actualizaciones.

No se necesita Firebase App Distribution.
