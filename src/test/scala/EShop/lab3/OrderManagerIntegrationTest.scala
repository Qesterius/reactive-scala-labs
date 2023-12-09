package EShop.lab3

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import EShop.lab2.TypedCheckout
import EShop.lab2.TypedCartActor

class OrderManagerIntegrationTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import OrderManager._


  override implicit val timeout: Timeout = 1.second

  implicit val scheduler: Scheduler = testKit.scheduler

  def sendMessage(
    orderManager: ActorRef[OrderManager.Command],
    message: ActorRef[Any] => OrderManager.Command
  ): Unit = {
    import akka.actor.typed.scaladsl.AskPattern.Askable
    orderManager.ask[Any](message).mapTo[OrderManager.Ack].futureValue shouldBe Done
  }

  it should "supervise whole order process" in {
    val orderManager = testKit.spawn(new OrderManager().start).ref
    val senderProbe = testKit.createTestProbe[OrderManager.Ack]()


    orderManager !  AddItem("rollerblades", senderProbe.ref)
    senderProbe.expectMessage(Done)

    orderManager ! Buy(senderProbe.ref)
    senderProbe.expectMessage(Done)

    orderManager ! SelectDeliveryAndPaymentMethod("paypal", "inpost", senderProbe.ref)
    senderProbe.expectMessage(Done)

    orderManager ! Pay(senderProbe.ref)
    senderProbe.expectMessage(Done)
  }

}
