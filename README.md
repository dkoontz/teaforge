# Teaforge Framework Platform Implementation Guide

The Teaforge framework is a flexible, type-safe framework for developing event-driven applications with clear separation of concerns around effects and state management. The concerns of what effects are possible and how to carry them out are handled by a `Platform` while the concerns of the application's logic and state is handled by a `Program`.

This guide explains how to implement a platform for the Teaforge framework.

## Understanding the Teaforge Architecture

A Teaforge program consists of three components:

1. **Model**: The state of your application
2. **Update**: A function that takes in a messages and program state and returns a modified state along with any effects the program wishes to run
3. **Subscriptions**: A mechanism provided by the platform that allows a program to receive external data on an ongoing basis. The program will return a list of zero or more subscriptions it wants to receive messages from. This will be evaluated as the program runs allowing subscriptions to be added or removed.

### (TEA)forge

Teaforge is inspired by the Elm programming language and its use of an architecure known as "The Elm Architecture" (TEA). Teaforge differs from TEA in that is platform-agnostic and requires a platform-specific implementation that makes all the decisions about what capabilites are available to a program (Effects and Subscriptions), when and how a program should be updated (when Messages are processed, Effects are run, etc.).

A platform can be any collection of capabilities (Effects and Subscriptions) such as a HTTP Server, Web Browser, Command Line Application, Mobile Application, or a specific hardware configuration such as a Raspberry Pi's GPIO pins.

### High Level Overview of Data Flow

To give you an idea of how this works let's go through how data flows into and out of a program running on a Teaforge platform.

- First the Teaforge runner is initialized. The details of this are platform specific but generally it is tied to whatever represents the "start" of the platform such as a browser loading the page, an application's main function, or a framework's init or startup function. At this point the platform's state will be initialized.

- Next Teaforge initializes the program which results in an initial Model and any Effects the program wants to run.

- At this point we are effectively in the equivalent of a "main loop" in a traditional application. Teaforge is periodically told to `step` by the platform integration which then carries out a number of actions.
    - Teaforge instructs the platform to update its Subscriptions which may produce Messages.
    - Teaforge uses the program's `update` function to process all pending Messages, one at a time, by handing update the current program Model and a Message. The result is an updated Model along with Effects the program wants to run.
    - Teaforge instructs the platform to run any effects produced by the program's update which typically produce Messages.


## Core Components for Platform Implementation

To implement a platform for the Teaforge framework, you need to define:

1. **Effect Type**: What actions can be performed on your platform.
2. **Subscription Type**: What (semi)continuous data sources are available on your platform.
3. **Platform Model**: The state maintained by your platform.
4. **Effect Handler**: The actions to take on your platform when a program wants to run a particular Effect, typically produces a Message informing the program of the success or failure of the Effect.
5. **Subscription Handler**: The process by which a data source can be polled / queried / subscribed to in order to send periodic Messages into a program.
6. **Teaforge Runner**: The integration with your platform's runtime.

## Step-by-Step Implementation Guide

### 1. Define Platform-Specific Effect Type

Effects represent actions that can be performed on your platform. This should be represented by a type that is a closed (non-extendable) set of possibilities. A sealed interface populated by data classes is recommended for this purpose. The Effect type will need to be parameterized by a Message type that will be provided by the program in order for the Effect to be able to communicate back to the program. The message to send back to the program is typically stored in the `message` field which is a function that maps the result of the Effect into a Message that the program understands.

```kotlin
sealed interface Effect<out TMessage> {
    // Log a message to the platform's console. Since this doesn't produce a message it will have Nothing as the type parameter
    data class Log(val msg: String) : Effect<Nothing>

    data class ReadFile(path: Path, message : (Result<FileIoError, File>) -> TMessage)

    data class SqlQuery<TMessage>(query : Query, message : (Result<SqlError, SqlResult>) -> TMessage) : Effect<TMessage>

    // If you have an effect with no parameters you can use object
    object DoNothing : Effect<Nothing>
}
```

