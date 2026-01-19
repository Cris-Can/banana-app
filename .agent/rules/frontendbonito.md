---
trigger: always_on
---

✅ REGLAS DE ESTÉTICA VISUAL (para AntiGravity)
1) Sistema de espaciado fijo (nunca al azar)

Usa solo estos valores:

4dp, 8dp, 12dp, 16dp, 24dp, 32dp

Reglas:

Padding general de pantalla: 16dp

Separación entre secciones grandes: 24dp

Separación entre elementos normales: 12dp

Separación interna de cards: 16dp

✅ Prohibido usar 10dp, 13dp, 18dp, etc.

2) Todo debe alinearse a una grilla

Reglas:

Todo el contenido debe estar alineado al mismo borde izquierdo (16dp)

Nada “flotando” sin alineación

Los botones principales siempre al mismo ancho (idealmente fillMaxWidth())

3) Jerarquía visual (títulos mandan)

En cada pantalla debe existir este orden:

Título grande (qué es esta pantalla)

Subtexto corto (qué hago acá)

Contenido principal

Acción principal

✅ Si no se entiende en 3 segundos qué pantalla es → está mal jerarquizada.

4) Solo 1 acción principal por pantalla

Regla clave para apps intuitivas:

1 botón principal (filled)

El resto: secundarios (outlined / text)

Ejemplo:
✅ “Crear evento” = botón principal
✅ “Cancelar” = text button

5) Cards siempre iguales

Si usas cards para eventos (recomendado):

Reglas de card:

Esquinas: 16dp

Padding interno: 16dp

Separación entre cards: 12dp

Cada card debe tener:

título

detalle corto

estado o categoría

acción (opcional)

✅ Todas las cards se ven iguales aunque el contenido cambie.

6) Máximo 2 estilos de botones

Evita pantallas con 6 botones distintos.

Usa solo:

FilledButton (acción principal)

OutlinedButton o TextButton (secundarias)

✅ El usuario aprende “qué es importante” sin pensar.

7) Texto: tamaños limitados

No inventes tamaños distintos por pantalla.

Usa:

titleLarge → títulos de pantalla

titleMedium → títulos de sección

bodyLarge → texto normal

bodyMedium → secundarios

labelLarge → botones

✅ Si hay 6 tamaños distintos en una pantalla → se ve “amateur”.

8) Colores: 1 protagonista + neutros

Reglas:

Usa un solo color principal (primary)

No uses colores fuertes para decorar

Para avisos:

éxito = verde suave

warning = amarillo suave

error = rojo suave

✅ Prohibido mezclar celeste + morado + naranja “porque sí”.

9) Iconos: consistencia total

Usa un solo pack (Material Icons)

Tamaño estándar: 20dp o 24dp

Si un botón tiene icono, sus similares también.

✅ Nada de un icono gigante en un lado y mini en otro.

10) Inputs limpios (formularios pro)

Reglas para textfields:

Siempre fillMaxWidth()

Separación entre campos: 12dp

Label claro, placeholder corto

Errores debajo del campo (no toast)

✅ Formularios consistentes = app “premium”.

11) Scaffold fijo en toda la app

Todas las pantallas usan el mismo patrón:

TopBar: título + back si aplica

Contenido: padding 16dp

FAB solo si es acción principal

BottomBar solo si es navegación global

✅ Nada de pantallas sin topbar o con padding distinto “porque sí”.

12) Animaciones pequeñas, no circo

Solo micro animaciones:

animateContentSize()

fadeIn/fadeOut al aparecer listas

loading suave

✅ Nada de transiciones largas o cosas que marean.

📌 REGLA DE ORO (la más importante)

El usuario no debe “aprender” cada pantalla.
Debe sentir que toda la app es el mismo sistema.