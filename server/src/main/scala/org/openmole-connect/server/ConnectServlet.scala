package org.openmoleconnect.server

import java.net.URLEncoder
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
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._

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

    println("login "+ login)
    println("pw " + password)

    println(s"${arguments.keyCloakServerURL}/auth/realms/test-kube/protocol/openid-connect/token")

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

        println("res "+res.get.body)

        val accessToken = "access_token"
        val idToken = "id_token"
        val refreshToken = "refresh_token"
        val expireIn = "expires_in"
        val refreshExpireIn = "refresh_expires_in"
        val path = "/auth/realms/test-kube/"

        def addCookie (jsonKey: String, json: JValue, cookieOptions: CookieOptions) = {
          val jsonValue = fromJson(jsonKey, json)
          //val jsonValue = (json \ jsonKey).values.toString
          println("jsonValue "+ jsonKey)
          response.addCookie(Cookie(jsonKey, jsonValue)(cookieOptions))
          //Cookie(jsonKey, jsonValue)(cookieOptions)
        }

        def fromJson (jsonKey : String, json : JValue) = {
          URLEncoder.encode(compact(render(json \ jsonKey)), "UTF-8")
        }

        val json = parse(res.get.body)

        /*List(accessToken, idToken, refreshToken, expireIn, refreshExpireIn).foreach{ jk =>
          addCookie(jk, json)
        }*/

        println("test1")
        addCookie(accessToken, json, CookieOptions(path=path, maxAge=fromJson(expireIn, json).toInt, httpOnly=true))

        println("test2")

        val headers = Map(
          "Access-Control-Allow-Origin"-> "*",
          "Access-Control-Allow-Methods"-> "POST, GET, PUT, UPDATE, OPTIONS",
        "Access-Control-Allow-Headers"-> "Content-Type, Accept, X-Requested-With"
        )

        Ok(res.get.body, headers)
     // redirect(arguments.openmoleManagerURL)
      case e: Failure[SimpleHttpResponse] => println("haaa, we got a problem!")
        println("e " + e)
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
