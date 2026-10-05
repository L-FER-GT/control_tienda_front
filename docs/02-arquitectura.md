# Arquitectura

:domain conserva modelos, puertos y reglas Kotlin. :app contiene Compose y ViewModels. :data implementa los puertos mediante Auth/REST/Storage de Supabase, OkHttp, SQLiteOpenHelper y WorkManager.

data/supabase/SupabaseClient.kt gestiona login, refresh de sesión y llamadas RPC. DocumentStore.kt mantiene caché confirmada y lotes pendientes separados por UID. Los repositorios mantienen los modelos existentes; las fechas se transportan como milisegundos y las operaciones de inventario se ejecutan en PostgreSQL.

El servidor es autoritativo para permisos, números y stock. Los flows observan la caché local y consultan ct_poll cada 15 segundos mientras tienen suscriptores. El etag evita transmitir filas sin cambios. No se usa un SDK Firebase ni Supabase Realtime.

Las imágenes se cargan desde copias locales pendientes o desde Storage autenticado con el JWT de Supabase. Se mantiene Open Food Facts y la generación de PDF/Excel en Android.
