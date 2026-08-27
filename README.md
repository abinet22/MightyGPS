# MightyGPS 🛰️

**MightyGPS** is a modern, professional, enterprise-ready multi-tenant SaaS GPS tracking client designed for Android. Powered by the **Traccar REST & WebSocket API**, MightyGPS offers real-time location updates, high-performance interactive maps, localized user interfaces, custom PDF/text report exporting, remote command dispatching, and full-featured fleet administration.

---

## 🎨 Visual Preview & User Experience

MightyGPS features a customized Material Design 3 **Dark Cosmic Slate** visual theme that ensures high-contrast readability in both daytime and nighttime field tracking environments. The interface uses generous negative space, sleek modern typography, responsive visual ripple effects, and intuitive icons to optimize screen ergonomics for fleet operators.

---

## 🚀 Core Features & Functionality

### 1. 📍 Real-Time Asset Locator & Live Map
* **Dynamic Slippy Map Engine**: A high-performance, container-based custom map renderer built natively in Jetpack Compose that seamlessly handles fluid pan and zoom gestures.
* **Mapbox & Google Styles**: Toggle dynamically between **Mapbox Streets, Mapbox Satellite, Mapbox Dark, Google Roadmap, Google Satellite, Google Terrain,** and more.
* **Camera Follow & Lock**: Locks onto selected devices, instantly flying to the vehicle’s location. Handles auto-recenter triggers smoothly.
* **Status-Aware Markers**: Custom-rendered vehicle indicators that color-code dynamically based on ignition status, moving speed, or idle states. Supports custom marker icons and label styles.

### 2. ⏳ Historic Route Playback & Trail Tracker
* **Interactive Breadcrumbs**: Loads historical route coordinate logs over any selected timeframe.
* **Animated Journey Playback**: Features controls to play, pause, or slide through historical paths, tracing the exact route taken with an animated vehicle indicator.
* **Telemetry Insights**: Shows active playback speed, direction, address details, and timestamps step-by-step.

### 3. 📊 Professional Telematics & Route Reports
* **Flexible Timeframes**: Select and analyze fleet telemetry across predefined time ranges: **Today, Weekly, or Monthly**.
* **Precise Physical Calculations**: Computes real-world metrics based on real GPS coordinates:
  * **Total Distance Traveled** (accurate km calculations)
  * **Average & Maximum Moving Speeds**
  * **Overspeeding Violations** (flagged whenever vehicle exceeds safety thresholds)
  * **Geofence Exceptions**
* **Detailed Activity Log**: A chronologically sorted telemetry log merging GPS position changes with custom alert/alarm events.
* **Professional PDF Exporter**: Renders print-ready PDF reports locally on the device, complete with stylized headers, structured metrics grids, and chronological activity logs, shareable via email, WhatsApp, or system print channels.
* **Plain Text Sharing**: Quick export options to copy and share text-based executive summaries.

### 4. 🔒 Device Commands Dispatcher
* **Remote Controls**: Send remote hardware signals directly to tracking devices, including:
  * *Engine Cut-Off / Stop*
  * *Engine Resume / Start*
  * *Set Speed Limit Constraints*
  * *Trigger Emergency Alarms*
* **Command Log History**: Live tracking of dispatched commands and status feedback.

### 5. 🛠️ Fleet & User Admin Panel
* **Asset Administration**: Instantly add new tracking devices, edit configurations, or delete inactive hardware.
* **Sub-Account & User Management**: Manage multi-tenant users, create sub-operator credentials, and assign administrative privileges.
* **Geofences Creator**: Draw circular geofences directly on the interactive map, configure safety boundaries, and link them to trackable assets.

### 6. 🌐 Dynamic Localization & Internationalization
MightyGPS natively supports dynamic language switching in the settings menu without restarting the application:
* **English** (Default)
* **Amharic (አማርኛ)**
* **Oromo (Afaan Oromoo)**
* **Spanish (Español)**

### 7. 🗄️ Offline Caching & Room Database
* **Local Persistence**: Integrated with a robust SQLite database powered by **Android Jetpack Room**.
* **Resilient Offline Mode**: Caches list coordinates, fleet assets, and custom geofence constraints, allowing operators to browse telemetry records and historic alerts even during unstable cellular coverage.

---

## 🛠️ Architecture & Tech Stack

* **Language**: 100% Kotlin
* **UI Framework**: Jetpack Compose (Material Design 3)
* **Architecture Pattern**: MVVM (Model-View-ViewModel) with Clean Architecture principles
* **Networking & WebSockets**: Retrofit for REST API queries + WebSocket channels for live event updates
* **Local Database**: Room DB (Entity caching)
* **Reporting Engine**: Android Native Graphics & Canvas PDF Rendering API
