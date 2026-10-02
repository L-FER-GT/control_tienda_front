# 3. Conectar la app con tu proyecto de Firebase

Primero crea y configura el proyecto siguiendo `docs/02-crear-proyecto-firebase.md` del
[repo del backend](https://github.com/L-FER-GT/control_tienda_backend). Luego:

## 1. Registrar las apps Android

En [Firebase Console](https://console.firebase.google.com) → tu proyecto → ⚙️ **Configuración del
proyecto** → **General** → **Tus apps** → **Agregar app** → Android. Registra **dos** apps:

| Nombre | Nombre del paquete |
|---|---|
| Control Tienda | `com.lfergt.controltienda` |
| Control Tienda (debug) | `com.lfergt.controltienda.debug` |

## 2. Huellas SHA (necesarias para "Continuar con Google")

En cada app registrada, agrega las huellas **SHA-1** y **SHA-256**:

```bash
# Debug (keystore de Android Studio)
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Release (tu keystore; ver 04-firma-de-la-app.md)
keytool -list -v -keystore control-tienda-release.jks -alias control-tienda
```

> Si algún día publicas en Google Play con *Play App Signing*, agrega también la SHA-1 que muestra
> Play Console → Integridad de la app → Firma de apps.

## 3. Proveedores de inicio de sesión

**Authentication → Método de inicio de sesión** → habilita:

- **Correo electrónico/contraseña**
- **Google** (elige el correo de asistencia del proyecto)

## 4. Descargar `google-services.json`

Después de agregar las huellas, descarga el `google-services.json` (contiene ambas apps) y guárdalo en:

```
app/google-services.json
```

Este archivo **no se sube a GitHub** (está en `.gitignore`). Para CI se guarda como secreto
(ver [08-credenciales-y-secretos.md](08-credenciales-y-secretos.md)).

Al compilar, el proyecto detecta que `project_id` ya no empieza con `demo-` y la app usa la nube
en lugar del emulador. El ID de cliente web de Google (`default_web_client_id`) se genera
automáticamente desde este archivo y lo usa Credential Manager.

## 5. Notificaciones push

No requieren configuración extra: FCM viene incluido. La app pide permiso de notificaciones
(Android 13+) al abrir la lista de tiendas y registra el token del dispositivo en
`users/{uid}/devices`.

## Comprobación rápida

1. Instala la variante `debug` en un celular.
2. Crea una cuenta con correo → en Firestore debe aparecer `users/{uid}` con un `code` de 10 dígitos.
3. Crea una tienda con foto → en Storage aparece `stores/{id}/store/<uuid>.jpg`.
