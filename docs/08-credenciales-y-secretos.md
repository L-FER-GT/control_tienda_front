# Variables

Públicas: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_STORAGE_BUCKET=media, GOOGLE_WEB_CLIENT_ID opcional.

Prioridad: entorno, propiedades Gradle, local.properties, .env frontend, .env backend hermano. .env.example es la plantilla versionada. Nunca incorporar SUPABASE_SERVICE_ROLE_KEY, SUPABASE_DB_URL ni SUPERADMIN_PASSWORD a BuildConfig.

Firma y Play: mantener ANDROID_KEYSTORE_PATH/PASSWORD, ANDROID_KEY_ALIAS/PASSWORD y PLAY_SERVICE_ACCOUNT_PATH en el entorno o los archivos ignorados correspondientes.
