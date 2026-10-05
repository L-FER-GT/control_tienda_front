# 4. Firma de la app (keystore)

Las versiones `release` deben firmarse siempre con **la misma** llave. Si la pierdes, no podrás
actualizar la app instalada en los celulares (habría que desinstalar) ni en Google Play.

La llave actual ya existe: `C:\dev\control_tienda\firma\control-tienda-release.jks` (alias `control-tienda`),
fuera de los dos repositorios, con sus contraseñas en `keystore.properties`. Haz una copia de ambos archivos
en un lugar seguro (gestor de contraseñas, USB cifrado); no se pueden recuperar.

## Crear la llave (solo si se empieza de cero)

```bash
keytool -genkeypair -v \
  -keystore control-tienda-release.jks \
  -alias control-tienda \
  -keyalg RSA -keysize 4096 -validity 10000
```

- Guarda el `.jks` y las contraseñas en un gestor de contraseñas y en una copia de seguridad.
- **Nunca** lo subas a GitHub (`*.jks` está en `.gitignore`).

## Firmar en tu computadora

Crea `keystore.properties` en la raíz del proyecto (ignorado por git):

```properties
storeFile=C:/ruta/segura/control-tienda-release.jks
storePassword=********
keyAlias=control-tienda
keyPassword=********
```

`./gradlew :app:assembleRelease` usará esa firma. Sin el archivo, el release se firma con la llave
de debug (sirve para probar, no para distribuir).

## Firmar en GitHub Actions

Convierte el keystore a base64 y guárdalo como secreto:

```powershell
# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("control-tienda-release.jks")) | Set-Clipboard
```

```bash
# Linux / macOS
base64 -w0 control-tienda-release.jks
```

| Secreto | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | El texto base64 del `.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `ANDROID_KEY_ALIAS` | `control-tienda` |
| `ANDROID_KEY_PASSWORD` | Contraseña de la llave |

El workflow lo decodifica en un archivo temporal y lo pasa a Gradle por variables de entorno
(ver [`app/build.gradle.kts`](../app/build.gradle.kts)).
