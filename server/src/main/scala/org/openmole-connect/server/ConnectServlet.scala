package org.openmoleconnect.server

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import fr.hmil.roshttp.Method
import fr.hmil.roshttp.body.{ByteBufferBody, PlainTextBody}
import fr.hmil.roshttp.response.StreamHttpResponse
import javax.servlet.http.HttpServletRequest
import org.scalatra._

import scala.concurrent.Await
import scala.collection.JavaConversions._

//import scala.concurrent.ExecutionContext.Implicits.global
import org.openmoleconnect.server
import scalatags.Text.all._
import scalatags.Text.{all => tags}
import scala.concurrent.duration._
import shared.Data._

import fr.hmil.roshttp.HttpRequest
import monix.execution.Scheduler.Implicits.global
import scala.util.{Failure, Success}
import fr.hmil.roshttp.response.SimpleHttpResponse

class ConnectServlet(arguments: ConnectServer.ServletArguments) extends ScalatraServlet {


  val basePath = "shared"
  implicit val secret: JWT.Secret = arguments.secret

  val allowHeaders = Seq(
    ("Access-Control-Allow-Origin", "*"),
    ("Access-Control-Allow-Methods", "POST, GET, PUT, UPDATE, OPTIONS"),
    ("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With")
  )

  def waitForGet(httpRequest: HttpRequest): SimpleHttpResponse = {
    Await.result(
      httpRequest.get()
      , 1 minute)
  }

  def waitForPost(httpRequest: HttpRequest): SimpleHttpResponse = {
    Await.result(
      httpRequest.withMethod(Method.POST).send()
      , 1 minute)
  }


  val forwardRequest = HttpRequest()
    .withProtocol(fr.hmil.roshttp.Protocol.HTTP)
    .withURL(arguments.publicAdress)
    .withPath("")

  def headers(request: HttpServletRequest) = request.getHeaderNames.map { hn => hn -> request.getHeader(hn) }.toSeq

  def proxyRequest = {
    val fR = waitForGet(forwardRequest.withHeaders((headers(request)): _*).withHeaders(allowHeaders: _*))
    Ok(fR.body, fR.headers)

  }

  def withConnection(action: HttpServletRequest => ActionResult): Serializable = {
    val isValid = Authentication.isValid(request)

    isValid match {
      case false => connectionHtml
      case true => action(request)
    }
  }

  def connectionAppRedirection = {
    val isValid = Authentication.isValid(request)

    isValid match {
      case false => connectionHtml
      case true => proxyRequest
    }
  }

  notFound {
    wrongWay
  }


  get("/*") {
    NotFound()
  }

  // OM instance requests
  post("/*", Authentication.isValid(request)) {
    println("post /*")
    multiParams("splat").headOption match {
      case Some(path) =>
        val is = request.getInputStream
        val bytes: Array[Byte] = Iterator.continually(is.read()).takeWhile(_ != -1).map(_.asInstanceOf[Byte]).toArray[Byte]
        val bb = ByteBuffer.wrap(bytes)

        val req = waitForPost(
          forwardRequest.withPath(s"/$path").withHeader("Content-Type", "application/octet-stream").withBody(ByteBufferBody(bb))
        )

        if (req.statusCode < 400) Ok(req.body)
        else NotFound()

      case None => NotFound()
    }
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
            redirect("/")
          }
          else connectionHtml
        }
      case true =>
        //Already logged
        redirect("/")
    }
  }


  private def getResource(path: String, requestContentType: String) = {
    val localPath = new java.io.File(arguments.resourceBase, request.uri.getPath)

    if (localPath.exists()) {
      contentType = requestContentType
      response.setHeader("Content-Disposition", "attachment; filename=" + localPath.getName)
      localPath
    } else {
      Ok(
        waitForGet(
          forwardRequest.withHeader("Content-Type", requestContentType).withPath(path)
        ).body
      )
    }
  }

  get("/js/*.*") {
    getResource(request.uri.getPath, "application/javascript")
  }

  get("/css/*.*") {
    getResource(request.uri.getPath, "text/css")
  }

  get("/img/*.*") {
    getResource(request.uri.getPath, request.contentType.getOrElse(""))
  }

  get("/fonts/*.*") {
    getResource(request.uri.getPath, request.contentType.getOrElse(""))
  }

  get("/") {
    connectionAppRedirection
  }


  def connectionHtml = {
    contentType = "text/html"
    tags.html(
      tags.head(
        tags.meta(tags.httpEquiv := "Content-Type", tags.content := "text/html; charset=UTF-8"),
        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "css/deps.css"),
        Seq("connect-deps.js", "connect.js").map { jf => tags.script(tags.`type` := "text/javascript", tags.src := s"js/$jf") }
      ),
      tags.body(tags.onload := "connection();")
    )
  }

  def wrongWay = {
    contentType = "text/html"
    tags.html(
      tags.head(
        tags.div(
          tags.div(
            display.flex,
            flexDirection.column,
            justifyContent.center,
            alignItems.center,
            height := 300)(
            tags.div(
              tags.img(src := "img/logo.svg",
                paddingTop := 300,
                width := 600),
              div(paddingLeft := 180)("Wrong way buddy. Are you lost ?")
            )
          )
        )
      )
    )
  }


}