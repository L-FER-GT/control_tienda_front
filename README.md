# Control Tienda Android — Supabase

App Kotlin/Compose con dominio independiente, adaptadores Supabase y caché/cola SQLite offline. Requiere el backend hermano control_tienda_backend con sus migraciones aplicadas.

## Preparar

1. Crear Supabase Free y ejecutar las migraciones siguiendo el README del backend.
2. Completar SUPABASE_URL, SUPABASE_ANON_KEY y SUPABASE_STORAGE_BUCKET=media en el .env del backend hermano. También se puede copiar .env.example a .env en este proyecto.
3. Opcional: GOOGLE_WEB_CLIENT_ID para Google. El login por correo funciona sin él.
4. Compilar: ./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug (en Windows: gradlew.bat).

No se necesita google-services.json, Firebase ni un servidor adicional. El APK nunca debe contener service_role o la URI PostgreSQL; Gradle rechaza claves privilegiadas en SUPABASE_ANON_KEY.

[Configuración](docs/03-configurar-supabase.md) · [Arquitectura](docs/02-arquitectura.md) · [Offline](docs/10-funcionamiento-offline.md) · [CI](docs/05-ci-cd.md)

Notificaciones e invitaciones dentro de la app; no push cuando está cerrada. Actualización entre dispositivos mediante consultas condicionales cada 15 segundos mientras se observa la pantalla. La primera conexión y login requieren internet.
