# Distribuir la demo

Configurar Supabase, compilar :app:assembleDebug y compartir app/build/outputs/apk/debug/app-debug.apk por un canal privado o descargar el artefacto apk-demo de Actions. Para una distribución firmada usar el workflow release y las claves Android.

No se necesita Firebase App Distribution. Los usuarios deben permitir instalación del APK desde el canal elegido. Las variantes debug y release son aplicaciones distintas y no comparten sesión/caché.
