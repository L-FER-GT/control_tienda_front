# CI y distribución

Android CI ejecuta tests de dominio, datos (incluida persistencia offline) y ViewModels, y genera APK debug. Puede compilar sin credenciales; ese APK requiere recompilar con configuración para conectarse.

Configurar SUPABASE_URL y SUPABASE_ANON_KEY como variables del environment pruebas/produccion. GOOGLE_WEB_CLIENT_ID es opcional. El workflow de develop guarda APK como artefacto de GitHub Actions. No utiliza Firebase App Distribution.

Release conserva firma mediante ANDROID_KEYSTORE_* y publicación opcional de Google Play con PLAY_SERVICE_ACCOUNT_JSON y PLAY_PUBLISH_ENABLED. No colocar secretos de Supabase en el APK.
