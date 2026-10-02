# 8. Credenciales y secretos

Ningún secreto se guarda en el repositorio (es público). Esta tabla dice **qué es**, **de dónde se
obtiene** y **dónde se guarda** cada credencial de la app.

| Credencial | Qué es | Dónde se obtiene | Local | GitHub |
|---|---|---|---|---|
| `google-services.json` | Configuración del proyecto Firebase para Android | Firebase Console → Configuración del proyecto → Tus apps → descargar ([guía](03-configurar-firebase.md)) | `app/google-services.json` | Secreto `GOOGLE_SERVICES_JSON_BASE64` |
| Keystore de release | Llave con la que se firma la app | La creas con `keytool` ([guía](04-firma-de-la-app.md)) | `keystore.properties` + `.jks` fuera del repo | `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |
| Cuenta de servicio de App Distribution | Permite que CI suba APKs a App Distribution | Google Cloud → IAM → Cuentas de servicio → rol *Firebase App Distribution Admin* → clave JSON ([guía](06-firebase-app-distribution.md)) | Solo si subes a mano: `FIREBASE_SERVICE_ACCOUNT_PATH` | `FIREBASE_SERVICE_ACCOUNT_JSON` (contenido del JSON) |
| Cuenta de servicio de Google Play | Permite publicar en Play (opcional) | Google Cloud + invitación en Play Console ([guía](07-google-play.md)) | `play-service-account.json` (ignorado) | `PLAY_SERVICE_ACCOUNT_JSON` |
| SHA-1 / SHA-256 | Huellas de las llaves, para el login con Google | `keytool -list -v …` | — | — (se registran en Firebase Console) |

## Convertir a base64

```powershell
# PowerShell: copia al portapapeles el google-services.json en base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json")) | Set-Clipboard
```

```bash
base64 -w0 app/google-services.json
```

## Dónde se cargan en GitHub

**Settings → Environments → `pruebas` / `produccion` → Environment secrets → Add secret.**

## ¿Y las credenciales del superadmin?

El usuario y la contraseña maestros viven en el `.env` del **backend** (nunca en la app). Ver
`docs/04-superadmin.md` del repo del backend.

## Si se filtra una credencial

| Credencial | Qué hacer |
|---|---|
| Clave de cuenta de servicio | Google Cloud → Cuentas de servicio → Claves → **borrar** la clave filtrada y crear otra |
| Keystore | No se puede revocar: si no estaba publicada en Play, genera otra y reinstala; con Play App Signing, solicita *restablecer la llave de subida* |
| `google-services.json` | No es secreto por sí mismo (la seguridad está en las reglas), pero puedes restringir la API key en Google Cloud → APIs y servicios → Credenciales |
