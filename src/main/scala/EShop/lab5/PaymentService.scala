package EShop.lab5

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.util.{Failure, Success}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import scala.concurrent.duration.Duration

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
    
    val http = Http(context.system)
    val uri = getURI(method)

    val result = http
      .singleRequest(HttpRequest(uri = uri))

    context.pipeToSelf(result) {
      case Success(value) => value
      case Failure(e)     => throw e
    }

    Behaviors.receiveMessage {
      case resp @ HttpResponse(StatusCodes.OK, _, _, _) =>
        payment ! PaymentSucceeded
        Behaviors.same
      case resp @ HttpResponse(code, _, _, _) =>
        code match {
          case StatusCodes.RequestTimeout => throw PaymentServerError()
          case _                          => throw PaymentClientError()
        }
      }
   }


  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
