# CI y distribución

Android CI ejecuta tests de dominio, datos (incluida persistencia offline) y ViewModels, y genera APK debug. Puede compilar sin credenciales; ese APK requiere recompilar con configuración para conectarse.

Configurar en GitHub (Settings → Secrets and variables → Actions → **Secrets**) SUPABASE_URL y SUPABASE_ANON_KEY, y ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD y ANDROID_KEY_ALIAS de la [firma](04-firma-de-la-app.md). GOOGLE_WEB_CLIENT_ID es opcional y va en **Variables**. El workflow de develop guarda APK debug como artefacto de GitHub Actions.

Release Android se ejecuta al subir un tag vX.Y.Z: prueba, compila el APK firmado y lo publica en GitHub Releases, de donde la app instalada toma las actualizaciones ([versiones](11-versiones-y-actualizaciones.md)). No colocar secretos de Supabase en el APK: solo la URL y la clave anon/publishable.
