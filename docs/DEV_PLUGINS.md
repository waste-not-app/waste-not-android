# Development Plugins

The tooling that speeds up Compose development in this project: what each plugin does, how it is configured, and the commands it adds.

## Contents

1. [Overview](#overview)
2. [Compose HotSwan (hot reload)](#compose-hotswan-hot-reload)
3. [Compose Navigation Graph](#compose-navigation-graph)
4. [Recommended IDE plugins](#recommended-ide-plugins)
5. [Shortcuts and commands](#shortcuts-and-commands)

## Overview

| Plugin | Type | Version | Purpose |
|---|---|---|---|
| Compose HotSwan | Gradle compiler plugin and IDE plugin | `2.0.1` | Hot reload of Compose UI that keeps input and navigation state |
| Compose Navigation Graph | Gradle plugin, KSP processor, and IDE plugin | `0.3.0` | Interactive visualization of the Navigation 3 graph with screen thumbnails |
| KSP | Gradle plugin | `2.3.12` | Annotation processing for the navigation graph |
| ADB Idea | IDE plugin | Latest | Quick device actions: clear data, restart, kill, uninstall |
| JSON To Kotlin Class | IDE plugin | Latest | Generates `@Serializable` Kotlin data classes from JSON |

## Compose HotSwan (hot reload)

HotSwan swaps classes directly in ART through a compiler-backed interpreter. Unlike Live Edit, it keeps form input, remembered values, and the navigation back stack. It reloads changes to composables, regular functions, and resources.

### Configuration

HotSwan is declared in `gradle/libs.versions.toml` and the root `build.gradle.kts`. It is applied only when the Gradle property `wastenot.hotReload=true` is set. Regular builds are native, so enable the property in a run configuration for hot reload sessions only.

```toml
[versions]
hotswan = "2.0.1"

[plugins]
hotswan-compiler = { id = "com.github.skydoves.compose.hotswan.compiler", version.ref = "hotswan" }
```

The Gradle plugin version must match the IDE plugin version exactly.

### Installation

1. Open **Settings** (`Ctrl+Alt+S`, or `Cmd+,` on macOS), then **Plugins** → **Marketplace**.
2. Search for **Compose HotSwan**, install it, and restart the IDE.

### Daily use

1. Run the app with `-Pwastenot.hotReload=true` on a connected device (`Shift+F10`).
2. Open **View** → **Tool Windows** → **HotSwan** and wait for the `WATCHING` status.
3. Edit a composable and save (`Ctrl+S` or `Cmd+S`). The change appears on the device within milliseconds.

### Troubleshooting

- **The status never reaches `WATCHING`.** Check that **Settings** → **Tools** → **Compose HotSwan** → **App Module Path** is set to `:app`.
- **Lifecycle methods.** Methods that call `super`, such as `MainActivity.onCreate()`, are skipped by the interpreter and always run natively.
- **Data and recovery logic.** HotSwan 2.0.1 reflection wraps `IOException`s thrown by suspend reads, which breaks the I/O recovery paths in the interpreter. Use native builds to check data or recovery behavior; hot reload is for visual work only.
- **`SessionTokens`.** This class is excluded through `hotSwanCompiler.exclude`, because HotSwan 2.0.1 cannot build a dispatch bridge for the cross-file `RefreshToken` value class. Changes to that DTO need a full rebuild. See the [HotSwan exclusion docs](https://hotswan.dev/docs/gradle-configuration#excluding-classes).

## Compose Navigation Graph

Maps the Compose navigation flow, fully compatible with Navigation 3, as an interactive graph inside Android Studio.

### Configuration

```toml
[versions]
ksp = "2.3.12"
skydovesNavgraph = "0.3.0"

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
skydoves-navgraph = { id = "com.github.skydoves.navgraph", version.ref = "skydovesNavgraph" }
```

Both plugins are applied in `app/build.gradle.kts`. Install the **Compose Navigation Graph** IDE plugin from the Marketplace, then open **View** → **Tool Windows** → **NavGraph Graph**.

### Annotations

Annotations come from `com.github.skydoves.navgraph.annotations`:

```kotlin
@NavGraphRoot
@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    NavDisplay(...)
}

@NavDestination(route = Main::class)
@Composable
fun MainScreen(...) { ... }

@NavPreview(route = Main::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    WasteNotTheme { MainScreen(...) }
}
```

- `@NavGraphRoot` marks the composable that owns `NavDisplay`.
- `@NavDestination` marks each screen.
- `@NavPreview` supplies the thumbnail shown in the graph.

### Commands

| Action | Make | Gradle | Output |
|---|---|---|---|
| Generate graph data | `make nav-graph` | `./gradlew :app:generateNavGraph` | `app/build/navgraph/nav-graph.json` |
| Export interactive HTML | `make nav-html` | `./gradlew :app:exportNavGraphHtml` | `app/build/navgraph/nav-graph.html` |
| Export Mermaid | `make nav-mermaid` | `./gradlew :app:exportNavGraphMermaid` | `app/build/navgraph/nav-graph.mmd` |
| Lint navigation | `make nav-lint` | `./gradlew :app:navLint` | Terminal report |
| Save the `.nav` baseline | `make nav-dump` | `./gradlew :app:navDump` | Baseline file to commit |
| Check against the baseline | `make nav-check` | `./gradlew :app:navCheck` | Exit code 0 when valid |

The exported HTML opens in any browser without a server or device.

## Recommended IDE plugins

### ADB Idea

Runs common adb actions from the IDE. Open it with `Ctrl+Alt+Shift+A` (`Cmd+Shift+A` on macOS) or **Tools** → **ADB Idea**.

| Action | Effect |
|---|---|
| Clear App Data | Resets databases and DataStore without uninstalling |
| Clear App Data and Restart | Resets the data, then relaunches the app |
| Restart App | Restarts the app process |
| Uninstall App | Removes the app from the device |

### JSON To Kotlin Class

Converts API JSON into Kotlin data classes:

1. Press `Alt+K` in a DTO package.
2. Paste the JSON and name the class.
3. Choose **KotlinX Serialization**, then generate.

Always check generated DTOs against the [API specification](design-kit/API.md).

## Shortcuts and commands

| Feature | Windows and Linux | macOS | Action |
|---|---|---|---|
| HotSwan reload | `Ctrl+S` | `Cmd+S` | Reload the edited file |
| ADB Idea menu | `Ctrl+Alt+Shift+A` | `Cmd+Shift+A` | Device quick actions |
| JSON to Kotlin | `Alt+K` | `Option+K` | Convert clipboard JSON to a data class |
| Reformat code | `Ctrl+Alt+L` | `Cmd+Option+L` | Format Kotlin code |
| Run app | `Shift+F10` | `Control+R` | Build and run on a device |

```sh
make build          # build the debug APK
make run            # build, install, and launch on a device
make check          # lint and unit tests
make pr-ready       # lint, tests, and navigation check before a pull request
make nav-html       # export the navigation graph to HTML and open it
make help           # list every target
```
