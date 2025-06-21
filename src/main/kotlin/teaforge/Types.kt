package teaforge

import teaforge.utils.Maybe

data class ProgramConfig<TMessage, TModel>(
    val init: (List<String>) -> Pair<TModel, List<TaggedEffect>>,
    val update: (TMessage, TModel) -> Pair<TModel, List<TaggedEffect>>,
    val subscriptions: (TModel) -> List<TaggedSubscription>,
)

data class CapabilityImplementation<TEffect, TSubscription, TSubscriptionState, TMessage>(
    val processEffect: (TEffect) -> Maybe<TMessage>,
    val processSubscription: (TSubscriptionState) -> Pair<TSubscriptionState, Maybe<TMessage>>,
    val startSubscription: (TSubscription) -> TSubscriptionState,
    val stopSubscription: (TSubscriptionState) -> Unit,
)

// Simple capability data class - serves as type witness
data class Capability<TEffect, TSubscription>(val name: String)

// Tagged effect/subscription types for unified handling
sealed interface TaggedEffect {
    val capabilityName: String
    val effect: Any
}

sealed interface TaggedSubscription {
    val capabilityName: String
    val subscription: Any
}

// Registry for type-safe capability handling
sealed interface RegisteredCapability {
    val capabilityName: String
    fun canHandleEffect(effect: TaggedEffect): Boolean
    fun canHandleSubscription(subscription: TaggedSubscription): Boolean
    fun processEffect(effect: TaggedEffect): Maybe<Any>
    fun processSubscription(subscriptionState: Any): Pair<Any, Maybe<Any>>
    fun startSubscription(subscription: TaggedSubscription): Any
    fun stopSubscription(subscriptionState: Any): Unit
}

data class TypedRegisteredCapability<TEffect, TSubscription, TSubscriptionState, TMessage>(
    val capability: Capability<TEffect, TSubscription>,
    val implementation: CapabilityImplementation<TEffect, TSubscription, TSubscriptionState, TMessage>
) : RegisteredCapability {
    override val capabilityName = capability.name
    
    override fun canHandleEffect(effect: TaggedEffect): Boolean =
        effect.capabilityName == capabilityName
    
    override fun canHandleSubscription(subscription: TaggedSubscription): Boolean =
        subscription.capabilityName == capabilityName
    
    override fun processEffect(effect: TaggedEffect): Maybe<Any> {
        @Suppress("UNCHECKED_CAST")
        val typedEffect = effect.effect as TEffect
        val result = implementation.processEffect(typedEffect)
        return when (result) {
            is Maybe.None -> Maybe.None
            is Maybe.Some -> Maybe.Some(result.value as Any)
        }
    }
    
    override fun processSubscription(subscriptionState: Any): Pair<Any, Maybe<Any>> {
        @Suppress("UNCHECKED_CAST")
        val typedState = subscriptionState as TSubscriptionState
        val (newState, maybeMessage) = implementation.processSubscription(typedState)
        val mappedMessage = when (maybeMessage) {
            is Maybe.None -> Maybe.None
            is Maybe.Some -> Maybe.Some(maybeMessage.value as Any)
        }
        return Pair(newState as Any, mappedMessage)
    }
    
    override fun startSubscription(subscription: TaggedSubscription): Any {
        @Suppress("UNCHECKED_CAST")
        val typedSubscription = subscription.subscription as TSubscription
        return implementation.startSubscription(typedSubscription) as Any
    }
    
    override fun stopSubscription(subscriptionState: Any): Unit {
        @Suppress("UNCHECKED_CAST")
        val typedState = subscriptionState as TSubscriptionState
        implementation.stopSubscription(typedState)
    }
}

// Registration function for type-safe capability registration
fun <TEffect, TSubscription, TSubscriptionState, TMessage> registerCapability(
    capability: Capability<TEffect, TSubscription>,
    implementation: CapabilityImplementation<TEffect, TSubscription, TSubscriptionState, TMessage>
): RegisteredCapability = TypedRegisteredCapability(capability, implementation)

// Concrete implementations for tagged effects and subscriptions
data class ConcreteTaggedEffect(
    override val capabilityName: String,
    override val effect: Any
) : TaggedEffect

data class ConcreteTaggedSubscription(
    override val capabilityName: String,
    override val subscription: Any
) : TaggedSubscription

// Extension functions for creating tagged effects and subscriptions
fun <TEffect> Capability<TEffect, *>.createEffect(effect: TEffect): TaggedEffect =
    ConcreteTaggedEffect(this.name, effect as Any)

fun <TSubscription> Capability<*, TSubscription>.createSubscription(subscription: TSubscription): TaggedSubscription =
    ConcreteTaggedSubscription(this.name, subscription as Any)

data class PlatformConfig<TRunnerModel, TProgramModel, TMessage>(
    val initRunner: (List<String>) -> TRunnerModel,
    val startOfUpdateCycle: (TRunnerModel) -> TRunnerModel,
    val endOfUpdateCycle: (TRunnerModel) -> TRunnerModel,
    val processHistoryEntry: (TRunnerModel, HistoryEntry<TMessage, TProgramModel>) -> TRunnerModel,
)

data class ProgramRunnerInstance<
    TMessage,
    TProgramModel,
    TRunnerModel,
>(
    val platformConfig: PlatformConfig<TRunnerModel, TProgramModel, TMessage>,
    val capabilities: List<RegisteredCapability>,
    val programConfig: ProgramConfig<TMessage, TProgramModel>,
    val pendingMessages: List<TMessage>,
    val pendingEffects: List<TaggedEffect>,
    val subscriptions: Map<TaggedSubscription, Any>,
    val runnerModel: TRunnerModel,
    val programModel: TProgramModel,
)

sealed interface HistoryEventSource<TMessage> {
    object ProgramInit : HistoryEventSource<Nothing>

    data class ProgramMessage<TMessage>(
        val message: TMessage,
    ) : HistoryEventSource<TMessage>
}

data class HistoryEntry<TMessage, TProgramModel>(
    val source: HistoryEventSource<TMessage>,
    val programModelAfterEvent: TProgramModel,
)
