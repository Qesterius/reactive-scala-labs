package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

/*
(15 points) Cart persistence. Implement cart persistence using the event sourcing pattern. Ensure correct timer recovery.
(15 points) Implement state persistence for PersistentCheckout and test the scenario where during the checkout operation, the app is stopped (e.g. via system.terminate). After the restart, the state should be correctly restored (together with timers).
(10 points) Write additional tests for PersistentCartActor (in particular, take into account actor state recovery).
 */

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup {
    context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId,
        Empty,
        commandHandler(context),
        eventHandler(context)
      )
  }

  def commandHandler(context: ActorContext[Command])
    : (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case GetItems(sender) =>
            sender ! Cart.empty
            Effect.none
          case RemoveItem(item) =>
            Effect.unhandled
          case _ =>
            Effect.unhandled
        }

      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item) =>
            Effect.persist(ItemAdded(item))
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case RemoveItem(item) =>
            if (cart.contains(item)) {
              if (cart.size == 1) Effect.persist(CartEmptied)
              else Effect.persist(ItemRemoved(item))
            } else Effect.none
          case ExpireCart =>
            Effect.persist(CartExpired)
          case StartCheckout(orderManagerRef) =>
            val checkoutActor = context
              .spawn(new TypedCheckout(context.self).start, "checkoutActor")
            orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
            Effect.persist(CheckoutStarted(checkoutActor))
          case _ =>
            Effect.unhandled
        }

      case InCheckout(_) =>
        command match {
          case AddItem(item) =>
            Effect.none
          case GetItems(sender) =>
            sender ! Cart.empty
            Effect.none
          case RemoveItem(item) =>
            Effect.none
          case ExpireCart =>
            Effect.persist(CartExpired)
          case ConfirmCheckoutCancelled =>
            Effect.persist(CheckoutCancelled)
          case ConfirmCheckoutClosed =>
            Effect.persist(CheckoutClosed)
          case _ =>
            Effect.unhandled
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State =
    (state, event) => {
      event match {
        case CheckoutStarted(_) => InCheckout(state.cart)
        case ItemAdded(item) =>
          NonEmpty(state.cart.addItem(item), scheduleTimer(context))
        case ItemRemoved(item) =>
          NonEmpty(state.cart.removeItem(item), scheduleTimer(context))
        case CartEmptied | CartExpired => Empty
        case CheckoutClosed            => Empty
        case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context))
      }
    }

}
