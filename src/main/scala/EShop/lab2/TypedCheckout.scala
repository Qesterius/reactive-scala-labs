package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager
import EShop.lab3.Payment
import java.rmi.UnexpectedException


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
        case CancelCheckout =>
          checkoutTimer.cancel()
          cancelled
        case _ => throw new Exception("Unknown message")
      }
    }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
    Behaviors.receiveMessage {

      case SelectDeliveryMethod(method) =>
        selectingPaymentMethod(timer)
      case CancelCheckout =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        timer.cancel()
        cancelled
      case ExpireCheckout =>
         cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        cancelled
      case _ => throw new Exception("Unknown message")
    }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = 
    Behaviors.receiveMessage {
      case SelectPayment(payment, orderManager) =>
        timer.cancel()
        Behaviors.setup { context =>
          {
            val paymentActor = context.spawn(new Payment( payment, orderManager, context.self).start, "paymentActor")
            orderManager ! OrderManager.ConfirmPaymentStarted(paymentActor)
            val paymentTimer = context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)
            processingPayment(paymentTimer)
          }
        }
      case CancelCheckout =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        timer.cancel()
        cancelled
      case ExpireCheckout =>
        cartActor ! TypedCartActor.ConfirmCheckoutCancelled
        cancelled
      case _ => throw new Exception("Unknown message")

    }


  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = 
      Behaviors.receiveMessage {
        
          case ConfirmPaymentReceived =>
            timer.cancel()
            cartActor ! TypedCartActor.ConfirmCheckoutClosed
            closed
          case CancelCheckout =>
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled
            timer.cancel()
            cancelled
          case ExpirePayment =>
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled
            cancelled
          case _ => throw new Exception("Unknown message") 
      }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receiveMessage {
    case _ => throw new Exception("Unknown message")
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive { (ctx,msg)=> msg match
    {
      case ConfirmPaymentReceived =>
          Behaviors.same
      case _ => throw new Exception("Unknown message "+ msg)
    }}
}
