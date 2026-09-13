# 🌐 DrakesTranslate

Plugin nativo de **traducción en tiempo real de chat para Paper / Purpur 1.21.1**, desarrollado exclusivamente para **DrakesCraft Network**.

---

## 🚀 Características Principales

- **Arquitectura Multi-Provider Híbrida:**
  - **Google Cloud Translation API v2:** Soporte para múltiples API Keys con rotación automática (Round-Robin o Failover). Cada cuenta de Google Cloud entrega 500.000 caracteres gratis al mes.
  - **LibreTranslate (Fallback Local en Star):** Respaldo automático sin costo si se agotan cuotas externas o no hay claves activas.
- **Caché LRU en Memoria RAM:**
  - Guarda frases recurrentes (*"hola", "gg", "alguien slimefun?", "compro minerales"*), ahorrando hasta un 70% de cuota y respondiendo en <1ms.
- **100% Asíncrono (Cero Lag):**
  - Desarrollado sobre `AsyncChatEvent` de Paper 1.21.1 y `java.net.http.HttpClient` asíncrono con `CompletableFuture`.
  - Mantiene intactos los 20.0 TPS del servidor.
- **Diseño Visual con Adventure & Hover:**
  - Muestra el texto traducido de forma limpia al receptor.
  - Al pasar el cursor por encima (`HoverEvent`), muestra el mensaje original en su idioma fuente y el motor que tradujo.
- **Soporte de Más de 100 Idiomas:**
  - Autocompletado inteligente con `<TAB>` (`es`, `en`, `pt`, `fr`, `de`, `it`, `ru`, `ja`, `zh`, etc.).
- **Compatibilidad Total:**
  - Funciona tanto para jugadores de **Java Edition** como de **Bedrock Edition (móviles y consolas vía Geyser)** sin instalar ningún mod ni aplicación externa.
  - Si un jugador ya utiliza mods de traducción en su cliente de Minecraft (como GoogleChat mod o similar), es 100% libre de seguir usándolo.

---

## 🎮 Comandos para Jugadores

| Comando | Descripción |
| :--- | :--- |
| `/translate on <idioma>` | Activa la traducción en vivo de los mensajes entrantes al idioma indicado (ej: `/translate on en`). |
| `/translate off` | Desactiva la traducción automática. |
| `/translate toggle` | Alterna rápidamente entre activado y desactivado. |
| `/translate me <idioma> <mensaje>` | Traduce tu mensaje saliente antes de emitirlo globalmente. |
| `/translate status` | Muestra tu idioma actual, estado y motor activo. |
| `/translate help` | Muestra la guía de uso in-game. |

---

## 🛡️ Comandos de Administración

| Comando | Permiso | Descripción |
| :--- | :--- | :--- |
| `/translate reload` | `drakestranslate.admin` | Recarga `config.yml`, proveedores y cachés en caliente. |

---

## 🛠️ Compilación

Requiere Java 21 y Maven:
```bash
mvn clean package
```
El archivo `.jar` resultante se genera en `target/DrakesTranslate-1.0.0.jar`.

---
© 2026 DrakesCraft Network · DrakesCraft-Labs
