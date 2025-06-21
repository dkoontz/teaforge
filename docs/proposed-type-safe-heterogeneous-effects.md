  ## First Attempt

  Alternative Approach: Heterogeneous Lists with Type 
  Witnesses

  Here's a compile-time safe approach using Kotlin's
  type system more cleverly:

  1. Capability Type Witnesses

  // Each CapabilitySet becomes a type witness
  sealed interface CapabilityId<TEffect, TSubscription> 
  {
      val name: String
  }

  object HttpCapability : CapabilityId<HttpEffect,
  Nothing> {
      override val name = "HTTP"
  }

  object PostgresCapability :
  CapabilityId<PostgresEffect, PostgresSubscription> {
      override val name = "PostgreSQL"
  }

  2. Typed Capability Pairs

  data class CapabilityPair<TEffect, TSubscription, 
  TSubscriptionState>(
      val id: CapabilityId<TEffect, TSubscription>,
      val implementation:
  CapabilityImplementation<TEffect, TSubscription,
  TSubscriptionState, *>
  )

  3. Heterogeneous Capability List

  // Sealed interface for type-safe heterogeneous 
  storage
  sealed interface RegisteredCapability {
      fun <R> handle(handler: CapabilityHandler<R>): R?
  }

  data class TypedCapability<TEffect, TSubscription, 
  TSubscriptionState>(
      val pair: CapabilityPair<TEffect, TSubscription,
  TSubscriptionState>
  ) : RegisteredCapability {
      override fun <R> handle(handler: 
  CapabilityHandler<R>): R? =
          handler.handle(pair)
  }

  interface CapabilityHandler<R> {
      fun <TEffect, TSubscription, TSubscriptionState> 
  handle(
          pair: CapabilityPair<TEffect, TSubscription, 
  TSubscriptionState>
      ): R?
  }

  4. Effect/Subscription Union with Type Tags

  sealed interface TaggedEffect<out TEffect> {
      val capabilityId: CapabilityId<*, *>
      val effect: TEffect
  }

  data class HttpTaggedEffect(
      override val effect: HttpEffect
  ) : TaggedEffect<HttpEffect> {
      override val capabilityId = HttpCapability
  }

  data class PostgresTaggedEffect(
      override val effect: PostgresEffect
  ) : TaggedEffect<PostgresEffect> {
      override val capabilityId = PostgresCapability
  }

  Benefits of This Approach:

  1. Compile-time safety: Type witnesses ensure
  capability IDs match their effect/subscription types
  2. No runtime reflection: All routing happens through
  sealed interfaces and pattern matching
  3. Minimal boilerplate: Users just pair capability IDs
   with implementations
  4. Type erasure boundary: The heterogeneous list hides
   complexity while maintaining safety
  5. Extensible: New capabilities can be added without
  modifying core types

  Usage Pattern:

  val capabilities = listOf(
      TypedCapability(CapabilityPair(HttpCapability,
  httpImplementation)),
      TypedCapability(CapabilityPair(PostgresCapability,
   postgresImplementation))
  )

  // Effects become tagged automatically through factory
   methods
  val httpEffect = HttpCapability.createEffect(HttpGet("
  https://api.example.com"))
  val dbEffect =
  PostgresCapability.createEffect(QueryDatabase("SELECT 
  * FROM users"))

  This maintains compile-time safety while avoiding user
   boilerplate for effect union types and routing logic.

╭───────────────────────────────────────────────────────────╮
│ Ready to code?                                            │
│                                                           │
│ Here is Claude's plan:                                    │
│ ╭───────────────────────────────────────────────────────╮ │
│ │ Type-Safe Multi-Capability System                     │ │
│ │                                                       │ │
│ │ Goal: Support multiple CapabilitySets with distinct   │ │
│ │ types while maintaining compile-time safety and       │ │
│ │ minimal boilerplate.                                  │ │
│ │                                                       │ │
│ │ Core Strategy: Type Witnesses + Heterogeneous Lists   │ │
│ │                                                       │ │
│ │ 1. Create Capability Type Witnesses                   │ │
│ │   - Define CapabilityId<TEffect, TSubscription>       │ │
│ │ sealed interface                                      │ │
│ │   - Each capability becomes a singleton object        │ │
│ │ implementing this interface                           │ │
│ │   - Provides compile-time type relationship between   │ │
│ │ ID and effect/subscription types                      │ │
│ │ 2. Implement Heterogeneous Capability Storage         │ │
│ │   - RegisteredCapability sealed interface for         │ │
│ │ type-safe heterogeneous lists                         │ │
│ │   - TypedCapability<TEffect, TSubscription,           │ │
│ │ TSubscriptionState> wrapper                           │ │
│ │   - CapabilityHandler<R> interface for type-safe      │ │
│ │ processing                                            │ │
│ │ 3. Create Tagged Effect/Subscription System           │ │
│ │   - TaggedEffect<TEffect> and                         │ │
│ │ TaggedSubscription<TSubscription> sealed interfaces   │ │
│ │   - Each capability provides tagged implementations   │ │
│ │   - Maintains type safety while enabling unified      │ │
│ │ handling                                              │ │
│ │ 4. Update Runner Types                                │ │
│ │   - Replace single TEffect/TSubscription with tagged  │ │
│ │ union types                                           │ │
│ │   - Use capability handlers for type-safe dispatch to │ │
│ │  implementations                                      │ │
│ │   - Maintain compile-time guarantees throughout       │ │
│ │ processing pipeline                                   │ │
│ │ 5. Provide Factory Methods                            │ │
│ │   - Capability IDs offer factory methods for creating │ │
│ │  tagged effects/subscriptions                         │ │
│ │   - Users work with strongly-typed                    │ │
│ │ capability-specific APIs                              │ │
│ │   - No manual union type construction required        │ │
│ │                                                       │ │
│ │ Benefits: Compile-time safety, no reflection, minimal │ │
│ │  boilerplate, extensible design                       │ │
│ ╰───────────────────────────────────────────────────────╯ │

## Second attempt

  Function-Based Type Witnesses

  Instead of interfaces, we can use factory functions as
   type witnesses:

  // Simple data classes for capabilities
  data class Capability<TEffect, TSubscription>(val
  name: String)

  // Factory functions serve as type witnesses
  fun httpCapability(): Capability<HttpEffect, Nothing>
  = Capability("HTTP")
  fun postgresCapability(): Capability<PostgresEffect,
  PostgresSubscription> = Capability("PostgreSQL")

  Registration Through Function Parameters

  The key insight is using function parameters to 
  capture types:

  // Registration function captures types through 
  parameters
  fun <TEffect, TSubscription, TSubscriptionState> 
  registerCapability(
      capability: Capability<TEffect, TSubscription>,
      implementation: CapabilityImplementation<TEffect, 
  TSubscription, TSubscriptionState, TMessage>
  ): RegisteredCapability = // ... implementation

  // Usage becomes very clean:
  val httpReg = registerCapability(httpCapability(),
  httpImplementation)
  val dbReg = registerCapability(postgresCapability(),
  postgresImplementation)

  Effect Creation Through Capability Functions

  // Extension functions on capabilities for effect 
  creation
  fun <TEffect> Capability<TEffect, 
  *>.createEffect(effect: TEffect): TaggedEffect =
      TaggedEffectImpl(this, effect)

  // Usage:
  val httpEffect = httpCapability().createEffect(HttpGet
  ("https://api.com"))
  val dbEffect = postgresCapability().createEffect(Query
  Database("SELECT..."))

  Builder Pattern for Runner Setup

  // Fluent builder using function-based registration
  fun programRunner() = ProgramRunnerBuilder()

  class ProgramRunnerBuilder {
      fun <TEffect, TSubscription, TState> 
  withCapability(
          capability: Capability<TEffect, 
  TSubscription>,
          implementation: 
  CapabilityImplementation<TEffect, TSubscription, 
  TState, TMessage>
      ): ProgramRunnerBuilder = // ... 
  }

  // Clean usage:
  val runner = programRunner()
      .withCapability(httpCapability(),
  httpImplementation)
      .withCapability(postgresCapability(),
  postgresImplementation)
      .build()

  Benefits of Function-Based Approach:

  1. No interfaces needed - just simple data classes and
   functions
  2. Type inference - Kotlin infers the relationship
  between capability and implementation
  3. Compile-time safety - mismatched types won't
  compile
  4. Clean API - feels natural and idiomatic
  5. Zero ceremony - no need to implement interfaces or
  create objects

  The functions act as "type constructors" that carry
  the type information forward, while the actual runtime
   behavior is handled by simple data structures and the
   type system enforces correctness.

╭──────────────────────────────────────────────────╮
│ Ready to code?                                   │
│                                                  │
│ Here is Claude's plan:                           │
│ ╭──────────────────────────────────────────────╮ │
│ │ Function-Based Type-Safe Multi-Capability    │ │
│ │ System                                       │ │
│ │                                              │ │
│ │ Goal: Use plain functions as type witnesses  │ │
│ │ to achieve compile-time safety with minimal  │ │
│ │ ceremony.                                    │ │
│ │                                              │ │
│ │ Core Strategy: Functions as Type             │ │
│ │ Constructors                                 │ │
│ │                                              │ │
│ │ 1. Create Simple Capability Data Class       │ │
│ │   - data class Capability<TEffect,           │ │
│ │ TSubscription>(val name: String)             │ │
│ │   - No interfaces needed - pure data         │ │
│ │ 2. Implement Factory Functions as Type       │ │
│ │ Witnesses                                    │ │
│ │   - fun httpCapability():                    │ │
│ │ Capability<HttpEffect, Nothing>              │ │
│ │   - fun postgresCapability():                │ │
│ │ Capability<PostgresEffect,                   │ │
│ │ PostgresSubscription>                        │ │
│ │   - Functions carry type information through │ │
│ │  return types                                │ │
│ │ 3. Registration Through Generic Function     │ │
│ │ Parameters                                   │ │
│ │   - fun <TEffect, TSubscription, TState>     │ │
│ │ registerCapability(...)                      │ │
│ │   - Type parameters captured from capability │ │
│ │  and implementation arguments                │ │
│ │   - Kotlin's type inference ensures matching │ │
│ │  types                                       │ │
│ │ 4. Effect Creation via Extension Functions   │ │
│ │   - fun <TEffect> Capability<TEffect,        │ │
│ │ *>.createEffect(effect: TEffect):            │ │
│ │ TaggedEffect                                 │ │
│ │   - Clean, type-safe effect creation         │ │
│ │   - No manual union type construction        │ │
│ │ 5. Builder Pattern for Runner Setup          │ │
│ │   - ProgramRunnerBuilder.withCapability(capa │ │
│ │ bility, implementation)                      │ │
│ │   - Fluent API using function-based          │ │
│ │ registration                                 │ │
│ │   - All type checking happens at compile     │ │
│ │ time                                         │ │
│ │ 6. Update Core Types                         │ │
│ │   - Replace generic TEffect/TSubscription    │ │
│ │ with tagged union types                      │ │
│ │   - Maintain heterogeneous capability list   │ │
│ │ internally                                   │ │
│ │   - Route effects/subscriptions using        │ │
│ │ capability metadata                          │ │
│ │                                              │ │
│ │ Benefits: No interfaces, clean API,          │ │
│ │ compile-time safety, zero ceremony,          │ │
│ │ leverages Kotlin's type inference            │ │
│ ╰──────────────────────────────────────────────╯ │

## Version 3
Immutable List-Based Registration

  Instead of a builder, we use a simple list:

  // Registration returns a 
  RegisteredCapability
  fun <TEffect, TSubscription, TState> 
  registerCapability(
      capability: Capability<TEffect, 
  TSubscription>,
      implementation: 
  CapabilityImplementation<TEffect, 
  TSubscription, TState, TMessage>
  ): RegisteredCapability =
  TypedRegisteredCapability(capability,
  implementation)

  // Usage - pure immutable list
  val capabilities = listOf(
      registerCapability(httpCapability(),
  httpImplementation),
      registerCapability(postgresCapability(),
  postgresImplementation)
  )

  // Pass to runner
  val runner = initRunner(platformConfig,
  capabilities, program, args)

  Tagged Union Types Explained

  The "tagged union" refers to a separate type 
  hierarchy for effects and subscriptions that
  carries capability metadata:

  // Tagged effect - carries both the effect 
  AND which capability it belongs to
  sealed interface TaggedEffect {
      val capabilityName: String
      val effect: Any // The actual effect 
  object
  }

  data class HttpTaggedEffect(
      val httpEffect: HttpEffect
  ) : TaggedEffect {
      override val capabilityName = "HTTP"
      override val effect = httpEffect
  }

  data class PostgresTaggedEffect(
      val postgresEffect: PostgresEffect
  ) : TaggedEffect {
      override val capabilityName =
  "PostgreSQL"
      override val effect = postgresEffect
  }

  Effect Routing Mechanism

  Here's how an effect gets routed to the
  correct implementation:

  // In Internal.kt - effect processing
  fun processEffect(
      effect: TaggedEffect, 
      capabilities: List<RegisteredCapability>
  ): Maybe<TMessage> {

      // Find the capability that can handle 
  this effect
      val matchingCapability =
  capabilities.firstOrNull { capability ->
          capability.canHandle(effect)
      }

      return
  matchingCapability?.processEffect(effect) ?:
  Maybe.None
  }

  // RegisteredCapability interface
  sealed interface RegisteredCapability {
      val capabilityName: String
      fun canHandle(effect: TaggedEffect):
  Boolean
      fun processEffect(effect: TaggedEffect):
  Maybe<TMessage>
      // Similar for subscriptions...
  }

  // Concrete implementation
  data class TypedRegisteredCapability<TEffect,
   TSubscription, TState>(
      val capability: Capability<TEffect,
  TSubscription>,
      val implementation:
  CapabilityImplementation<TEffect,
  TSubscription, TState, TMessage>
  ) : RegisteredCapability {

      override val capabilityName =
  capability.name

      override fun canHandle(effect: 
  TaggedEffect): Boolean =
          effect.capabilityName ==
  capabilityName

      override fun processEffect(effect: 
  TaggedEffect): Maybe<TMessage> {
          // Safe cast because canHandle() 
  verified the type
          @Suppress("UNCHECKED_CAST")
          val typedEffect = effect.effect as
  TEffect
          return
  implementation.processEffect(typedEffect)
      }
  }

  Effect Creation Flow

  // User creates effects through capability 
  functions
  fun <TEffect> Capability<TEffect, 
  *>.createEffect(effect: TEffect):
  TaggedEffect =
      when (name) {
          "HTTP" -> HttpTaggedEffect(effect as
  HttpEffect)
          "PostgreSQL" ->
  PostgresTaggedEffect(effect as
  PostgresEffect)
          else -> throw
  IllegalArgumentException("Unknown capability:
   $name")
      }

  // Usage in program
  val effects = listOf(
      httpCapability().createEffect(HttpGet("ht
  tps://api.com")),
      postgresCapability().createEffect(QueryDa
  tabase("SELECT..."))
  )

  Type Safety Guarantees

  1. Registration: registerCapability() ensures
   capability and implementation types match
  2. Effect Creation: Extension functions
  ensure effects match their capability types
  3. Routing: canHandle() ensures only
  compatible implementations process effects
  4. Processing: Type casting is safe because
  routing verified compatibility

  The key insight is that compile-time safety
  comes from the registration and creation
  functions, while runtime routing uses the
  capability name tags to find the right
  handler. The unsafe cast is actually safe
  because the type system guaranteed the
  relationship at registration time.

╭──────────────────────────────────────────────────╮
│ Ready to code?                                   │
│                                                  │
│ Here is Claude's plan:                           │
│ ╭──────────────────────────────────────────────╮ │
│ │ Immutable Function-Based Type-Safe           │ │
│ │ Multi-Capability System                      │ │
│ │                                              │ │
│ │ Goal: Use immutable lists and function-based │ │
│ │  type witnesses for compile-time safe        │ │
│ │ multi-capability support.                    │ │
│ │                                              │ │
│ │ Core Design:                                 │ │
│ │                                              │ │
│ │ 1. Immutable Registration System             │ │
│ │   - registerCapability(capability,           │ │
│ │ implementation) returns RegisteredCapability │ │
│ │   - Pass List<RegisteredCapability> to       │ │
│ │ runner (no builder pattern)                  │ │
│ │   - Pure functional approach with no         │ │
│ │ mutation                                     │ │
│ │ 2. Tagged Union Types for                    │ │
│ │ Effects/Subscriptions                        │ │
│ │   - TaggedEffect sealed interface with       │ │
│ │ capability name + effect object              │ │
│ │   - Concrete implementations like            │ │
│ │ HttpTaggedEffect, PostgresTaggedEffect       │ │
│ │   - Separate from RegisteredCapability -     │ │
│ │ this is the unified effect type              │ │
│ │ 3. Type-Safe Effect Creation                 │ │
│ │   - Extension functions on                   │ │
│ │ Capability<TEffect, TSubscription>           │ │
│ │   - capability.createEffect(effect) returns  │ │
│ │ properly tagged effect                       │ │
│ │   - Compile-time guarantee that effect       │ │
│ │ matches capability type                      │ │
│ │ 4. Runtime Effect Routing                    │ │
│ │   -                                          │ │
│ │ RegisteredCapability.canHandle(TaggedEffect) │ │
│ │  checks capability name match                │ │
│ │   - RegisteredCapability.processEffect(Tagge │ │
│ │ dEffect) safely casts and processes          │ │
│ │   - Type safety guaranteed by                │ │
│ │ registration-time type checking              │ │
│ │ 5. Update Core Types                         │ │
│ │   - ProgramRunnerInstance uses               │ │
│ │ TaggedEffect/TaggedSubscription as           │ │
│ │ TEffect/TSubscription                        │ │
│ │   - Replace capabilityImplementations:       │ │
│ │ List<CapabilityImplementation> with          │ │
│ │ capabilities: List<RegisteredCapability>     │ │
│ │   - Update Internal.kt to use capability     │ │
│ │ routing instead of iteration                 │ │
│ │ 6. Key Implementation Details                │ │
│ │   - Safe casting in processEffect() because  │ │
│ │ canHandle() verified compatibility           │ │
│ │   - Capability name serves as runtime type   │ │
│ │ tag for routing                              │ │
│ │   - Compile-time type relationships          │ │
│ │ preserved through function signatures        │ │
│ │                                              │ │
│ │ Benefits: Immutable, no builders,            │ │
│ │ compile-time safety, clean routing,          │ │
│ │ extensible                                   │ │
│ ╰──────────────────────────────────────────────╯ │
