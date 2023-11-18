package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCheckout {
  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] =

    Behaviors.setup { context =>
      // Schedule expiration
      val checkoutTimer = context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

      Behaviors.receiveMessage{
      
        case StartCheckout =>
          selectingDelivery(checkoutTimer)

      }
    }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {

      case SelectDeliveryMethod(method) =>
        selectingPaymentMethod(timer)
      case CancelCheckout =>
        cancelled
      case ExpireCheckout =>
        cancelled
    }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = 
    Behaviors.receiveMessage {
      case SelectPayment(payment) =>
        processingPayment(timer)
      case CancelCheckout =>
        cancelled
      case ExpireCheckout =>
        cancelled
    }


  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = 
    Behaviors.receiveMessage {
      case ConfirmPaymentReceived =>
        closed
      case CancelCheckout =>
        cancelled
      case ExpireCheckout =>
        cancelled
    }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case _ => Behaviors.stopped
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case _ => Behaviors.stopped
  }
}