### 2. Define Platform-Specific Subscription Type

Subscriptions represent data sources that are ongoing. They are very similar to Effects in that they also produce a Message to communicate back to a program but unlike an Effect a Subscription is an ongoing producer of Messages. Making a HTTP request would be an Effect since it either succeeds or fails. Getting the current system milliseconds could be done once as an Effect but more likely would be a Subscription since if you need the time once you likely will need it every step of the program.

```kotlin
sealed interface Subscription<out TMessage> {
    // Subscribe to a sensor reading, messages are sent every `pollingIntervalMs` milliseconds
    data class SensorReading<TMessage>(
        val sensorId: Int,
        val pollingIntervalMs: Int,
        val message: (Double) -> TMessage
    ) : Subscription<TMessage>

    // Only sends a message when the button's value changes
    data class ButtonValueChaged<TMessage>(
        val buttonId : ButtonId
        val message: () -> TMessage
    ) : Subscription<TMessage>
}
```

### 3. Define Platform Model

Your platform model contains platform-specific state:

```kotlin
data class PlatformModel(
    // Platform-specific resources
    val hardware: PlatformHardware,
)

// Example of platform hardware state
data class PlatformHardware(
    val motors: Map<MotorId, Motor>,
    val sensors: Map<SensorId, Sensor>,
    // Other hardware components
)
```

### 4. Implement the Effect Handler

Teaforge will take care of deciding when effects need to be processed, but it doesn't know anything about a platform's effects, those were defined by the platform. So a platform is required to provide a function that can process an effect.

Effects can be either synchronous (completing immediately) or asynchronous (completing later), they communicate this by returning an `EffectResult` which can be either `Sync` or `Async`.

```kotlin
fun <TMessage, TModel> processEffect(
        model: PlatformSpecificModel<TMessage, TModel>,
        effect: Effect<TMessage>,
): EffectResult<PlatformSpecificModel<TMessage, TModel>, TMessage> {
    return when (effect) {
        // Synchronous effect - completes immediately
        is Effect.SetPwmMotorSpeed -> {
            // do logic to communicate with PWM motor
            EffectResult.Sync(
                updatedModel = model,
                message = Maybe.None
            )
        }

        // Synchronous effect with a message result
        is Effect.ReadConfig -> {
            val config = readConfigFile()
            EffectResult.Sync(
                updatedModel = model,
                message = Maybe.Some(effect.message(config))
            )
        }

        // Asynchronous effect - runs in background
        is Effect.HttpRequest -> {
            EffectResult.Async(
                updatedModel = model.copy(requestsInFlight = model.requestsInFlight + 1),
                completion = {
                    // This action runs asynchronously
                    val result = httpClient.fetch(effect.url)

                    // Return a completion function that receives the CURRENT model
                    // when the async work finishes. The model could have been updated
                    // by other effects / subscriptions since the effect was started so
                    // you cannot use the local `model` variable.
                    { currentModel ->
                        Pair(
                            currentModel.copy(requestsInFlight = currentModel.requestsInFlight - 1),
                            Maybe.Some(effect.message(result))
                        )
                    }
                }
            )
        }
    }
}
```

The async effect pattern ensures that when multiple async effects complete in different orders, each completion function receives the up-to-date model state rather than a stale snapshot from when the effect was initiated.

### 5. Implement the Subscription Handler

Subscriptions are handled in a similar fashion to effects except subscriptions have their own state that is stored by Teaforge. When a subscription is added or removed an associated function is run to modify the platform's state as needed and to initialize / shut down any processes associated with the subscription.

