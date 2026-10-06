**Revisión UX/UI — Control Tienda — 6 de octubre de 2026**

**Plan de implementación y registro de continuidad (trabajo autorizado)**

El usuario autorizó implementar las mejoras y buscar/aplicar paletas azules. No requiere nueva aprobación para continuar las etapas. La auditoría original, más abajo, describe el estado anterior; sus números de línea quedarán desactualizados al editar. No marcar una etapa como verificada si solo se ha editado código.

| Paso | Trabajo y hallazgos cubiertos | Estado |
|---|---|---|
| 1 | Investigar paleta azul; definir colores claros/oscuros y contraste (27) | Implementado; contraste verificado por prueba (`ThemeContrastTest`) en ambos temas |
| 2 | Componentes compartidos: búsqueda, teclado, semántica, diálogos y tamaños (14, 19, 28–30, 32) | Implementado; búsqueda probada (`CatalogSearchTest`); TalkBack y fuente 200 % pendientes en dispositivo |
| 3 | Ventas: buscar productos, código escrito, cantidades, vaciar/deshacer, bloqueo durante guardado (1, 2, 5, 6) | Implementado y probado (`CreateOrderViewModelTest`, incluida latencia simulada) |
| 4 | Proteger editores y restaurar borradores, conservar fotos (3, 4) | Implementado; serialización y restauración probadas en JVM; muerte de proceso real pendiente en dispositivo |
| 5 | Guardados seguros, cargas con salida, reportes consistentes (7–10, 20, 24) | Implementado; doble toque en recepción y detalle inexistente probados; resto pendiente en dispositivo |
| 6 | Navegación, catálogo, inicio, formularios e inventario explícito (11–13, 15, 18, 21) | Implementado; revisión visual con sesión pendiente |
| 7 | Recepciones: selección, cantidades visibles y visor de facturas (16, 17) | Implementado; límite de fotos probado; visor pendiente en dispositivo |
| 8 | Primera carga y seguimiento persistente de sincronización (22, 23) | Implementado; validar escenarios de red |
| 9 | Actualización no bloqueante, permisos y acceso coherentes (25, 26, 33) | Implementado; acceso y registro revisados en emulador |
| 10 | Historiales/reportes: filtros, resumen y renderizado (31) | Implementado; compilado |
| 11 | Compilación, pruebas de comportamiento, contraste y revisión final | Hecho, salvo revisión visual con sesión iniciada |
| 12 | Pedido nuevo: reabrir la tienda que quedó abierta al iniciar con sesión, pudiendo volver a la lista | Implementado; compilado; prueba con sesión pendiente |

Secuencia: resolver componentes antes de reutilizarlos; actualizar esta tabla y el registro al terminar cada lote; ejecutar pruebas dirigidas y compilación; documentar exactamente cualquier limitación. El usuario pidió después hacer commits, versión y despliegue al terminar (v1.1.0).

**Registro de ejecución**

