# Revisión de escáner y Supabase — 5 de octubre de 2026

Cambios preparados sobre la versión ya publicada, conservando la configuración y el sistema de actualizaciones.

## Causa del cierre al escanear (confirmada en emulador)

Solo fallaba el APK **release** (el publicado en GitHub); el debug no se minifica. ML Kit crea sus
`ComponentRegistrar` por reflexión con el constructor vacío, y R8 en modo completo lo eliminaba
(`NoSuchMethodException: …BarcodeRegistrar.<init>`). Sin registrars, `BarcodeScanning.getClient()`
lanzaba `NullPointerException`: en v1.0.0 la app se cerraba al conceder la cámara; con los cambios de
abajo ya no se cerraba, pero mostraba "No se pudo abrir la cámara". La regla de `app/proguard-rules.pro`
conserva esos constructores. Verificado con el APK release: barras y QR abren la cámara, se cierran y
reabren sin errores, y el código ingresado llega al campo correcto. Para revisar un cierre de un APK
publicado, desofuscar el logcat con el `mapping.txt` del artefacto `mapping-vX.Y.Z` del workflow.

## Correcciones

- Gestionar productos y recepción tienen acciones separadas para QR y código de barras. El editor de producto y ventas ya tenían ambas opciones.
- El escáner espera la inicialización de CameraX, habilita únicamente análisis de imágenes, maneja errores de apertura y libera la cámara al salir. Usa una vista compatible con diálogos y un executor que permanece disponible mientras ML Kit termina sus callbacks. Se basa en la [integración recomendada de CameraX y ML Kit](https://developer.android.com/media/camera/camerax/mlkitanalyzer).
- Al denegar el permiso se puede ir a Ajustes y volver. El diálogo ofrece entrada manual incluso si falla la cámara. Si no hay cámara trasera se intenta con la frontal; la linterna solo se habilita cuando existe flash.
- El recuadro sirve de guía: se acepta también un único código detectado fuera de él. Se incluyen Code 93 y Codabar entre los formatos de barras.
- Recepción conserva el tipo detectado: un QR numérico sigue siendo QR y una barra alfanumérica conserva su contenido al crear el producto.
- Un producto puede tener el mismo contenido en QR y barras. Se sigue impidiendo que ese código pertenezca a otro producto mediante la validación de la app. La búsqueda admite fragmentos y no distingue mayúsculas en códigos.
- Tomar fotos solicita el permiso de cámara y maneja la ausencia de una app para capturar o elegir imágenes.
- Un fallo de autenticación al cargar imágenes se entrega como error de imagen, evitando una excepción fatal en el hilo de OkHttp.
- Consultar una categoría, un rango de fechas o un resultado limitado conserva los demás documentos del caché. Las filas que desaparecen de un filtro se vuelven a consultar para distinguir un cambio de datos de una eliminación o pérdida de permiso. Los errores transitorios del servidor permiten seguir leyendo datos ya guardados.
- La migración nueva del backend `20261005000004_member_profile_photos.sql` permite crear la membresía del dueño y modificar permisos o actividad de miembros que tienen avatar. Conserva las comprobaciones de autorización.

## Validación y publicación

Las pruebas automatizadas cubren QR numéricos, barras alfanuméricas, caché filtrado y limitado, errores transitorios, sesión cerrada al cargar imágenes y operaciones de miembros con avatar. El backend mantiene pruebas de ventas, stock, permisos e idempotencia.

Resultado local: 61 pruebas Android y 11 pruebas del backend sin fallos; APK debug y release compilados; Android Lint sin errores bloqueantes (persisten avisos de estilo, recursos y versiones de dependencias). La comprobación contra Supabase publicado pasó para Auth, perfil, RLS y `ct_poll`. URL, clave pública y bucket de ambos `.env` son compatibles.

Para llevar los cambios a la versión instalada:

1. Integrar el backend siguiendo su flujo `develop → master`. Supabase debe aplicar la migración **nueva**; no editar las tres migraciones anteriores.
2. Integrar el frontend y publicar un tag superior al instalado, siguiendo [versiones y actualizaciones](11-versiones-y-actualizaciones.md).
3. Instalar la actualización y comprobar en un teléfono: abrir/cerrar repetidamente el escáner, alternar QR y barras, denegar/otorgar permiso, volver desde Ajustes, usar linterna, registrar un código desconocido y crear un producto desde recepción.

La revisión local no publica automáticamente los cambios. Compilar el APK no demuestra que la cámara funcione en todos los dispositivos. Sin un teléfono conectado ni el registro del cierre original no se puede confirmar su causa exacta. Si se repite, registrar modelo, versión de Android, pantalla y excepción de `AndroidRuntime` en Logcat.

## Límites que continúan

- Los permisos por rol se mantienen: los trabajadores necesitan permisos para administrar productos, miembros o recepciones; un cliente no tiene permisos administrativos. Se corrigen rechazos indebidos, sin dar acceso administrativo a todas las cuentas.
- Las cuotas y pausas del plan Supabase Free siguen aplicando. Las operaciones guardadas sin conexión se sincronizan al reconectar; registro/inicio de sesión y aceptación de invitaciones requieren servidor.
- El acceso con Google requiere configurar `GOOGLE_WEB_CLIENT_ID` y el proveedor OAuth en Supabase. El inicio por correo está disponible con la configuración actual.
- La consulta pública de Auth confirmó que el registro está habilitado, Google está deshabilitado y se exige confirmar el correo. No permite verificar si hay SMTP propio configurado. Si se usa el servicio de correo incorporado, este solo envía a miembros de la organización y tiene un límite de dos mensajes por hora, según la [documentación de Supabase](https://supabase.com/docs/guides/auth/auth-smtp). Para una demo cerrada sin SMTP, se puede desactivar Confirm email desde el panel; si se conserva la confirmación, se necesita configurar SMTP. La recuperación de contraseña seguirá necesitando correo. No se cambió esta configuración remota.
- No se ha realizado una prueba manual con tres dispositivos a la vez ni una prueba de cámara física.