```kotlin
fun <TMessage, TModel> processSubscription(
    model: PlatformSpecificModel<TMessage, TModel>,
    subscriptionState: PlatfomDefinedSubscriptionState<TMessage>
): Triple<PlatformSpecificModel<TMessage, TModel>, PlatfomDefinedSubscriptionState<TMessage>, Maybe<TMessage>> {
    return when (subscriptionState) {
        // Update a subscription that gets the value from a GPIO pin
        is SubscriptionState.IoPortValue -> runReadIoPort(model, subscriptionState)

        // This subscription sends a message every nth time the program is stepped forward
        is SubscriptionState.EveryN ->
            val config = subscriptionState.config
            val updatedSubscriptionState =
                subscriptionState.copy(times = subscriptionState.times + 1)

            // if there have been enough steps, send the message
            if(updatedSubscriptionState.times > config.n) {
                Triple(
                    model,
                    updatedSubscriptionState.copy(times = 0),
                    Maybe.Some(config.message(Unit)
                )
            }
            else {
                Triple(model, updatedSubscriptionState, Maybe.None)
            }
    }
}
```


### 6. Implement the Teaforge Runner

Finally you need to choose when you want to initialize and step the Teaforge system on your platform. You must provide a configuration for the platform as well as a program that is to be run. You can also pass along configuration arguments that will be passed to the platform runner and the program init functions to allow you to customize the initial state of both the platform and the program.

```kotlin
    fun main() {
        val teaforgeRunner = teaforge.platform.initRunner(
            runnerConfig,
            platformArgs,
            program,
            programArgs
        )
    }
```

Then when appropriate you tell the Teaforge instance to step. The `stepProgram` function requires a `CoroutineScope` which is used to launch and manage async effects.

```kotlin
    // Example using a coroutine scope
    runBlocking {
        while(programIsRunning) {
            teaforgeRunner = teaforge.platform.stepProgram(teaforgeRunner, this)
        }
    }
```

The CoroutineScope allows async effects to be launched and their completions to be collected on subsequent steps.


## Best Practices for Platform Implementation

1. **Type Safety**: Leverage Kotlin's type system to expose your platform's capabilities precisely. If your platform's api looks like this:

```kotlin
    fun Sensor.read(port: Int) : Bool
```

but your platform only has six IO ports to read from then having an effect / subscription that takes an Int to identify which sensor to read from would allow the mistake of getting sensor 12 or 25 or 5,121,810. Instead constrain the possible sensor ports like this:

```kotlin
enum class IoPort {
    Zero,
    One,
    Two,
    Three,
    Four,
    Five,
}

sealed interface Subscription<out TMessage> {
    data class IoPortValue<TMessage>(
            val port: IoPort,
            val millisecondsBetweenReads: Int,
            val message: (IoPortStatus) -> TMessage,
    ) : Subscription<TMessage>
}
```

You can also map the result type of your platform to something more meaningful. A result of `Bool` might be ambiguous, instead you could return a more meaningful type.

```kotlin
enum class IoPortStatus {
    Open,
    Closed,
}
```

2. **Error Handling**: Implement robust error handling for hardware interactions. Any errors should be communicated back to a program via a message.


## Debug Logging

TeaForge includes a debug logging system that records program state changes in JSONL format for use with external debuggers. This enables time-travel debugging and state inspection tools.

### Enabling Debug Logging

Configure your `ProgramRunnerConfig` to return `LoggerStatus.Enabled`:

```kotlin
val runnerConfig = ProgramRunnerConfig(
    // ... other config ...
    loggerStatus = {
        LoggerStatus.Enabled(
            DebugLoggingConfig(
                getTimestamp = { System.currentTimeMillis() },
                log = { json -> logFile.appendText(json + "\n") },
                compressionEnabled = true,
            )
        )
    }
)
```

To disable logging, return `LoggerStatus.Disabled`.

### Log Format

The debug log is a JSONL file (one JSON object per line). When compression is disabled, it contains three entry types:

**init** - Logged when the program initializes:
```json
{"type":"init","timestamp":1234567890,"model":{...},"effects":[...]}
```

**update** - Logged for each message processed:
```json
{"type":"update","timestamp":1234567890,"message":{...},"model":{...},"effects":[...]}
```

