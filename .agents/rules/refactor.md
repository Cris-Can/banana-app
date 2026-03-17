---
trigger: always_on
---

# Reglas para Refactorizar el "God Object" AppNavigation.kt (Compose Navigation)

Cuando se necesite refactorizar un archivo gigante de navegación en Jetpack Compose (como `AppNavigation.kt` con decenas de rutas) para dividirlo en sub-grafos más pequeños (`NavGraphBuilder` extension functions), **DEBES seguir obligatoriamente estas reglas** para evitar romper la compilación de la app con errores indescifrables de scopes y dependencias.

## 1. 🚫 PROHIBIDO USAR SCRIPTS DE PYTHON PARA REESCRIBIR TODO EL ARCHIVO DE UNA VEZ
No intentes extraer todas las rutas de un solo golpe usando scripts de Python basados en regex o manipulación de strings. Compose depende fuertemente de contextos, receiver scopes (`this`) y anotaciones (`@Composable`) que los scripts no entienden bien. Si lo haces, romperás los scopes de animación y variables.

## 2. 🐢 EXTRACCIÓN MANUAL Y PROGRESIVA (UN GRAFO A LA VEZ)
El proceso debe ser paso a paso. En lugar de vaciar todo `AppNavigation.kt`:
- Toma una feature pequeña primero (Ej. `authGraph` que contiene "splash", "login", "onboarding").
- Crea ese `fun NavGraphBuilder.authGraph(...)` en el mismo archivo temporalmente, o en un archivo nuevo copiando las dependencias.
- **Compila inmediatamente**.
- Si funciona, pasa al siguiente grafo (Ej. `homeGraph`).

## 3. 🎯 PROPAGACIÓN CORRECTA DE SCOPES (EL MAYOR PROBLEMA)
En Compose (especialmente con `SharedTransitionLayout`), el bloque de código dentro de `composable("ruta") { ... }` tiene contexto de `AnimatedVisibilityScope` (que es el `this` de la lambda).
- Si usas `this@composable` dentro de la lambda, y mueves ese código a una extensión `fun NavGraphBuilder.homeGraph(...)`, ese `@composable` ya no existe.
- Si usas animaciones compartidas referenciando a un `SharedTransitionScope` externo, debes pasar ese scope como parámetro de la función constructora del grafo.
- **Firma recomendada para extraer grafos con Shared Elements:**
```kotlin
fun NavGraphBuilder.homeGraph(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    sharedTransitionScope: SharedTransitionScope
) {
    composable("home") { // <- El 'this' aquí es el AnimatedVisibilityScope
        HomeScreen(
            ...,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this 
        )
    }
}
```

## 4. 📦 VERIFICAR IMPORTS SI EXTRAES A OTRO ARCHIVO
Si decides pasar tus funciones `fun NavGraphBuilder.xyzGraph(...)` a un archivo `NavGraphs.kt`:
- Muchas veces los ViewModels inyectados con `hiltViewModel()` importan de `androidx.hilt.navigation.compose.hiltViewModel`. Asegúrate de copiar explícitamente ese y los imports referidos a los UiState models relacionados.
- Copia las dependencias estáticas que tenía la clase original (modelos de enums, utils estáticas).

## 5. 🏗️ COMPILACIÓN ESTRICTA
Siguiendo la regla global del proyecto: **Solo avanzas si compila exitosamente**. Si extraes la parte de Login y falla el `kspDebugKotlin` o `compileProdDebugKotlin`, te detienes, haces un cat/view_error de gradle y reparas **ese** sub-grafo específico antes de seguir destruyendo el archivo original.

## Resumen del algoritmo de éxito (Lo que funcionó mejor):
1. **Analizo las dependencias base:** Identifico ViewModel de sesión compartida, Preferences que leían adentro.
2. **Defino la firma de mi función de grafo:** Veo qué necesita esa parte del grafo para correr y lo defino (Ej. `homeGraph(nav, sessionVM)`).
3. **Corto y pego código:** Uso herramientas precisas (edición multi reemplazar) para transferir el bloque `composable(...) { ... }` exacto de un .kt a la función de extensión. 
4. **Reemplazo `this@composable`** si procediera y compruebo el receiver de `AnimatedVisibilityScope`.
5. Administro los `import` necesarios.
6. **Compilo y Fixeo**.
7. Prosigo con el grafo N+2.