- Cierre (6 oct.): 34 pruebas en app (antes 15), data 14, domain 36, sin fallos; `assembleRelease` con R8 correcto. Nuevas pruebas: contraste por tema, búsqueda sin tildes/varias palabras, venta por nombre, cantidad exacta, deshacer, carrito bloqueado con latencia, restauración de carrito y recepción tras serializar, doble toque al crear producto, límite de diez fotos, detalle inexistente y serialización de todos los borradores. Limitación: en JVM `toRoute` devuelve `null` en argumentos opcionales, así que la recepción inexistente se probó con el detalle de venta (ID obligatorio).
- Emulador `ct_test`: release 1.1.0 instalada encima de 1.0.2, arranca sin fallos; login claro y registro oscuro con el mínimo de ocho caracteres visible. El emulador no tiene sesión iniciada, así que no se revisaron pantallas de tienda ni la reapertura de la última tienda.
- Tienda recordada: `LastStorePreference` (SharedPreferences por usuario) guarda la tienda presente en la pila de navegación y la olvida al volver a la lista. Al abrir la app con sesión (no al recrearla), se navega a esa tienda sobre la lista, sin animación, y Atrás vuelve a la lista.
- Texto secundario con `outline` cambiado a `onSurfaceVariant` (foto de formulario y «Sin control de stock»). Colores de estado expuestos como `LightStatus`/`DarkStatus` para probarlos.
- Tras la interrupción: `:app:compileDebugKotlin` fallaba en `Reports.kt` (faltaban imports `remember`/`mutableIntStateOf` de la paginación); corregido y compilación exitosa. `gradlew --offline test`: 65 pruebas existentes, 0 fallos (app 15, data 14, domain 36). Siguiente: pruebas nuevas de contraste, búsqueda, carrito y borradores.
- Primer build y suite existente exitosos: `:app:compileDebugKotlin :app:testDebugUnitTest` (26 s), antes del último lote de filtros/historiales. Los errores de importación anteriores están corregidos. Falta ejecutar pruebas nuevas y suite final completa.
- ADB funciona con ejecución autorizada: `adb devices -l` no encontró dispositivos conectados. La verificación visual sigue pendiente por falta de dispositivo, no por permisos.
- Azul principal ajustado a `#005EA8` para alcanzar contraste 5,36:1 incluso sobre `#E0E8F0`; oscuro `#70B8FF`. Se añadirán pruebas de los pares de texto/fondo reales.
- Sincronización: estado observable con cola, envío en curso, última confirmación y error consultable desde la nube en las barras. Se restablece al cambiar de usuario. El seguimiento es persistente entre pantallas durante la sesión; no hay un historial de errores entre reinicios.
- Actualizaciones: descarga ocultable, estado compartido entre raíz y Configuración, acción para volver a consultar/instalar. Se retiró pedido de notificaciones de inicio. Google solo aparece cuando hay client ID; registro/recuperación exigen ocho caracteres y login permite contraseñas antiguas.
- Historiales: búsqueda y filtros Hoy/7 días/mes/todo. Reportes: resumen y paginación de cuarenta filas para limitar composición; exportaciones conservan todas las filas. Gestión de productos: categoría, stock y lista compacta.

- Editores: protección de salida y serialización de borradores/baseline en SavedStateHandle para producto, recepción, tienda, proveedor y perfil; carrito/pago también. El selector de galería intenta conservar permiso de lectura de URI. Pendiente validar muerte de proceso en dispositivo y serialización en tests.
- Guardados: categorías/permisos esperan señal de éxito; proveedor y producto rápido bloquean doble envío; reportes bloquean cambio de filtros durante consulta. Detalles ausentes tienen mensaje y salida. DocumentStore espera el primer intento remoto antes de emitir vacío sin caché (mantiene emisión inmediata si hay datos locales).
- Navegación: títulos de módulos, catálogo directo a Todos con filtro de categoría, acción Nueva venta destacada y cabecera compacta para empleados; tarjetas de categorías abren detalle y ofrecen quitar explícitamente. Producto: campos principales antes de foto, una columna, botón fijo y control explícito de inventario.
- Recepciones: selector compartido con cantidades, límite visible de diez fotos y visor con zoom/restablecer; quitar foto reserva 48 dp. Recompilar: el último error conocido era `savedState` sin propiedad en SupplierEditorViewModel, ya corregido.

- Paleta aplicada: Radix Blue + Slate adaptada, azul principal claro `#0D74CE`, principal oscuro `#70B8FF`, contenedores azul pálido/navy, colores de estado por tema. Fuentes adicionales: https://www.radix-ui.com/colors/docs/palette-composition/scales y https://github.com/radix-ui/colors . El logo existente conserva su azul original.
- Componentes: búsqueda con limpiar/acción de teclado, normalización de tildes para nombres, detalle desplazable, selector común de productos con cantidades visibles, guardia de salida reutilizable y campos con acción Siguiente. Ventas: selector por nombre/código, cantidad exacta, confirmación de vaciado, deshacer retirada y guardia de mutaciones durante guardado. Borrador de carrito y pago mediante SavedStateHandle.
- Compilación disponible con permiso de ejecución revisado automáticamente: PowerShell `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:GRADLE_USER_HOME = 'C:\Users\GTLui\.gradle'; .\gradlew.bat --offline :app:compileDebugKotlin --console=plain`. Primer intento detectó función de normalización faltante, ya añadida; repetir al cerrar el siguiente lote. En scripts Node por stdin usar `$OutputEncoding = [System.Text.UTF8Encoding]::new($false)` para preservar tildes.

