package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds


  def scheduleTimer: Cancellable = {
    import context.dispatcher // Import the execution context for scheduling

    context.system.scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  }

  def receive: Receive = selectingDelivery(scheduleTimer.start)

  def selectingDelivery(timer: Cancellable): Receive = {
    case StartCheckout =>
      context become selectingDelivery(scheduleTimer)
    case SelectDeliveryMethod(method) =>
      context become selectingPaymentMethod(scheduleTimer)
    case CancelCheckout =>
      context become cancelled
    case ExpireCheckout =>
      context become cancelled
  }
  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case SelectPayment(payment) =>
      context become processingPayment(scheduleTimer)
    case CancelCheckout =>
      context become cancelled
    case ExpireCheckout =>
      context become cancelled
  }
  def processingPayment(timer: Cancellable): Receive = {
    case ConfirmPaymentReceived =>
      context become closed
    case CancelCheckout =>
      context become cancelled
    case ExpireCheckout =>
      context become cancelled
  }
  def cancelled: Receive = {
    case _ => log.info("Checkout is cancelled")
  }

  def closed: Receive = {
    case _ => log.info("Checkout is closed")
  }

}
