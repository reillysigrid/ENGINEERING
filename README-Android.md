# BrainEngineering Android

Código Android reproducible; app WebView apuntando al sitio HTTPS de BrainEngineering.

El APK de depuración se genera en la pestaña **Actions** > **BrainEngineering Android** > Artifacts.
El AAB de Google Play se genera **sin firmar**: todavía necesita firma propia con una clave segura y pruebas de publicación. No publiques el keystore ni contraseñas en el repositorio.

Google Play: usar Android Studio > Build > Generate Signed Bundle / APK para generar el AAB firmado. La app sigue mostrando el contenido web actual (prototipo sin reservas/chat/llamadas reales). No presentarla como aplicación final lista para usuarios hasta implementar backend y verificar políticas.

Target API 36. Android 8.0+ mínimo. Idiomas nativos de mensajes del sistema: ES, EN, FR.
