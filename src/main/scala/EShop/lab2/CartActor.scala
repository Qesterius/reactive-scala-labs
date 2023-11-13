package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps
import java.util.Timer

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable ={
    import context.dispatcher // Import the execution context for scheduling

    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)
  } 
    
  def receive: Receive = empty 

  def empty: Receive ={
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  } 

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      context become nonEmpty(cart.addItem(item), timer)
    case RemoveItem(item) =>
      val newCart = cart.removeItem(item)
      if (newCart.size == 0) context become empty
      else context become nonEmpty(newCart, timer)
    case StartCheckout =>
      context become inCheckout(cart)
    case ExpireCart =>
      context become empty
  } 

  def inCheckout(cart: Cart): Receive = {
    case ConfirmCheckoutCancelled =>
      context become nonEmpty(cart, scheduleTimer)
    case ConfirmCheckoutClosed =>
      context become empty
    case ExpireCart =>
      context become empty
  }

}
