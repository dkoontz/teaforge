# Type-Safe Multi-Capability Usage Example

This example demonstrates how to use the new type-safe multi-capability system in TeaForge.

## 1. Define Your Capability Types

```kotlin
// HTTP Capability
sealed interface HttpEffect {
    data class Get(val url: String) : HttpEffect
    data class Post(val url: String, val body: String) : HttpEffect
}

// No subscriptions for HTTP
typealias HttpSubscription = Nothing

// Database Capability
sealed interface DatabaseEffect {
    data class Query(val sql: String) : DatabaseEffect
    data class Insert(val table: String, val data: Map<String, Any>) : DatabaseEffect
}

sealed interface DatabaseSubscription {
    data class TableChanged(val tableName: String) : DatabaseSubscription
}
```

## 2. Create Capability Instances

```kotlin
// Factory functions that serve as type witnesses
fun httpCapability(): Capability<HttpEffect, HttpSubscription> =
    Capability("HTTP")

fun databaseCapability(): Capability<DatabaseEffect, DatabaseSubscription> =
    Capability("Database")
```

## 3. Implement Platform-Specific Handlers

```kotlin
// HTTP implementation for your platform
val httpImplementation = CapabilityImplementation<HttpEffect, HttpSubscription, Unit, AppMessage>(
    processEffect = { effect ->
        when (effect) {
            is HttpEffect.Get -> {
                // Make HTTP GET request
                val response = makeHttpRequest(effect.url)
                Maybe.Some(AppMessage.HttpResponse(response))
            }
            is HttpEffect.Post -> {
                // Make HTTP POST request
                val response = makeHttpPostRequest(effect.url, effect.body)
                Maybe.Some(AppMessage.HttpResponse(response))
            }
        }
    },
    processSubscription = { state -> state to Maybe.None },
    startSubscription = { subscription -> Unit },
    stopSubscription = { }
)

// Database implementation for your platform
val databaseImplementation = CapabilityImplementation<DatabaseEffect, DatabaseSubscription, DatabaseState, AppMessage>(
    processEffect = { effect ->
        when (effect) {
            is DatabaseEffect.Query -> {
                val results = executeQuery(effect.sql)
                Maybe.Some(AppMessage.QueryResults(results))
            }
            is DatabaseEffect.Insert -> {
                val success = insertData(effect.table, effect.data)
                Maybe.Some(AppMessage.InsertComplete(success))
            }
        }
    },
    processSubscription = { state ->
        // Check for table changes
        val changes = checkTableChanges(state)
        val newMessage = if (changes.isNotEmpty()) {
            Maybe.Some(AppMessage.TableChanged(changes))
        } else {
            Maybe.None
        }
        state to newMessage
    },
    startSubscription = { subscription ->
        when (subscription) {
            is DatabaseSubscription.TableChanges ->
                DatabaseState(subscription.tableName)
        }
    },
    stopSubscription = { state -> cleanupTableWatch(state) }
)
```

## 4. Register Capabilities

```kotlin
// Type-safe registration - compile-time ensures types match
val capabilities = listOf(
    registerCapability(httpCapability(), httpImplementation),
    registerCapability(databaseCapability(), databaseImplementation)
)
```

## 5. Use in Your Program

```kotlin
// Your program config
val program = ProgramConfig<AppMessage, AppModel>(
    init = { args ->
        val initialModel = AppModel()
        val initialEffects = listOf(
            // Type-safe effect creation
            httpCapability().createEffect(HttpEffect.Get("https://api.example.com/data")),
            databaseCapability().createEffect(DatabaseEffect.Query("SELECT * FROM users"))
        )
        initialModel to initialEffects
    },
    update = { message, model ->
        when (message) {
            is AppMessage.HttpResponse -> {
                // Handle HTTP response
                val newEffect = databaseCapability().createEffect(
                    DatabaseEffect.Insert("cache", mapOf("data" to message.data))
                )
                model.copy(lastResponse = message.data) to listOf(newEffect)
            }
            is AppMessage.QueryResults -> {
                // Handle query results
                model.copy(users = message.results) to emptyList()
            }
            // ... other message handlers
        }
    },
    subscriptions = { model ->
        listOf(
            // Type-safe subscription creation
            databaseCapability().createSubscription(
                DatabaseSubscription.TableChanges("users")
            )
        )
    }
)
```

## 6. Initialize and Run

```kotlin
// Initialize the runner with your capabilities
val runner = initRunner(
    platformConfig = yourPlatformConfig,
    capabilities = capabilities,
    runnerArgs = emptyList(),
    program = program,
    programArgs = emptyList()
)

// Run the program
val nextRunner = stepProgram(runner)
```

## Key Benefits

1. **Compile-time Safety**: Mismatched capability types won't compile
2. **Zero Boilerplate**: No need to write union types or routing logic
3. **Clean API**: Effects and subscriptions are created through capability functions
4. **Extensible**: Easy to add new capabilities without changing existing code
5. **Type Inference**: Kotlin's type system handles the complexity automatically

## How It Works

- Each capability is defined with its own effect and subscription types
- `registerCapability()` ensures the implementation matches the capability types
- `createEffect()` and `createSubscription()` create tagged instances
- The runtime routes tagged effects/subscriptions to the correct implementations
- Type safety is maintained throughout the entire pipeline
