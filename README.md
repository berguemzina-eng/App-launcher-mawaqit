# Mawaqit Launcher

App mínima para Android / Android TV que, al encender el dispositivo, abre automáticamente **Mawaqit** (horarios de oración) sin mostrar el launcher del sistema ni ninguna otra pantalla. Pensada para pantallas fijas de mezquita (Xiaomi TV con Play Store).

No tiene anuncios, no tiene interfaz propia (salvo la configuración inicial de 3 pasos) y es de código abierto: la compilas tú mismo desde tu propio repositorio.

## ¿Qué hace exactamente?

1. **Al arrancar el dispositivo**, un `BroadcastReceiver` detecta `BOOT_COMPLETED` y lanza Mawaqit.
2. **Como refuerzo en Android TV** (donde no se puede cambiar el launcher del sistema fácilmente), un servicio de Accesibilidad vigila cuándo aparece el launcher de Xiaomi y salta a Mawaqit al instante.
3. **En teléfonos/tablets**, la app puede configurarse como pantalla de inicio (Home) del sistema.
4. Un asistente de configuración de **3 pasos** (solo la primera vez) deja todo listo para uso 24/7:
   - Paso 1: activar Accesibilidad (TV) o Inicio predeterminado (móvil) — **obligatorio**, es la base del arranque automático.
   - Paso 2: excluir la app de la optimización de batería — opcional, con botón "Saltar este paso" (poco relevante en una tele, que siempre está enchufada).
   - Paso 3: permitir que la app fije la pantalla para que nunca se apague — opcional, también saltable.
5. **Al abrir la app manualmente** (no durante el arranque automático), se muestra una pantalla de bienvenida con el logotipo de mezquita animado (cúpula, minaretes, luna y estrella) durante **5 segundos por defecto** (configurable: instantáneo / 3s / 5s / 10s), con un botón "Ajustes" para acceder a más opciones sin esperar.
6. **Pantalla de Ajustes completa**: estado de cada permiso (✓/✗), botones para reconfigurar cada paso individualmente, activar/desactivar "pantalla siempre encendida", duración del splash, vista previa, y acceso directo para abrir Mawaqit al instante.
7. **Reintentos automáticos al arrancar**: si Mawaqit aún no está listo justo tras el arranque, el receptor reintenta varias veces (hasta 6, cada 2s) antes de rendirse.
8. **Vigilante permanente**: mientras el servicio de accesibilidad esté activo, cualquier pantalla que no sea Mawaqit (el launcher de Xiaomi, un diálogo de error, un cierre inesperado) dispara el relanzamiento automático de Mawaqit cada 1.5s.
9. **Registro de última apertura**: en Ajustes puedes ver la fecha, hora y origen (arranque / vigilante / app) de la última vez que se abrió Mawaqit — útil para comprobar que todo funciona sin mirar la tele en directo.
10. **Accesos directos** en Ajustes a Wi-Fi, fecha/hora, protector de pantalla, y permisos de ambas apps (Mawaqit y el propio launcher).
11. **Detecta el lanzador de Android TV (Leanback)** además del lanzador normal de móvil, para encontrar Mawaqit sea cual sea el tipo de app que tengas instalada.
12. **Botones con foco visible para el mando**: al moverte con las flechas del mando entre botones, el que está seleccionado se agranda y resalta.
13. **Nunca se cierra si actúa como pantalla de Inicio** del sistema, evitando pantallas en negro al salir de todas las apps.

## Requisitos

- Una cuenta de GitHub (gratis).
- Tener instalada la app oficial de **Mawaqit** en el dispositivo (paquete `com.mawaqit.androidtv`, la versión específica para Android TV — la de móvil es un paquete distinto, `com.kanout.mawaqit`).
- Nada más — la compilación del APK se hace en la nube con GitHub Actions, no necesitas Android Studio ni instalar nada en tu ordenador.

## Cómo subirlo a GitHub y generar el APK

