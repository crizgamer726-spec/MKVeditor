# MKV Editor (Android + FFmpeg)

App nativa (Kotlin + Jetpack Compose). Edita pistas, subtítulos, audios y metadatos de MKV sin recodificar.

## Cómo obtener el APK
1. Sube TODO el contenido de esta carpeta a tu repositorio de GitHub.
2. Ve a la pestaña **Actions** → "Build APK" → espera el check verde (3–6 min).
3. Entra a la ejecución y descarga **MKV-Editor-APK** (abajo, en "Artifacts"). Descomprime e instala `app-debug.apk`.

## Estructura
- `.github/workflows/build.yml` → receta que compila el APK automáticamente
- `app/src/main/java/com/mkveditor/app/` → el código (pantalla, lógica, comandos FFmpeg)
- `app/src/main/res/` → textos, tema e icono
- `app/build.gradle.kts` → dependencias (incluye FFmpeg)
