# Entorno

Usar Android Studio compatible con el AGP declarado en gradle/libs.versions.toml, JDK 21 y SDK 37. Gradle Wrapper fija la versión.

Crear Supabase siguiendo la guía del backend. .env puede estar en este proyecto o en ../control_tienda_backend/.env. Compilar ./gradlew :app:assembleDebug. Sin variables la compilación de CI funciona, pero el login informa que falta configurar Supabase; no hay un backend ficticio.

Para Supabase CLI local usar http://10.0.2.2:54321 en el emulador Android y sus claves públicas locales. En un dispositivo usar una conexión HTTPS válida al proyecto alojado. No usar las antiguas propiedades firebase.*.
