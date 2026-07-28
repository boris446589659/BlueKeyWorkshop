# KeyFlux

KeyFlux is an LSPosed/Xposed module for customizing Google Keyboard (Gboard).

It focuses on exposing selected hidden or experimental Gboard options, improving clipboard behavior, and integrating additional settings into Gboard's native settings interface when supported by the installed Gboard version.

## Features

* **Hidden Gboard Flags**

  * Exposes selected internal Gboard flags through configurable settings.
  * Availability may depend on the installed Gboard version.

* **Experimental Features**

  * Optional toggles for selected experimental Gboard capabilities.
  * Some features may depend on Google server-side configuration, Gboard version, device ROM, or Android version.

* **Clipboard Enhancements**

  * Adds configurable clipboard history retention options.
  * Supports longer retention behavior where compatible.
  * Designed not to log clipboard contents or copied sensitive text.

* **Chinese Personalization**

  * Enables Gboard's on-device Chinese training cache on supported versions.
  * Keeps correction storage available and avoids forced user-history decay.
  * Can be disabled independently when local learning is not desired.

* **Gboard Settings Integration**

  * Injects KeyFlux options into supported Gboard settings screens.
  * Attempts to match Gboard's native layout style where possible.

* **Compatibility Fallbacks**

  * Uses a safer initialization path with fallback hook handling.
  * Prevents duplicate initialization within the same process.

## Compatibility

Compatibility may vary depending on Android version, ROM, Gboard version, and hook framework.

### Tested / Targeted

* **Target app:** Google Gboard
* **Hook frameworks:**

  * [Vector](https://github.com/NawafCode/Vector) recommended
  * LSPosed-compatible frameworks may work, but behavior can vary
* **Architecture:**

  * `arm64-v8a`
  * `x86_64`

### Notes

* Some hidden or experimental Gboard flags may not exist in every Gboard version.
* Some features may be controlled remotely by Google and may not activate even if the local flag is enabled.
* Samsung One UI and heavily modified ROMs may require additional fallback handling.

## Installation

1. Install an LSPosed-compatible hook framework on a rooted device.
2. Download the latest KeyFlux APK from the Releases page.
3. Install the APK.
4. Enable the KeyFlux module in your hook framework manager.
5. Select **Gboard** as the module scope.
6. Force stop Gboard.
7. Open Gboard settings and configure KeyFlux options.

## Build Instructions

To build the project locally:

```bash
./gradlew assembleDebug
```

For release builds:

```bash
./gradlew assembleRelease
```

## Privacy

KeyFlux is designed not to log clipboard contents or copied sensitive text.

If you find logs that expose sensitive data, please open an issue with the log context after removing private information.

## Troubleshooting

If KeyFlux does not load correctly:

1. Make sure Gboard is selected in the module scope.
2. Force stop Gboard after enabling the module.
3. Reboot the device if needed.
4. Check LSPosed/Vector logs.
5. Open an issue and include:

   * Android version
   * ROM / device model
   * Gboard version
   * KeyFlux version
   * Relevant LSPosed/Vector logs

## Contributors

* [NawafCode](https://github.com/NawafCode) - Original project author.
* [Boris448](https://github.com/boris446589659) - Fork maintenance and device validation.
* **OpenAI Codex** - AI-assisted debugging, implementation, and validation.

## License

This project is licensed under the MIT License.