- Inicio: repositorio sin cambios de aplicación; solo este informe estaba sin seguimiento. No hay AGENTS.md en el workspace. No se usa delegación a subagentes.
- Investigación: paleta azul de Atlassian y roles semánticos de Material 3. Fuentes: https://atlassian.design/foundations/color/color-palette/ y https://m3.material.io/styles/color/the-color-system . La selección final y los contrastes se registrarán tras implementarla.
- Verificación pendiente: preparar Gradle/JDK disponibles sin leer/imprimir secretos. La revisión anterior no pudo iniciar ADB; volver a comprobar si es posible sin alterar configuración global.

**Para continuar tras una interrupción**

Leer primero esta tabla y el registro, revisar `git diff` y ejecutar las comprobaciones pendientes. El objetivo abarca los 33 hallazgos, no solo la paleta. Mantener explícitos los parciales y pruebas no realizadas. No asumir que un cambio editado ya compila. Actualizar este bloque con comandos, resultados y siguiente acción concreta al cerrar cada etapa.

La aplicación tiene una base visual coherente. Las mejoras de mayor impacto están en completar las tareas de venta, conservar el trabajo del usuario y comunicar correctamente carga, guardado y sincronización. Conviene resolver esos flujos antes de cambiar la identidad visual.

**Alcance y límites**

Revisión estática de las pantallas de acceso, tiendas, catálogo, ventas, compras, reportes, miembros, notificaciones, configuración, administración y actualizaciones; navegación, componentes, tema y las partes de dominio/datos necesarias para comprobar sus efectos. No se modificó código de la aplicación ni se leyeron archivos `.env`.

Se verificaron rutas y llamadas, condiciones de los estados y colores sRGB definidos en el tema. No se ejecutaron pruebas de interfaz ni se inspeccionaron pantallas renderizadas: ADB falló al inicializar su directorio `.android` por permisos del entorno. Los posibles recortes, solapamientos y anuncios de TalkBack se identifican como riesgos pendientes de prueba. No se ejecutó Gradle: compilar o pasar pruebas unitarias no demostraría estos aspectos visuales.

Prioridades: **P1** = bloquea tareas importantes, pierde trabajo o puede confundir datos operativos; **P2** = fricción o accesibilidad relevante; **P3** = refinamiento. **Confirmado** describe el comportamiento visible en código; **riesgo** requiere reproducción; **propuesta** es una decisión de diseño que conviene validar con usuarios.

Las referencias parten de `app/src/main/java/com/lfergt/controltienda/`, salvo que se indique otra raíz. Las líneas corresponden a la revisión actual.

**Hallazgos prioritarios**

1. **P1 · Confirmado · No se pueden seleccionar productos existentes por nombre al vender.**
   `feature/orders/CreateOrderScreen.kt:113` ofrece barras, QR e introducción manual. El método `addProduct` de `CreateOrderViewModel.kt:98` no está conectado a un selector de la pantalla. Si el producto no tiene código o falla la cámara, el usuario termina creando un ítem manual, que no descuenta inventario. Agregar «Buscar producto» y permitir introducir un código existente sin cámara, como ya permite el escáner de otros módulos. Criterio: vender un producto registrado sin código, por nombre, conservando su identidad, precio e impacto en stock.

2. **P1 · Confirmado · No se puede introducir una cantidad exacta en una línea de producto registrado.**
   `feature/orders/CreateOrderScreen.kt:203` fija incrementos de 1 o 0,25 y muestra la cantidad como `Text`. Un producto de 0,375 kg no se puede registrar mediante esos controles; vender muchas unidades exige demasiados toques. Hacer editable la cantidad, con teclado decimal cuando corresponda, unidad visible y validación; conservar +/− como atajos. Criterio: registrar 0,375 kg o 36 unidades sin recurrir a un ítem manual.

3. **P1 · Confirmado · Salir de los formularios descarta cambios sin advertencia.**
   `feature/catalog/ProductEditor.kt:250`, `feature/purchasing/ReceptionEditor.kt:279`, `feature/purchasing/Suppliers.kt:228`, `feature/store/StoreEditor.kt:134` y `feature/profile/Settings.kt:137` conectan directamente el regreso. La protección sí existe para órdenes (`CreateOrderScreen.kt:103`). Aplicar detección de cambios a flecha y gesto de atrás, con «Seguir editando» y «Descartar». Para recepciones largas, ofrecer borrador. Criterio: escribir, adjuntar una foto y volver no elimina el trabajo por un gesto accidental.

