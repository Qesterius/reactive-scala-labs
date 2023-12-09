package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {
  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case StartCheckout =>
          val checkoutTimer = ctx.scheduleOnce(checkoutTimerDuration, ctx.self, ExpireCheckout)
          selectingDelivery(checkoutTimer)
        case CancelCheckout =>
          cancelled
        case _ => Behaviors.unhandled
      }
    }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {
      case SelectDeliveryMethod(method) =>
        selectingPaymentMethod(timer)
      case CancelCheckout =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled //?
        timer.cancel()
        cancelled
      case ExpireCheckout =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled //?
        cancelled
      case _ => Behaviors.unhandled
    }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case SelectPayment(payment, orderManager) =>
          timer.cancel()
          val paymentActor = ctx.spawn(new Payment(payment, orderManager, ctx.self).start, "paymentActor")
          orderManager ! OrderManager.ConfirmPaymentStarted(paymentActor)
          val paymentTimer = ctx.scheduleOnce(paymentTimerDuration, ctx.self, ExpirePayment)
          processingPayment(paymentTimer)
        case CancelCheckout =>
          timer.cancel()
          cancelled
        case ExpireCheckout =>
          cancelled
        case _ => Behaviors.unhandled

      }
    }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {
      case ConfirmPaymentReceived =>
        timer.cancel()
        cartActor ! TypedCartActor.ConfirmCheckoutClosed
        closed
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case ExpirePayment =>
        cancelled
      case _ => Behaviors.unhandled
    }

  def cancelled: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (_, message) =>
      message match {
        case _ => Behaviors.stopped
      }
    }

  def closed: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (_, message) =>
      message match {
        case _ => Behaviors.stopped
      }
    }
}
