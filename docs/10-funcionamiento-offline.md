# Sin conexión y sincronización

La caché y la cola residen en supabase_offline.db dentro del almacenamiento privado de Android. Cada fila está asociada a un UID. Las credenciales de sesión se guardan en preferencias privadas; el backup de Android está deshabilitado.

Las escrituras de tienda, catálogo, ventas, recepciones y perfiles se guardan como lotes locales, con ID UUID estable. La interfaz ve la versión optimista. WorkManager intenta enviarlos al recuperar conexión y las pantallas también sincronizan al observar datos. La confirmación del servidor actualiza la caché y elimina el lote. Ante una respuesta perdida se reenvía el mismo ID: PostgreSQL reconoce el recibo y no repite stock ni correlativos.

Un rechazo definitivo (permisos o validación) elimina la versión optimista de ese lote y muestra un aviso. Los fallos de red o servidor conservan la cola. Los lotes de otra cuenta no se envían con la sesión actual. Al cerrar sesión los pendientes se conservan para cuando la misma cuenta vuelva a entrar.

Las cargas de archivos llevan el UID de origen. Primero se sincronizan documentos/membresías y luego se suben al bucket privado media. La copia local se conserva hasta confirmar la carga. Los archivos reemplazados se eliminan mediante la tarea de limpieza del backend.

La primera autenticación, aceptar invitaciones, eliminar cuenta y opciones maestras requieren conexión. Los reportes offline solo contienen datos previamente descargados; no implican que esté disponible todo el historial. Sin conexión no se detectan revocaciones nuevas de permisos.

Entre dispositivos se consulta cada 15 segundos mientras la pantalla observa datos. Si no cambió el resultado se recibe solo el etag. No se garantiza actualización instantánea, ni notificaciones push con la app cerrada.
