# 1. Entorno de desarrollo

Guía para compilar y ejecutar la app en tu computadora.

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| Android Studio | 2025.3.3 (Panda) o superior | Incluye el JDK 21 (JBR) que usa Gradle |
| Android SDK | Platform 37 | Gradle lo descarga solo si falta (licencias aceptadas) |
| JDK | 21 | El de Android Studio sirve: `C:\Program Files\Android\Android Studio\jbr` |
| Git | cualquiera reciente | |
| Backend | [control_tienda_backend](https://github.com/L-FER-GT/control_tienda_backend) | Para usar el emulador de Firebase en local |

Versiones principales del proyecto (en [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)):
AGP 9.2.1 · Gradle 9.4.1 · Kotlin 2.3.21 · KSP 2.3.12 · Compose BOM 2026.09.00 · Hilt 2.60.1 · Firebase BoM 34.19.0.

## Clonar y abrir

```bash
git clone https://github.com/L-FER-GT/control_tienda_front.git
cd control_tienda_front
git checkout develop
```

Abre la carpeta en Android Studio y espera a que termine el *Gradle Sync*.

> **Windows:** no trabajes dentro de OneDrive: la sincronización bloquea archivos de `build/`.
> Usa una carpeta local, por ejemplo `C:\dev\control_tienda\`. Si ves errores del tipo
> *"El proceso no tiene acceso al archivo porque está siendo utilizado por otro proceso"*,
> agrega `C:\dev` a las exclusiones de Windows Defender (Seguridad de Windows → Protección
> contra virus y amenazas → Exclusiones). Gradle se recupera solo, pero compila más lento.

## Modos de ejecución

La app decide a qué Firebase conectarse según el archivo `app/google-services.json`:

| Situación | Qué pasa |
|---|---|
| No existe `app/google-services.json` | Gradle copia [`config/google-services.demo.json`](../config/google-services.demo.json) (proyecto `demo-control-tienda`) y la app usa el **Firebase Emulator Suite**. No necesitas cuenta de Firebase. |
| Pusiste el `google-services.json` real | La app usa tu proyecto real de Firebase (ver [03-configurar-firebase.md](03-configurar-firebase.md)). |

Puedes forzar el comportamiento en `local.properties` (no se versiona):

```properties
# true = siempre emulador, false = siempre nube
firebase.useEmulators=true
# IP de la PC con los emuladores. 10.0.2.2 = tu PC vista desde el emulador de Android.
# Para un celular físico usa la IP de tu PC en la red Wi-Fi (p. ej. 192.168.1.20).
firebase.emulatorHost=10.0.2.2
```

## Ejecutar con el emulador de Firebase

1. En el repo del backend: `npm install` y luego `npm run emulators` (ver su `docs/05-emuladores-y-pruebas.md`).
2. Crea el superadmin de prueba: `npm run superadmin:seed:emulator`.
3. En Android Studio ejecuta la configuración **app** (variante `debug`) en un emulador o celular.
4. Inicia sesión o crea una cuenta. El panel del emulador está en <http://localhost:4000>.

> El inicio con Google no funciona con el proyecto demo (no hay cliente OAuth real). Usa correo y contraseña.

## Comandos útiles

```bash
./gradlew :domain:test              # pruebas del dominio (Kotlin puro, muy rápidas)
./gradlew :app:testDebugUnitTest    # pruebas de ViewModels (Turbine)
./gradlew :app:assembleDebug        # APK debug en app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # APK release (firmado con debug si no hay keystore)
```

En Windows usa `gradlew.bat`. Si la terminal no encuentra Java:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

## Variantes

| Variante | applicationId | Uso |
|---|---|---|
| `debug` | `com.lfergt.controltienda.debug` | Desarrollo; se puede instalar junto a la de producción |
| `release` | `com.lfergt.controltienda` | App Distribution y Google Play; minificada con R8 |
