# Sin conexión y sincronización

La caché y la cola residen en supabase_offline.db dentro del almacenamiento privado de Android. Cada fila está asociada a un UID. Las credenciales de sesión se guardan en preferencias privadas; el backup de Android está deshabilitado.

Las escrituras de tienda, catálogo, ventas, recepciones y perfiles se guardan como lotes locales, con ID UUID estable. La interfaz ve la versión optimista. WorkManager intenta enviarlos al recuperar conexión y las pantallas también sincronizan al observar datos. La confirmación del servidor actualiza la caché y elimina el lote. Ante una respuesta perdida se reenvía el mismo ID: PostgreSQL reconoce el recibo y no repite stock ni correlativos.

Un rechazo definitivo (permisos o validación) elimina la versión optimista de ese lote y muestra un aviso. Los fallos de red o servidor conservan la cola. Los lotes de otra cuenta no se envían con la sesión actual. Al cerrar sesión los pendientes se conservan para cuando la misma cuenta vuelva a entrar.

Las cargas de archivos llevan el UID de origen. Primero se sincronizan documentos/membresías y luego se suben al bucket privado media. La copia local se conserva hasta confirmar la carga. Los archivos reemplazados se eliminan mediante la tarea de limpieza del backend.

La primera autenticación, aceptar invitaciones, eliminar cuenta y opciones maestras requieren conexión. Los reportes offline solo contienen datos previamente descargados; no implican que esté disponible todo el historial. Sin conexión no se detectan revocaciones nuevas de permisos.

Entre dispositivos se consulta cada 15 segundos mientras la pantalla observa datos. Si no cambió el resultado se recibe solo el etag. No se garantiza actualización instantánea, ni notificaciones push con la app cerrada.

## Carga instantánea desde la base local

Las pantallas muestran primero lo que ya está en el teléfono y actualizan desde la nube en segundo plano:

- Cada consulta lee de SQLite solo sus documentos (por la clave `uid + ruta`), no toda la caché del usuario. Una tienda con muchas ventas ya no hace lenta la lista de productos.
- Cuando llega un cambio, solo vuelven a leer la base las consultas a las que les afecta esa ruta.
- Si varias pantallas observan la misma consulta (por ejemplo la tienda en la cabecera y en la lista), comparten una sola lectura y un solo sondeo. Al volver a una pantalla en menos de un minuto se ve al instante el último dato.
- Hasta tres consultas se actualizan desde la nube a la vez, así la pantalla visible no espera en fila detrás de las demás.
- El etag de cada consulta se guarda en la base (`poll_state`). Al reabrir la app, si nada cambió, el servidor responde solo el etag y no se vuelve a descargar el catálogo.
- Una consulta que nunca se sincronizó y no tiene datos locales espera la nube como máximo 3 segundos; después se muestra lo que haya y se completa al llegar.
