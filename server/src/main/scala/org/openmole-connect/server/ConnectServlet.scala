package org.openmoleconnect.server

import java.nio.ByteBuffer
import org.scalatra._
import scala.concurrent.ExecutionContext.Implicits.global
import org.openmoleconnect.server

import scalatags.Text.all._
import scalatags.Text.{all => tags}
import shared.Data._

class ConnectServlet(arguments: ConnectServer.ServletArguments) extends ScalatraServlet {

  val basePath = "shared"
  implicit val secret: JWT.Secret = arguments.secret

  get("/") {

    response.setHeader("Access-Control-Allow-Origin", "*")
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, UPDATE, OPTIONS")
    response.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With")

    Authentication.isValid(request) match {
      case false => redirect(connectionRoute)
      case true => redirect(arguments.openmoleManagerURL)
    }
  }

  get(connectionRoute) {
    connectionHtml
  }

    post(connectionRoute) {
      Authentication.isValid(request) match {
        case false =>
          // Get login and password from the post request parameters
          val login = params.getOrElse("login", "")
          val password = params.getOrElse("password", "")
          if (login.isEmpty || password.isEmpty) connectionHtml

          //Build cookie with JWT token if login/password are valid and redirect to the openmole manager url
          else {
            if (DB.exists(DB.User(login, password))) {
              val tokenAndContext = JWT.writeToken(login)
              response.setHeader(
                "Set-Cookie",
                s"${Authentication.openmoleCookieKey}=${tokenAndContext.token};Expires=${tokenAndContext.expiresTime};HttpOnly;SameSite=Strict")
              redirect(arguments.openmoleManagerURL)
            }
            else connectionHtml
          }
        case true =>
          //Already logged
          redirect(arguments.openmoleManagerURL)
      }
    }

    def connectionHtml = {
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
  }