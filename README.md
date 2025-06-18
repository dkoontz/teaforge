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
data class PlatformModel<TMessage, TModel>(
    // Track message history if needed
    val messageHistory: List<HistoryEntry<TMessage, TModel>>,
    
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

```kotlin
fun <TMessage, TModel> processEffect(
        model: PlatformSpecificModel<TMessage, TModel>,
        effect: Effect<TMessage>,
): Pair<PlatformSpecificModel<TMessage, TModel>, Maybe<TMessage>> {
    return when (effect) {
        is Effect.ReadFile -> {
            // do platform specific File IO logic here, updating the platform model as needed, then return that updated model along with a message
            Pair(updatedModel, Maybe.Some(effect.message(result)))
        }
        is Effect.SetPwmMotorSpeed -> {
            // do logic to communicate with PWM motor, in this case there's nothing to update in the model and no message to return
            Pair(model, Maybe.None)
        }
    }
}
```

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

Then when appropriate you tell the Teaforge instance to step.

```kotlin
    while(programIsRunning) {
        teaforge.platform.stepProgram(teaforgeInstance)
    }
```


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


## Using TeaForge as a Dependency

### Maven

To use TeaForge in your Maven project, add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.teaforge</groupId>
    <artifactId>teaforge</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

To use TeaForge in your Gradle project, add the following to your `build.gradle` file

```groovy
implementation 'io.github.teaforge:teaforge:0.1.0'
```

or `build.gradle.kts` file

```kotlin
implementation("io.github.teaforge:teaforge:0.1.0")
```

## Building

Use `./mvnw clean package` to build the package.

You can install it locally using `./mvnw install`.

To publish, push your changes then trigger the "Publish to GitHub Packages" workflow under GitHub Actions.

*Note:* You will need to have the `GITHUB_TOKEN` environment variable set.