4. **P1 · Confirmado · Los borradores principales no se restauran tras la muerte del proceso.**
   `CreateOrderViewModel.kt:56`, `ProductEditor.kt:121` y `ReceptionEditor.kt:146` conservan carrito/formulario en memoria. `SavedStateHandle` se usa para la ruta, no para guardar esos borradores. Un ViewModel cubre rotaciones, pero no la recreación tras terminar el proceso. Persistir datos pequeños o un identificador de borrador local, y gestionar la validez de las fotos adjuntas. Criterio: enviar la app al fondo, terminar su proceso y restaurarla conserva el trabajo recuperable. No confundir esta prueba con solo rotar la pantalla.

5. **P1 · Confirmado · «Vaciar» borra todo el carrito inmediatamente.**
   `CreateOrderScreen.kt:152` llama directamente a `clear()` (`CreateOrderViewModel.kt:116`); quitar una línea o reducirla a cero tampoco ofrece deshacer. Es inconsistente con la confirmación al salir. Pedir confirmación para vaciar y ofrecer «Deshacer» al quitar una línea. Criterio: un toque accidental no obliga a escanear de nuevo toda la venta.

6. **P1 · Riesgo respaldado por código · La venta sigue siendo editable mientras se registra.**
   Solo se deshabilita «Registrar venta» (`CreateOrderScreen.kt:249`). Cantidades, quitar, vaciar, método de pago y opciones para agregar siguen activos; `CreateOrderViewModel.kt:126` envía el carrito y después lo vacía. Si el guardado demora y se agregan productos, pueden desaparecer al completar la operación sin formar parte de la venta enviada. Bloquear las mutaciones mientras se registra o separar explícitamente la venta enviada de un nuevo carrito. Criterio: con latencia simulada, lo confirmado coincide exactamente con la venta guardada y ningún cambio posterior se pierde.

7. **P1 · Confirmado · Algunos detalles confunden «no existe» con «cargando».**
   `feature/orders/MySales.kt:145` usa `null` como valor inicial y como ausencia; en `:155` ambos muestran el spinner. `ReceptionEditor.kt:156` y `StoreEditor.kt:84` esperan indefinidamente `filterNotNull().first()` si el registro nunca aparece. Separar carga, disponible, no encontrado, sin acceso y error; proporcionar volver/reintentar. Criterio: abrir un ID inexistente o eliminado presenta una salida comprensible sin carga infinita.

8. **P1 · Confirmado · Cambiar filtros durante la generación puede presentar un reporte de otro periodo.**
   `feature/reports/Reports.kt:219` captura los filtros al iniciar la consulta; los controles de `:330`, `:349` y `:357` permanecen activos. Los setters actualizan el periodo visible y borran la tabla, pero la consulta anterior puede terminar en `:235` y reinsertar su resultado. Bloquear filtros durante la consulta o cancelar/descartar respuestas que no coincidan con la solicitud vigente. Criterio: cambiar de mes mientras carga nunca muestra una tabla o exportación atribuida al periodo equivocado.

9. **P1 · Confirmado · Un guardado fallido de categoría o permisos obliga a reconstruir la edición.**
   `feature/catalog/Categories.kt:125` y `:311` cierran el diálogo antes de que termine el guardado. `feature/members/MembersScreen.kt:152` hace lo mismo con permisos. Si falla, solo queda el mensaje y se pierde la edición local al desmontar el componente. Mantener abierto, mostrar progreso y cerrar únicamente tras el éxito; conservar los datos para reintentar. Criterio: un fallo al procesar la foto o guardar permisos no exige volver a introducir los cambios.

10. **P1 · Riesgo respaldado por código · Algunos guardados permiten envíos repetidos.**
    `feature/purchasing/Suppliers.kt:191` no tiene estado/guardia de guardado; su botón en `:248` siempre está habilitado. `data/.../repository/CommerceRepositories.kt:107` crea un identificador nuevo para cada alta sin ID. Dos activaciones antes de completar pueden crear dos proveedores. `ReceptionEditor.kt:202` y `:453` tampoco bloquean «Crear y agregar» durante la creación rápida. Incorporar guardia en ViewModel, estado visible y botón deshabilitado; aplicar idempotencia cuando corresponda. Criterio: doble toque con latencia produce una sola alta.

**Navegación, catálogo y velocidad de trabajo**