**subscriptionChange** - Logged when subscriptions are added or removed:
```json
{"type":"subscriptionChange","timestamp":1234567890,"started":[...],"stopped":[...]}
```

### LZ78-Style Compression

When `compressionEnabled = true`, the log uses dictionary-based compression to reduce file sizes. Repeated strings (type names, property keys, string values) are replaced with compact references like `@0`, `@5`.

**Compressed log structure:**

```json
{"type":"header","version":1,"compression":"stringDict"}
{"type":"stringDict","strings":{"0":"_type","1":"Model","2":"swerve","3":"SwerveSubsystem.Model"}}
{"type":"init","timestamp":1234567890,"model":{"@0":"@1","@2":{"@0":"@3"}}}
{"type":"stringDict","strings":{"4":"Message"}}
{"type":"update","timestamp":1234567891,"message":{"@0":"@4"},"model":{"@0":"@1","@2":{"@0":"@3"}}}
```

The compression provides significant file size reduction for logs with repetitive type names and property keys. Dictionary entries (`stringDict`) are emitted before the log entries that first use them.

**What gets compressed:**
- `_type` values (e.g., `"SwerveSubsystem.Model"` → `"@3"`)
- Property names/keys (e.g., `"leftJoystick"` → `"@5"`)
- String values (repeated string constants)

**What stays uncompressed:**
- Top-level entry fields (`type`, `timestamp`, `model`, etc.)
- Numeric values
- Boolean values

### Serialization

The `TeaSerializer` uses Kotlin reflection to serialize program types:

- Data classes include all properties
- Sealed interface variants include a `_type` field with the qualified type name
- Wrapper messages (e.g., `Message.Subsystem(innerMessage)`) include an `_inner` field
- Functions are serialized as references with `_functionId` and `_signature` fields
- Enums are serialized with their qualified name

### Schema

A JSON Schema for the log format is available at `docs/tea-debug-log-schema.json`.


## Using TeaForge as a Dependency

### Maven

To use TeaForge in your Maven project, add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.teaforge</groupId>
    <artifactId>teaforge</artifactId>
    <version>0.1.6</version>
</dependency>
```

### Gradle

To use TeaForge in your Gradle project, add the following to your `build.gradle` file

```groovy
implementation 'io.github.teaforge:teaforge:0.1.6'
```

or `build.gradle.kts` file

```kotlin
implementation("io.github.teaforge:teaforge:0.1.6")
```

## Building

Use `./gradlew build` to build the package.

You can install it locally using `./gradlew publishToMavenLocal`.

To publish the package, manually run the Release workflow from the GitHub Actions tab. The workflow reads the version from `build.gradle.kts` and creates a release with that version.

## Code Formatting

This project uses [ktlint](https://pinterest.github.io/ktlint/) for consistent Kotlin code formatting. Formatting rules are configured in `.editorconfig`.

### Gradle Integration

- Code is automatically formatted before compilation
- A pre-push hook prevents pushing unformatted code (works on macOS, Linux, and Windows)
- To manually format: `./gradlew ktlintFormat` (or `gradlew.bat ktlintFormat` on Windows)
- To check formatting: `./gradlew ktlintCheck`

### Git Hooks Setup

Run `./gradlew build` (or `./gradlew installGitHooks`) to install the pre-push hook. The hook is generated as a platform-appropriate script:
- **macOS/Linux**: Shell script
- **Windows**: Batch script (works with native Git and IntelliJ's Git integration)

### IntelliJ IDEA Setup

For real-time linting while editing, install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint) for IntelliJ IDEA:

1. Go to **Settings** → **Plugins** → **Marketplace**
2. Search for "ktlint" and install the plugin
3. Restart IntelliJ IDEA
4. Go to **Intellij IDEA** -> **Settings** -> **KtLint**
5. Under **Project settings** / **Mode** select "Distract free" and enable "on save"

The plugin will use the `.editorconfig` settings to highlight formatting issues as you type.
