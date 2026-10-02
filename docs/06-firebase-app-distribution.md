# 6. Firebase App Distribution (instalación interna)

Mientras la app no esté en Google Play, los celulares la instalan y actualizan con
**Firebase App Distribution**.

## Preparar (una vez)

1. Firebase Console → **Lanzamiento y supervisión → App Distribution** → **Comenzar** (elige la app `com.lfergt.controltienda`).
2. Pestaña **Verificadores y grupos** → crea los grupos con estos alias exactos:
   - `testers` — reciben cada build de `develop`.
   - `produccion` — reciben cada build de `master` (los usuarios reales de la empresa).
3. Agrega los correos de cada persona al grupo correspondiente.

## Cuenta de servicio para subir builds

1. [Google Cloud Console](https://console.cloud.google.com) → selecciona el proyecto → **IAM y administración → Cuentas de servicio** → **Crear cuenta de servicio**.
2. Nombre: `github-app-distribution`. Rol: **Firebase App Distribution Admin**.
3. En la cuenta creada → **Claves → Agregar clave → JSON**. Se descarga un archivo.
4. Copia **todo el contenido** del JSON en el secreto `FIREBASE_SERVICE_ACCOUNT_JSON` de los
   environments `pruebas` y `produccion` de GitHub. Luego borra el archivo descargado.

## Cómo instalan los usuarios

1. Reciben un correo de Firebase App Distribution → **Comenzar**.
2. Instalan la app **Firebase App Tester** (el enlace viene en el correo) e inician sesión con ese correo.
3. Desde App Tester descargan Control Tienda. Cuando sale una versión nueva, reciben una notificación para actualizar.

> Android pedirá permitir "instalar apps de fuentes desconocidas" para App Tester. Es normal.

## Subir manualmente (sin CI)

```bash
# con keystore.properties y app/google-services.json reales en tu PC
set FIREBASE_SERVICE_ACCOUNT_PATH=C:\ruta\firebase-sa.json
gradlew.bat :app:assembleRelease :app:appDistributionUploadRelease
```