11. **P2 · Confirmado · Los títulos no identifican el módulo actual.**
    Gestionar productos (`ManageProducts.kt:65`), categorías (`Categories.kt:109`), ventas (`MySales.kt:86`), nueva orden (`CreateOrderScreen.kt:107`), proveedores, recepciones, miembros y lista de reportes muestran solo la tienda. Usar «Productos», «Nueva venta», «Mis ventas», etc., con la tienda como subtítulo. Unificar «orden» y «venta» para la misma tarea, manteniendo «N.º de venta» si no existe un flujo independiente de pedidos. Criterio: al volver a la app se reconoce tarea y tienda sin interpretar el contenido.

12. **P2 · Propuesta · El inicio de tienda da demasiado espacio a la foto y el mismo peso a todas las tareas.**
    `feature/store/StoreLanding.kt:184` fija una foto de 220 dp en vertical, más nombre/dirección; `:160` muestra todos los módulos como tarjetas equivalentes. Para trabajo diario, probar cabecera compacta o colapsable, «Nueva venta» como acción principal y grupos de ventas, inventario y administración. Mantener una experiencia apropiada para visitantes, que no necesitan acciones operativas. Criterio: un empleado encuentra y abre la venta rápidamente en un teléfono pequeño.

13. **P2 · Confirmado / propuesta · Buscar en el catálogo exige elegir primero una categoría.**
    `navigation/AppNavHost.kt:46` abre categorías; `feature/catalog/ViewProducts.kt:66` no tiene búsqueda y esta aparece recién en `:114`. Ofrecer «Todos los productos» con búsqueda al entrar y categorías como filtros. En gestión, agregar filtros por categoría/stock y opción de lista compacta si el catálogo es grande. Criterio: localizar un producto por nombre desde la entrada del catálogo sin conocer su categoría.

14. **P2 · Confirmado · Búsqueda sensible a tildes y sin acción de limpiar.**
    `feature/catalog/CatalogCommon.kt:51` solo aplica `trim().lowercase()`: «cafe» no coincide con «Café». `ui/components/Forms.kt:146` no incluye botón de limpiar ni una acción explícita del teclado. Normalizar diacríticos para nombres, preservando los códigos como identificadores; agregar limpiar, cantidad de resultados y salida de «Sin resultados». Evaluar búsqueda por varias palabras. Criterio: «cafe» encuentra «Café» y se puede restaurar la lista con un toque.

15. **P2 · Confirmado · Tocar un producto tiene significados inesperados en categorías.**
    `feature/catalog/Categories.kt:256` abre la confirmación para quitarlo de la categoría; en «Todos» la tarjeta conserva interacción pero no hace nada. En otras pantallas el mismo `ProductTile` abre detalle o edición. Reservar el toque principal para detalle y mostrar «Quitar de la categoría» en una acción explícita. Criterio: el usuario puede inspeccionar un producto sin que la primera respuesta sea una acción de retirada.

16. **P2 · Confirmado · El selector de productos de una recepción no muestra qué acaba de agregarse.**
    `ReceptionEditor.kt:354` deja el selector abierto, mientras `ProductPicker` (`:410`) solo recibe productos y moneda; no conoce las cantidades de la recepción. Se puede añadir varias veces sin ver el resultado hasta cerrar. Mostrar cantidad agregada, feedback y «Listo»; permitir seleccionar desde toda la fila. Añadir estado sin resultados. Criterio: agregar varios productos permite verificar la selección sin cerrar y reabrir la hoja.

17. **P2 · Confirmado · Las fotos de factura no se pueden inspeccionar y el límite se aplica silenciosamente.**
    `ReceptionEditor.kt:325` presenta miniaturas de 96 dp sin acción de ampliar. `addPhoto` (`:176`) descarta nuevas fotos al llegar a diez, pero «Agregar foto» (`:334`) sigue disponible. Añadir visor con zoom, deshabilitar o explicar el límite y permitir reemplazar. Criterio: leer una factura adjunta dentro de la app y entender por qué no se puede adjuntar otra.

**Formularios y claridad de estados**

18. **P2 · Confirmado / riesgo visual · El editor de producto prioriza una foto grande sobre los campos necesarios.**
    `ProductEditor.kt:262` pone la foto 4:3 antes de nombre y precio; el botón queda al final (`:320`). Precios, categoría/unidad y stock se organizan siempre en dos columnas. Priorizar nombre, precio y unidad; hacer opcionales foto/códigos/costo mediante secciones y mantener una acción de guardado visible. Adaptar columnas al ancho y tamaño de letra. Criterio: crear un producto básico sin desplazamientos innecesarios y sin etiquetas recortadas con fuente grande.

