# FreePrint

FreePrint is an Android application written in **Kotlin** that allows users to print files directly from their Android device to printers available on the local network.

The project aims to provide a free and extensible alternative to vendor-specific printing applications by using a driver-based architecture.

---

## Overview

FreePrint enables users to:

- Select files from their Android device
- Discover printers available on the local network
- Map printers to compatible drivers
- Prepare print jobs for execution

Most of the core functionality is already implemented and operational.

---

## Current Status

The application is largely functional, including:

- File selection
- Network printer discovery
- Printer mapping and configuration
- Driver management system

### Known Limitation

The remaining issue is **driver-to-printer communication**.

Although drivers can be successfully mapped to printers, the final step of sending formatted print data to the physical printer is still under development. This is currently the primary blocker preventing full end-to-end printing.

---

## Architecture (High-Level)

1. User selects a file to print  
2. Application scans the local network for available printers  
3. Discovered printers are mapped to compatible drivers  
4. A print job is constructed using the selected driver  
5. Driver communication layer sends data to the printer (in progress)

---

## Tech Stack

- Language: Kotlin
- Platform: Android
- Build System: Gradle
- Architecture: Modular, driver-based design

---


