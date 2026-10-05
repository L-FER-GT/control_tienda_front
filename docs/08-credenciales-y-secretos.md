# Variables

Públicas: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_STORAGE_BUCKET=media, GOOGLE_WEB_CLIENT_ID opcional. Van dentro del APK; en GitHub se guardan como secrets para no exponerlas en el repositorio.

Prioridad: entorno, propiedades Gradle, local.properties, .env frontend, .env backend hermano. .env.example es la plantilla versionada. Nunca incorporar SUPABASE_SERVICE_ROLE_KEY, SUPABASE_DB_URL ni SUPERADMIN_PASSWORD a BuildConfig.

Versión: VERSION_NAME (X.Y.Z) la fija el tag del release; en local vale 0.0.0. UPDATE_REPO (por defecto L-FER-GT/control_tienda_front) es el repositorio cuyos releases ofrece el build release como actualización.

Firma: keystore.properties en local (ignorado por Git) o ANDROID_KEYSTORE_PATH/PASSWORD y ANDROID_KEY_ALIAS en CI (ANDROID_KEY_PASSWORD opcional, por defecto la del keystore). La llave vive fuera del repositorio; ver [firma](04-firma-de-la-app.md).