19. **P2 · Confirmado / riesgo visual · Falta una política común para teclado y errores.**
    `Forms.kt:72` configura el tipo de teclado, pero no expone acciones «Siguiente», «Listo» o «Buscar». Los formularios largos no llevan al primer error; por ejemplo, `ProductEditor.kt:190` puede marcar precio, arriba, después de pulsar guardar abajo. El login sí usa `imePadding`; en `BackScaffold` ese ajuste se aplica al snackbar, no al contenido. Hay `adjustResize` en el manifiesto, por lo que la oclusión real debe probarse, no asumirse. Añadir navegación de foco, desplazamiento al error y un patrón de insets verificado. Criterio: completar y corregir el formulario sin cerrar repetidamente el teclado.

20. **P2 · Confirmado · El feedback de guardado es inconsistente.**
    Producto (`ProductEditor.kt:320`), recepción (`ReceptionEditor.kt:345`) y perfil (`Settings.kt:160`) deshabilitan el botón sin cambiar su texto ni mostrar progreso. El editor de tienda y ventas sí presentan un indicador. Estandarizar «Guardando…», éxito y error recuperable. Diferenciar «Guardado en este dispositivo» de «Sincronizado» cuando corresponda. Criterio: durante una operación lenta el usuario entiende que el toque fue recibido y qué falta.

21. **P2 · Confirmado · «Vacío = ilimitado» es una regla operativa demasiado implícita.**
    `ProductEditor.kt:299` usa campo vacío para dejar de controlar existencias; `StockLabel` (`Tiles.kt:220`) lo presenta como «Stock ilimitado». Vaciar accidentalmente el campo cambia el comportamiento del inventario. Proponer un switch «Controlar inventario» y campos de cantidad/mínimo condicionados; usar «Sin control de stock» si ese es el significado real. Criterio: dejar de controlar existencias requiere una decisión explícita.

22. **P2 · Confirmado · La primera carga no distingue catálogo vacío de caché todavía sin datos.**
    `CatalogCommon.kt:47` deriva `loaded` del encabezado. `ui/common/StoreContext.kt:30` lo marca al emitir los flujos locales. `data/.../supabase/DocumentStore.kt:192` emite caché mientras actualiza la red por separado. En un primer acceso pueden aparecer estados vacíos o sin acceso antes de completar la carga remota. Incorporar estado de primera carga/refresco y antigüedad de datos; conservar contenido útil cuando ya existe caché. Criterio: red lenta y primera visita no comunican erróneamente que no hay productos.

23. **P2 · Confirmado · La sincronización no tiene un lugar persistente de seguimiento.**
    `MainActivity.kt:109` muestra mensajes temporales y `Scaffolds.kt:99` un icono sin conexión. Ventas y recepciones sí muestran pendientes, pero la UI no ofrece recuento global, última sincronización ni detalle recuperable de errores. Crear un indicador consultable con pendientes y fallos. El mensaje de conexión restablecida no debe equivaler a confirmación de envío terminado. Criterio: el usuario puede comprobar si sus cambios ya llegaron al servidor después de desaparecer el snackbar.

24. **P2 · Confirmado · Buscar miembros puede ocultar errores y presentar respuestas antiguas.**
    `MembersViewModel.kt:102` captura errores por nombre sin mostrarlos y conserva resultados; la búsqueda no cancela automáticamente la anterior al cambiar el texto. `feature/master/Master.kt:99` lanza consultas por cada cambio y no limpia resultados al vaciar la búsqueda. Cancelar o descartar solicitudes antiguas, identificar carga/error/sin resultados y limpiar al vaciar. Criterio: escribir rápidamente y perder conexión no deja una lista correspondiente a otro texto sin explicación.

25. **P2 · Confirmado · La descarga de una actualización bloquea el uso de la app.**
    `feature/update/AppUpdate.kt:115` abre un diálogo que no se puede descartar y no ofrece acciones; `dismiss()` ignora el estado de descarga. Permitir continuar trabajando y mostrar progreso consultable, con cancelar/reintentar según soporte. Criterio: descargar con conexión lenta no impide registrar ventas.

26. **P2 · Confirmado · Se pide permiso de notificaciones sin una funcionalidad de notificación del sistema implementada en el código revisado.**
    `HomeScreen.kt:194` solicita permiso al entrar; `notifications/Push.kt` crea el canal, pero no se encontraron llamadas para publicar notificaciones. El README y los flujos documentan notificaciones dentro de la app, sin push de fondo. Retirar el pedido hasta que aporte una función o implementarla y explicar cuándo se usará. Criterio: el primer ingreso solo pide permisos necesarios para capacidades disponibles.

