**Mejoras UX/UI aprobadas — Control Tienda — 6 de octubre de 2026**

**Plan de implementación y registro de continuidad (trabajo autorizado)**

Origen: segunda revisión UX/UI (después de v1.1.0). El usuario aprobó los puntos 1 (modificado), 3–15, 17, 19 y 20, y todos los textos simplificados. Rechazó los puntos 2 (el carrito queda igual), 16 (proveedores), 18 (lista de tiendas), los opcionales (vuelto, compartir lista) y todo lo demás. Al terminar: subir versión (v1.2.0), desplegar en master y dejar develop sincronizado.

**Revisión del backend:** no requiere cambios. Los textos que genera (invitaciones y respuestas) no usan el vocabulario que cambia, y no se modifica ningún contrato: búsqueda de perfiles, permisos, membresías, ventas, compras ni reportes. Todo el trabajo es de interfaz en el frontend, más dos textos de dominio (nombres de reportes y columnas).

**Decisiones de interpretación**

- Punto 1: en «Nueva venta» el botón + se reemplaza por tres botones con íconos siempre visibles: código de barras, QR y mano (agregar a mano). Buscar por nombre se mantiene como campo fijo arriba, porque vender un producto sin código por su nombre es un requisito previo (hallazgo 1 del documento 13). En «Productos» (gestión) se aplica igual: barras, QR y mano (nuevo producto).
- Punto 4: «Productos» abre la gestión si hay permiso para editar productos y la vista de solo lectura si no. «Categorías» deja de ser un acceso del inicio y pasa a una acción dentro de «Productos» (solo con permiso de categorías). La etiqueta del acceso de miembros sigue siendo «Empleados y clientes».
- Convención de campos: «*» marca los obligatorios y se elimina «(opcional)».

| Paso | Trabajo (puntos) | Estado |
|---|---|---|
| A | Base común: plurales (8), fechas cercanas en 12 h (19), indicador de sincronización solo cuando hace falta (5), filtros con chips (6), textos simplificados, vocabulario (3) | Hecho; probado (`FormatsTest`, `SyncIndicatorTest`) |
| B | Ventas: + reemplazado por barras/QR/mano y búsqueda fija (1), escáner (11), agregar a mano (12), Mis ventas (9), detalle de venta (10) | Hecho; compilado; número de venta probado; revisión visual con sesión pendiente |
| C | Inicio de tienda agrupado y «Productos» único (4), catálogo con chips y lista por defecto (6), categorías (14), editor de producto (13) | Hecho; probado (`LandingSectionsTest`, filtro de stock) |
| D | Compras (15), reportes automáticos (7), empleados (17), títulos (20), configuración y stock bajo | Hecho; probado (`ReportDetailViewModelTest`, `InviteSearchTest`, diferencia de factura) |
| E | Pruebas nuevas y ajustadas, compilación, release local, revisión en emulador, commits, v1.2.0 y despliegue | Hecho: v1.2.0 publicada (CI de develop y Release Android exitosos) |

**Pruebas previstas:** plurales, fechas cercanas, visibilidad del indicador de sincronización, secciones del inicio de tienda, invitación con un solo campo (código o nombre), reporte generado automáticamente y regenerado al cambiar el periodo, diferencia entre factura y productos, número de venta sin ceros. Se ajusta `ReportGeneratorTest` por el texto «Productos sin costo registrado».

**Registro de ejecución**

- Inicio: v1.1.0 publicada; develop = master = `af7e3a4`; árbol limpio.
- A, B y C editados y compilando (`:app:compileDebugKotlin`). Nuevos: `ui/common/Formats.kt` (formatTime/formatDay/formatWhen/formatShortDate/plural), `syncIndicator`/`syncLabel` en Scaffolds, `AddActions` y `SearchLauncher` en Forms (se eliminó `ExpandableFab`), `CategoryFilterChip` y `StockFilter`, `landingSections`, `ProductValidator.FIELD_STOCK`. Siguiente: paso D.
- D hecho: compras con botón fijo, aviso si la factura no coincide y ayuda arriba; reportes que se generan solos (sin «Ver reporte») y totales una sola vez; empleados con menú ⋮ («Permisos», «Quitar acceso»/«Dar acceso») e invitación con un solo campo; títulos de editores y de «Stock bajo»; «Zona de peligro» retirada; «Administrador» en lugar de «Dueño».
- Pruebas: `:domain:test :data:testDebugUnitTest :app:testDebugUnitTest` → app 53 (antes 34), data 14, domain 36, sin fallos. `ReportGeneratorTest` ajustado a «Productos sin costo registrado».
- Limitación de pruebas: en JVM `toRoute` no decodifica el segundo argumento de la ruta. El VM de reportes ahora usa «Ventas por periodo» si el tipo no es válido (en producción las rutas siempre llevan un nombre válido), y la prueba cubre la generación automática con ese tipo.
- Release local `VERSION_NAME=1.2.0` (R8) correcta; instalada sobre 1.1.0 en `ct_test`, arranca sin fallos. Sin sesión en el emulador: no se revisaron las pantallas internas en dispositivo.
- Publicación: commits `90d039c` y `0e0c7ba` en develop, merge `e47a54c` en master, develop sincronizado y tag anotado `v1.2.0`. CI de develop y «Release Android» exitosos; APK `control-tienda-v1.2.0.apk` en GitHub Releases. Pendiente: recorrer las pantallas con una cuenta real en el teléfono.

**Para continuar tras una interrupción**

Leer esta tabla y el registro, revisar `git diff`, compilar con `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:GRADLE_USER_HOME = 'C:\Users\GTLui\.gradle'; .\gradlew.bat --offline :app:compileDebugKotlin --console=plain` y seguir con el primer paso no terminado. Correr `:domain:test :data:testDebugUnitTest :app:testDebugUnitTest` antes de cada commit. Para instalar un release local sobre el del emulador, definir `$env:VERSION_NAME` con la versión.
