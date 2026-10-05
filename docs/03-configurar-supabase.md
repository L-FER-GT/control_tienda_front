# Configurar Supabase

Aplicar primero las migraciones del backend. En .env: SUPABASE_URL, SUPABASE_ANON_KEY (pública) y SUPABASE_STORAGE_BUCKET=media. Si los repositorios son hermanos, se lee automáticamente el .env del backend. El .env del frontend puede sobrescribirlo.

Gradle solo expone esos valores públicos; nunca SUPABASE_SERVICE_ROLE_KEY ni SUPABASE_DB_URL. Recompilar después de cambiarlos.

Auth: habilitar correo/contraseña. Para una demo cerrada sin SMTP, desactivar Confirm email en el panel. Para usuarios con confirmación o recuperación por correo, configurar SMTP propio. Añadir controltienda://auth/callback en Redirect URLs; el enlace de recuperación abre el formulario de nueva contraseña en la app.

Google es opcional: habilitar el proveedor en Supabase, configurar los clientes OAuth y firmas Android en Google Cloud y copiar el client ID web a GOOGLE_WEB_CLIENT_ID. Sin esta variable usar correo/contraseña.

Prueba: iniciar sesión con el administrador creado por npm run superadmin:seed; crear tienda; registrar trabajador y cliente; invitarlos mediante su código; aceptar; comprobar permisos y una venta offline.