**Accesibilidad y adaptación visual**

27. **P2 · Confirmado por cálculo · Varios colores de texto pequeño no tienen suficiente contraste.**
    `ui/theme/Theme.kt:106` define colores de estado fijos; `MySales.kt:133`, `Receptions.kt:112` y `Tiles.kt:217` los usan en texto pequeño. Cálculo de luminancia relativa sRGB con colores opacos definidos, sin simular capturas:

    | Texto / fondo | Contraste aproximado | Uso relevante |
    |---|---:|---|
    | Amarillo `#E6A100` / tarjeta clara `#EDEDF9` | 1,91:1 | Pendiente de sincronizar |
    | Rojo `#D64545` / tarjeta clara `#EDEDF9` | 3,77:1 | Estado de peligro |
    | Rojo `#D64545` / tarjeta oscura `#1D1F28` | 3,75:1 | Estado de peligro en oscuro |
    | `outline` `#747687` / fondo claro `#FAF8FF` | 4,26:1 | Algunos textos auxiliares |

    Android recomienda al menos 4,5:1 para texto pequeño. Definir colores semánticos para texto/fondo por tema y usar `onSurfaceVariant` para contenido secundario en vez de `outline`. Añadir etiquetas «Stock bajo»/«Agotado» junto a cantidades, no solo variaciones de color. Ver [guía oficial de accesibilidad](https://developer.android.com/guide/topics/ui/accessibility/apps). Criterio: verificar cada combinación real en claro y oscuro, incluidos los distintos fondos de tarjetas.

28. **P2 · Confirmado / riesgo de TalkBack · Hay controles sin etiqueta de acción suficientemente contextual.**
    El avatar de `HomeScreen.kt:97` identifica la persona o sus iniciales, pero no explica «Abrir menú de cuenta». `Forms.kt:288` hace pulsable una foto cuyo contenido carece de descripción al existir imagen. Los switches de `MembersScreen.kt:185` y `StoreEditor.kt:174` no asocian explícitamente la etiqueta vecina a su nodo; comprobar la lectura resultante. Unir etiqueta/estado/acción mediante semántica o una fila `toggleable`; usar nombres como «Reducir cantidad de Café» donde convenga. Los iconos decorativos con descripción nula son correctos. Criterio: completar los flujos con TalkBack sin inferir la función por posición. Ver [semántica y controles en Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

29. **P2 · Riesgo visual · Diálogos largos no tienen desplazamiento explícito.**
    `CatalogCommon.kt:62` muestra foto y metadatos; `CreateOrderScreen.kt:362` varios campos manuales; `Categories.kt:140` nombre y foto; `MembersScreen.kt:221` invita con controles/resultados. Todos usan columnas sin scroll del conjunto. Con teclado, horizontal o fuente grande, parte del contenido puede no ser alcanzable. Limitar altura, permitir scroll o usar una pantalla/hoja apropiada para formularios extensos. Criterio: acceder a todos los campos y acciones en horizontal y con fuente al 200 %.

30. **P2 · Riesgo visual · Filas y tarjetas rígidas pueden degradarse en pantallas compactas.**
    `CreateOrderScreen.kt:207` coloca foto de 52 dp, descripción, cantidad y tres botones en una sola fila; queda muy poco ancho para el nombre. `Tiles.kt:138` fija proporción de las opciones aunque aumente el texto. `Settings.kt:208` alinea código grande con dos botones. `ReceptionEditor.kt:402` dibuja quitar foto en 28 dp: Compose puede ampliar automáticamente su zona táctil, por lo que no se afirma que mida solo 28 dp, pero hay que comprobar separación y solapamientos. Usar filas en dos niveles, alturas flexibles y reserva táctil suficiente. La [documentación de Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) explica el objetivo de 48 dp y la expansión automática. Criterio: 320/360 dp, fuentes 100/150/200 %, sin recortes que impidan la tarea.

**Refinamientos posteriores**

31. **P3 · Propuesta · Reportes y listados necesitan facilitar decisiones frecuentes.**
    `Reports.kt:393` presenta una tabla horizontal sin resumen previo; todas sus filas se componen mediante `forEachIndexed`. `MySales.kt:91` y `Receptions.kt:86` carecen de filtros de fecha/búsqueda. Probar indicadores de total/cantidad/margen antes de la tabla, conservar encabezados al desplazarse y ofrecer filtros útiles en historiales. Medir rendimiento con datos grandes antes de atribuir lentitud; considerar renderizado perezoso o paginación si la medición lo justifica.

32. **P3 · Confirmado / propuesta · Algunos controles parecen acciones aunque solo muestran estado.**
    `Notifications.kt:193` y `:196` usan chips pulsables con `onClick = {}`; la nube de `StoreLanding.kt:223` también es un botón sin efecto. Presentarlos como etiquetas o darles una acción explicativa. Los estados vacíos deberían ofrecer una salida contextual; por ejemplo, proveedores filtrados (`Suppliers.kt:114`) no muestran «Sin resultados». Unificar esos patrones y mensajes.

33. **P3 · Confirmado · La experiencia de acceso comunica reglas inconsistentes.**
    `LoginViewModel.kt:50` valida seis caracteres; la recuperación de `MainActivity.kt:94` exige ocho. El registro no anticipa el mínimo hasta el error. El botón de Google se muestra siempre, aunque `LoginScreen.kt:206` detecte configuración ausente solo al pulsarlo. Unificar la regla de contraseña con la configuración real del backend y exponer solo opciones de acceso disponibles. No se inspeccionaron secretos para determinar si Google está configurado en este despliegue.

**Aspectos que conviene conservar**

- Material 3, tema claro/oscuro, tipografía y formas compartidas: ofrecen consistencia sin rediseñar cada pantalla.
- Grillas adaptables y ancho máximo en formularios/listas: buena base para distintas dimensiones.
- Estados vacíos con texto explicativo y restricciones por rol en muchas pantallas.
- Protección al salir de una venta, confirmaciones para eliminar productos/categorías y confirmación reforzada para eliminar cuenta.
- Escáner con respuesta visual, sonido, vibración, linterna, reintento y acceso a ajustes de permisos.
- Indicación de pendientes en ventas/recepciones, total y método de pago visibles y errores junto a campos en varios editores.
- Rangos de fechas rápidos y exportación de reportes.

**Orden de implementación recomendado**

| Entrega | Objetivo | Hallazgos |
|---|---|---|
| 1 | Completar ventas y proteger datos operativos | 1–10 |
| 2 | Hacer visible tarea, resultado y sincronización | 11, 14–16, 19–24, 27–28 |
| 3 | Mejorar composición y adaptación visual | 12–13, 17–18, 29–30 |
| 4 | Pulir acceso, actualizaciones y análisis | 25–26, 31–33 |

Son grupos de alcance, no estimaciones de tiempo. Algunas mejoras pequeñas —títulos, contraste, estados de botones— se pueden entregar antes, sin esperar un rediseño completo.

**Verificación pendiente en dispositivo**

| Escenario | Comprobación |
|---|---|
| Teléfono compacto, horizontal, tablet y multiventana | Acciones visibles, etiquetas legibles y formularios alcanzables |
| Tema claro/oscuro y fuentes 100/150/200 % | Contraste, adaptación y contenido completo |
| TalkBack y navegación por teclado | Nombre, rol y estado de controles; orden de foco y anuncios |
| Primer acceso lento, sin conexión con/sin caché | Distinguir espera, vacío, datos locales y error |
| Reconexión y rechazo de sincronización | Estado persistente de pendientes y recuperación |
| Venta por nombre, código escrito y cámara | Mismo producto e impacto de inventario, cantidad exacta |
| Doble toque y guardados con latencia | Una sola operación; controles coherentes y sin pérdida de cambios |
| Borrador con fotos y terminación del proceso en segundo plano | Recuperación de campos, carrito y adjuntos válidos |
| Registro eliminado o permisos revocados | Estado final comprensible, volver/reintentar |
| Reporte lento y cambio de periodo durante la consulta | Tabla, filtros y exportación corresponden a la misma solicitud |
| Catálogo/historial grande y nombres largos | Búsqueda útil, lectura y desplazamiento fluidos |

No se encontraron pruebas instrumentadas en `app/src/androidTest`. Para las correcciones, priorizar pruebas de comportamiento de estos escenarios y de los estados asíncronos; las capturas de pantalla ayudarían a detectar regresiones de tamaños, tipografía y temas.
