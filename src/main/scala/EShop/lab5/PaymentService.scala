package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.util.{Failure, Success}
import akka.http.scaladsl.settings.ConnectionPoolSettings

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
      method: String,
      payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { context =>
    implicit val system = context.system
    implicit val ec = context.executionContext

    val uri = getURI(method)
   
    //set timeout to 1 second
    val connectionPoolSettings = ConnectionPoolSettings(system)
      .withResponseEntitySubscriptionTimeout(Duration(1, "second"))
    Http()
      .singleRequest(HttpRequest(uri = uri), settings = connectionPoolSettings)
      .onComplete {
        case Success(response) =>
          response.status match {
            case StatusCodes.OK =>
              payment ! PaymentSucceeded
              Behaviors.stopped
            case StatusCodes.RequestTimeout =>
              throw PaymentServerError()
            case _              => throw PaymentServerError()
          }
        case Failure(_) => throw PaymentClientError()
      }

    Behaviors.empty
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
