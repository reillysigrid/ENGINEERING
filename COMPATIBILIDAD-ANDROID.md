# Compatibilidad de BrainEngineering

Este cambio se desarrolla fuera de la rama maestra `main`, que permanece intacta.

- `minSdk 23`: Android 6.0 o posterior; Android anterior no es compatible.
- `targetSdk 36`: mantiene el objetivo exigido por Google Play para nuevas aplicaciones en noviembre de 2026.
- Se comprueba la versión antes de activar WebView Safe Browsing (API 26+).
- Se añade el manejador de enlaces compatible con Android 6 y 7.
- Requiere WebView moderno, internet y que el sitio HTTPS siga disponible. No se puede garantizar el funcionamiento en todo fabricante/versión sin pruebas en dispositivos reales.
- APK debug generado en el trabajo CI: para pruebas; AAB generado sin firmar: no enviar así a Google Play.
- El contenido web sigue siendo una demostración mientras no estén disponibles las reservas, usuarios, chat y llamadas reales.
