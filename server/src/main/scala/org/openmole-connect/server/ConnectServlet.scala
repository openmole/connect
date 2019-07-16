package org.openmoleconnect.server

import java.nio.ByteBuffer

import org.scalatra._

import scala.concurrent.ExecutionContext.Implicits.global
import boopickle.Default._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scalatags.Text.all._
import scalatags.Text.{all => tags}
import fr.hmil.roshttp.HttpRequest
import fr.hmil.roshttp.body.URLEncodedBody
import fr.hmil.roshttp.response.SimpleHttpResponse

import scala.util.{Failure, Success}
import shared.Data._

//object AutowireServer extends autowire.Server[ByteBuffer, Pickler, Pickler] {
//  override def read[R: Pickler](p: ByteBuffer) = Unpickle[R].fromBytes(p)
//
//  override def write[R: Pickler](r: R) = Pickle.intoBytes(r)
//}

class ConnectServlet(arguments: ConnectServer.ServletArguments) extends ScalatraServlet {

  import monix.execution.Scheduler.Implicits.global

  val basePath = "shared"

  get("/") {
    redirect(connectionRoute)
  }

  get(connectionRoute) {
    contentType = "text/html"

    tags.html(
      tags.head(
        tags.meta(tags.httpEquiv := "Content-Type", tags.content := "text/html; charset=UTF-8"),
        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "css/deps.css"),
        tags.script(tags.`type` := "text/javascript", tags.src := "js/deps.js"),
        tags.script(tags.`type` := "text/javascript", tags.src := "js/demo.js")
      ),
      tags.body(tags.onload := "connection();")
    )
  }


  post(connectionRoute) {
    response.setHeader("Access-Control-Allow-Origin", "*")
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, UPDATE, OPTIONS")
    response.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With")

    val login = params.getOrElse("login", "")
    val password = params.getOrElse("password", "")

    val httpRequest = HttpRequest(s"${arguments.keyCloakServerURL}/auth/realms/test-kube/protocol/openid-connect/token")

    val urlEncodedData = URLEncodedBody(
      "client_id" -> "test-kube",
      "client_secret" -> arguments.secret,
      "response_type" -> "code token",
      "grant_type" -> "password",
      "username" -> login,
      "password" -> password,
      "scope" -> "openid"
    )

    httpRequest.post(urlEncodedData).onComplete({
      case res: Success[SimpleHttpResponse] =>
        res.get.headers.foreach { h =>
          response.addCookie(Cookie(h._1, h._2))
        }
        
      redirect(arguments.openmoleManagerURL)
      case e: Failure[SimpleHttpResponse] => println("Houston, we got a problem!")
        Unauthorized(e)
    })

  }


  //  post(s"/$basePath/*") {
  //    val req = Await.result({
  //      val is = request.getInputStream
  //      val bytes: Array[Byte] = Iterator.continually(is.read()).takeWhile(_ != -1).map(_.asInstanceOf[Byte]).toArray[Byte]
  //      val bb = ByteBuffer.wrap(bytes)
  //      AutowireServer.route[Api](ApiImpl)(
  //        autowire.Core.Request(
  //          basePath.split("/").toSeq ++ multiParams("splat").head.split("/"),
  //          Unpickle[Map[String, ByteBuffer]].fromBytes(bb)
  //        )
  //      )
  //    },
  //      Duration.Inf
  //    )
  //
  //    val data = Array.ofDim[Byte](req.remaining)
  //    req.get(data)
  //    Ok(data)
  //  }

}
