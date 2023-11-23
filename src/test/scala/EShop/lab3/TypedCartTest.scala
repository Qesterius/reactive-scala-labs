package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import com.typesafe.config.ConfigFactory
import org.slf4j.event.Level

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly synchronously" in {
      val testKit = BehaviorTestKit(new TypedCartActor().start)
      val inbox = TestInbox[Cart]()

      testKit.run(TypedCartActor.AddItem("item1"))
      testKit.run(TypedCartActor.GetItems(inbox.ref))
      val cart = inbox.receiveMessage()
      cart.items should contain("item1")
      cart.size shouldBe 1
  }
  it should "add item properly asynchronously" in {

    val cart = testKit.spawn(new TypedCartActor().start)
    cart ! AddItem("item1")

    val probe = testKit.createTestProbe[Any]()
    cart ! GetItems(probe.ref)
    probe.expectMessage(Cart(Seq("item1")))
  }

  it should "be empty after adding and removing the same item synch" in {
      val testKit = BehaviorTestKit(new TypedCartActor().start)
      val inbox = TestInbox[Cart]()
      testKit.run(TypedCartActor.AddItem("item1", inbox.ref))
      testKit.run(TypedCartActor.RemoveItem("item1", inbox.ref))
      testKit.run(TypedCartActor.GetItems(inbox.ref))
      inbox.expectMessage(Cart.empty)

  }
  it should "be empty after adding and removing the same item asynch" in {
    val cart = testKit.spawn(new TypedCartActor().start)
    cart ! AddItem("item1")
    cart ! RemoveItem("item1")

    val probe = testKit.createTestProbe[Any]()
    //send GetItems command to cart with sender and expect cart to be empty
    cart ! GetItems(probe.ref)

    probe.expectMessage(Cart.empty)
  }

  it should "start checkout synch" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[OrderManager.Command]()
    testKit.run(TypedCartActor.AddItem("item1", inbox.ref))
    testKit.run(TypedCartActor.StartCheckout(inbox.ref))
    inbox.expectMessage(_: OrderManager.ConfirmCheckoutStarted)
  }
  it should "start checkout asynch" in {
    val cart = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Any]()
    cart ! AddItem("item1")
    cart ! StartCheckout(probe.ref)
    probe.expectMessageType[OrderManager.ConfirmCheckoutStarted]
  }
}
