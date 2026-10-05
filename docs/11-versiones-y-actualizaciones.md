# 11. Versiones y actualizaciones (GitHub Releases)

Cada versión de la app es un tag `vX.Y.Z`. Al subirlo, el workflow **Release Android** prueba, compila el APK
firmado y lo publica en [Releases](https://github.com/L-FER-GT/control_tienda_front/releases). La app
instalada consulta el último release al abrirse (y en **Configuración → Buscar actualizaciones**); si es más
nuevo, ofrece descargarlo e instalarlo.

## Publicar una versión

Ramas: `feature/*` → `develop` (pruebas: genera el APK debug `apk-demo`) → `master` (producción). Solo se
publican versiones desde `master`; el workflow rechaza un tag que no apunte a un commit de `master`.

```bash
# 1. Llevar a master lo probado en develop (o con un pull request develop → master en GitHub)
git checkout master && git pull
git merge --no-ff develop -m "Release v1.0.1"
git push origin master

# 2. Crear la versión sobre master
git tag -a v1.0.1 -m "Corrige el total de ventas y agrega el reporte por categoría"
git push origin v1.0.1
git checkout develop
```

- El mensaje del tag son las **notas** que verán los usuarios en el aviso de actualización.
- `X.Y.Z` con `Y` y `Z` entre 0 y 99. El `versionCode` de Android es `X*10000 + Y*100 + Z`, así que cada
  tag debe ser mayor que el anterior: después de `v1.0.1` puede ir `v1.0.2`, `v1.1.0` o `v2.0.0`.
- El APK queda en el release como `control-tienda-vX.Y.Z.apk`. El enlace para descargar siempre la última
  versión es `https://github.com/L-FER-GT/control_tienda_front/releases/latest`.
- Si el workflow falla, borrar el tag (`git push --delete origin v1.0.1` y `git tag -d v1.0.1`), corregir y repetir.

## Cómo se actualiza la app

1. Al abrir la app (solo el build release; debug no busca actualizaciones) consulta
   `api.github.com/repos/L-FER-GT/control_tienda_front/releases/latest`. Sin conexión no muestra nada.
2. Si el tag es mayor que la versión instalada, muestra **Nueva versión X.Y.Z** con las notas.
3. **Descargar** guarda el APK en la caché de la app y comprueba su SHA-256 con el que publica GitHub.
4. **Instalar** abre el instalador de Android. La primera vez Android pide permitir *Instalar apps
   desconocidas* para Control Tienda; luego se vuelve y se pulsa Instalar otra vez.
5. Android solo acepta la actualización si está firmada con **la misma llave** que la versión instalada
   ([firma](04-firma-de-la-app.md)). La sesión y los datos locales se conservan.

Un APK compilado en local (debug) es otra app (`com.lfergt.controltienda.debug`) y no se actualiza desde GitHub.

## Credenciales: qué es público y qué no

| Dato | Dónde vive | ¿Público? |
|---|---|---|
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | Secrets de GitHub → dentro del APK | Viajan en el APK, como en toda app móvil; la seguridad la dan RLS y las RPC del backend |
| `SUPABASE_SERVICE_ROLE_KEY`, contraseña de la base | Solo `.env` del backend | Nunca en el APK ni en este repositorio |
| Llave de firma (`.jks`) y sus contraseñas | `C:\dev\control_tienda\firma`, `keystore.properties` y secrets de GitHub | Nunca en el repositorio |

El repositorio es público: el código se ve, pero `.env`, `keystore.properties` y `*.jks` están en `.gitignore`.

## Google Play

Google Play no permite que una app se actualice por fuera de Play ni el permiso `REQUEST_INSTALL_PACKAGES`
para este uso. Si algún día se publica en Play, esa variante debe compilarse sin el actualizador
(ver [Google Play](07-google-play.md)).
