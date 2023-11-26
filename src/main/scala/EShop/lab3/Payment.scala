package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command
}

class Payment(
  method: String,
  orderManager: ActorRef[OrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receiveMessage {
    case DoPayment =>
      println("Payment actor received DoPayment")
      checkout ! TypedCheckout.ConfirmPaymentReceived
      orderManager ! OrderManager.ConfirmPaymentReceived
      println("Payment actor sent message to OM back")
      
      Behaviors.same
    case _ =>
      Behaviors.unhandled
  }
}
