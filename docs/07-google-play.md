# 7. Google Play (preparado, aún no activo)

La app es de uso interno por ahora, pero todo está listo para publicarla cuando se decida.
El workflow de `master` ya genera el **AAB** firmado y tiene el paso de publicación desactivado
hasta que crees la variable `PLAY_PUBLISH_ENABLED=true`.

## Requisitos de Play que ya cumple la app

- ✅ `targetSdk 37`
- ✅ Opción para **eliminar la cuenta** desde la app (Configuración → Eliminar mi cuenta)
- ✅ Permisos justificados: cámara (escáner y fotos) y notificaciones
- ✅ App Bundle (AAB) firmado y minificado con R8

## Pasos para activarlo

1. Crea una cuenta de desarrollador en [Play Console](https://play.google.com/console) (pago único de USD 25).
2. **Crear app** → nombre "Control Tienda", idioma español, App, gratuita.
3. Completa la ficha: política de privacidad (URL), clasificación de contenido, público objetivo,
   seguridad de los datos (correo, nombre, fotos; datos cifrados en tránsito; el usuario puede
   eliminar su cuenta).
4. **Primera subida manual** (Play exige que la primera versión se suba a mano): descarga el AAB
   del artefacto `release-*` del último workflow de `master` y súbelo a **Pruebas → Prueba interna**.
5. Acepta **Play App Signing** y agrega en Firebase la SHA-1 de la llave de firma de Play
   (Integridad de la app → Firma de apps), si no, "Continuar con Google" fallará en la versión de Play.
6. Cuenta de servicio para publicar:
   - Google Cloud Console → crea la cuenta de servicio `github-play-publisher` y una clave JSON.
   - Play Console → **Usuarios y permisos → Invitar usuarios** → el correo de esa cuenta de
     servicio, con permiso para la app: *Publicar en pistas de prueba* (y *Lanzar a producción* si quieres).
   - Guarda el JSON en el secreto `PLAY_SERVICE_ACCOUNT_JSON` del environment `produccion`.
7. Crea la variable `PLAY_PUBLISH_ENABLED` con valor `true`.

Desde entonces, cada push a `master` publica el AAB en la pista **interna** con
[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher). Para pasar a
producción, promueve la versión desde Play Console o cambia `track` en `app/build.gradle.kts`.
