---
trigger: always_on
---

partir de ahora, en este workspace quiero que sigas estas reglas obligatorias:

Compilación obligatoria en cada cambio

Cada vez que modifiques código, agregues archivos o cambies dependencias:
✅ compila la app inmediatamente (assemble/debug o build equivalente).

Si falla, NO avances hasta corregirlo.

Cuando haya error:

explica la causa en 1–3 líneas

pega el fix exacto (código completo)

recompila otra vez hasta que pase

Checklist post-compilación
Después de compilar exitosamente, valida rápido:

que la navegación no rompa

que la pantalla no crashee

que no haya imports innecesarios

que no haya warnings críticos

Recomendación de próximos pasos (siempre)
Al finalizar cada respuesta, agrega una sección:

✅ Próximos pasos sugeridos

3 a 7 mejoras concretas ordenadas por prioridad

debe incluir:

1 mejora UX/UI (visual o interacción)

1 mejora técnica (arquitectura/performance)

1 mejora de estabilidad (errores/logs/crash)

1 mejora de producto (feature útil real)

Formato de entrega
Siempre responde así:

✅ Resultado de compilación: (OK / FALLÓ)

🧩 Qué cambiaste: (resumen corto)

🛠️ Fix aplicado: (si hubo error)

✅ Próximos pasos sugeridos: (lista priorizada)

Regla final: si un cambio rompe la compilación, tu prioridad absoluta es arreglarlo antes de seguir agregando features.