1. Ve a [github.com/new](https://github.com/new) y crea un repositorio (puede ser privado o público), por ejemplo `mawaqit-launcher`.
2. Descarga y descomprime el contenido de este proyecto.
3. Sube **todos los archivos y carpetas** (incluida la carpeta oculta `.github`) a la raíz del repositorio:
   - Más fácil: en la página del repo, botón **"Add file" → "Upload files"**, arrastra todo el contenido de la carpeta descomprimida (no la carpeta en sí, su contenido) y haz commit.
   - O por línea de comandos:
     ```bash
     cd carpeta-descomprimida
     git init
     git add .
     git commit -m "Primera versión"
     git branch -M main
     git remote add origin https://github.com/TU-USUARIO/mawaqit-launcher.git
     git push -u origin main
     ```
4. Entra a la pestaña **Actions** de tu repositorio. Se habrá lanzado automáticamente el flujo **"Build APK"** (tarda 2-4 minutos).
5. Cuando el flujo termine con un ✅ verde, entra en ese resultado (run) y baja hasta la sección **Artifacts**.
6. Descarga **mawaqit-launcher-apk** (es un .zip). Descomprímelo: dentro está `app-debug.apk`.

## Cómo instalarlo en la tele / móvil

1. Copia `app-debug.apk` a la tele (USB, Google Drive, o un gestor de archivos que permita instalar APKs).
2. En la tele, activa **Ajustes → Seguridad → Orígenes desconocidos** para la app que uses para instalar (Gestor de archivos, Drive, etc.).
3. Instala el APK.
4. Abre **"Mawaqit Launcher"** desde el menú de apps y sigue los 3 pasos de configuración.
5. Además, en **Ajustes → Aplicaciones → Mawaqit Launcher → Inicio automático**, actívalo (en Xiaomi/MIUI es un permiso aparte que no se puede pedir desde la app).
6. Reinicia el dispositivo para probar que todo funciona.

## Volver a compilar tras hacer cambios

Cada vez que subas un cambio a la rama `main` (por ejemplo, si algún día cambia el paquete de Mawaqit), GitHub Actions recompilará el APK solo. También puedes lanzarlo manualmente desde **Actions → Build APK → Run workflow**.

## Estructura del proyecto

```
launcher-project/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/tuusuario/mawaqitlauncher/
│       │   ├── MainActivity.kt          (asistente de configuración + ajustes + lanzador)
│       │   ├── BootReceiver.kt          (lanza Mawaqit al arrancar, con reintentos)
│       │   ├── TVHomeWatcherService.kt  (vigilante permanente de accesibilidad)
│       │   └── LaunchLog.kt             (registro de última apertura)
│       └── res/                         (icono con diseño islámico, textos)
├── .github/workflows/build.yml          (compila el APK automáticamente)
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Notas y limitaciones honestas

- En Android TV **certificado con Play Store**, el sistema no permite cambiar el launcher predeterminado sin root — por eso se usa el servicio de Accesibilidad en vez del método directo de Android normal. Esto puede causar un parpadeo de medio segundo del launcher de Xiaomi antes de saltar a Mawaqit; es una limitación del sistema, no de esta app.
- **Algunos firmwares de Android TV económicos (incluidos varios Xiaomi) no incluyen la pantalla de Accesibilidad en ningún menú de Ajustes.** Si el botón "Abrir Accesibilidad" no encuentra nada, hay que activarlo directamente por ADB con este comando (puedes ejecutarlo desde el propio móvil con la app **Termux**, sin necesidad de PC):
  ```
  adb shell settings put secure enabled_accessibility_services com.tuusuario.mawaqitlauncher/com.tuusuario.mawaqitlauncher.TVHomeWatcherService
  adb shell settings put secure accessibility_enabled 1
  ```
- **Importante:** cada vez que instales una nueva versión del APK, Android puede desactivar automáticamente el servicio de Accesibilidad por seguridad. Si notas que el launcher deja de saltar a Mawaqit tras actualizar, repite el comando ADB de arriba una vez más.
- La app declara un bloque `<queries>` en el manifiesto para poder "ver" el paquete de Mawaqit y las apps de Ajustes del sistema — esto es obligatorio desde Android 11; sin ello, `getLaunchIntentForPackage` puede devolver que la app no existe aunque sí esté instalada.
- Si cambias el nombre del paquete de Mawaqit (por ejemplo si un día usas otra app de horarios), edita la constante `MAWAQIT_PACKAGE` en `MainActivity.kt`, `BootReceiver.kt` y `TVHomeWatcherService.kt`.
- El APK generado por el workflow es una build de **debug** (sin firma de producción), suficiente para instalar y usar normalmente en tus propios dispositivos.
