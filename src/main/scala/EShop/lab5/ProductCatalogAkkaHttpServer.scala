package EShop.lab5

import EShop.lab5.ProductCatalogAkkaHttpServer.Greetings
import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json.{
  DefaultJsonProtocol,
  JsString,
  JsValue,
  JsonFormat,
  RootJsonFormat
}

import java.net.URI
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{
  DefaultJsonProtocol,
  JsString,
  JsValue,
  JsonFormat,
  RootJsonFormat
}
import spray.json.JsArray

object ProductCatalogAkkaHttpServer {
  case class Name(name: String)
  case class Greetings(greetings: String)
  case class SearchRequest(brand: String, productKeyWords: List[String])
  case class SearchResponse(items: List[ProductCatalog.Item])
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit lazy val nameFormat =
    jsonFormat1(ProductCatalogAkkaHttpServer.Name)
  implicit lazy val greetingsFormat =
    jsonFormat1(ProductCatalogAkkaHttpServer.Greetings)
  implicit lazy val searchFormat
    : RootJsonFormat[ProductCatalogAkkaHttpServer.SearchRequest] =
    jsonFormat2(ProductCatalogAkkaHttpServer.SearchRequest)
  implicit lazy val responseFormat
    : RootJsonFormat[ProductCatalogAkkaHttpServer.SearchResponse] =
    jsonFormat1(ProductCatalogAkkaHttpServer.SearchResponse)

  //custom formatter just for example
  implicit lazy val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue =
      JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }
  implicit val itemFormat: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(
    ProductCatalog.Item)

}

class ProductCatalogAkkaHttpServer extends JsonSupport {
  implicit val system =
    ActorSystem[Nothing](Behaviors.empty, "ProductCatalogAkkaHttp")
  implicit val timeout_duration = Duration.fromNanos(1000000)
  implicit val timeout: Timeout = Timeout(timeout_duration)

  def routes: Route = {
    path("search") {
      get {
        entity(as[ProductCatalogAkkaHttpServer.SearchRequest]) {
          searchRequest =>
            val productCatalogListingFuture =
              system.receptionist.ask[Receptionist.Listing](
                askReplyTo =>
                  Receptionist.Find(ProductCatalog.ProductCatalogServiceKey,
                                    askReplyTo)
              )

            val productCatalogListingResult: Receptionist.Listing =
              Await.result(productCatalogListingFuture, timeout_duration)

            val productCatalogInstances =
              productCatalogListingResult.getAllServiceInstances(
                ProductCatalog.ProductCatalogServiceKey)
            var itemsResult: ProductCatalog.Items = null
            productCatalogInstances.forEach(productCatalog => {
              val itemsFuture = productCatalog.ask(
                (ref: ActorRef[ProductCatalog.Ack]) =>
                  ProductCatalog.GetItems(searchRequest.brand,
                                          searchRequest.productKeyWords,
                                          ref)
              )
              itemsResult = Await
                .result(itemsFuture, timeout_duration)
                .asInstanceOf[ProductCatalog.Items]
            })
            val response =
              ProductCatalogAkkaHttpServer.SearchResponse(itemsResult.items)
            val marshalledOutput = responseFormat.write(response)
            complete(marshalledOutput)
        }
      }
    }
  }

  def start(port: Int) = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    Await.ready(system.whenTerminated, Duration.Inf)
  }

  object ProductCatalogAkkaHttpServerApp extends App {
    new ProductCatalogAkkaHttpServer().start(9000)
  }

}
