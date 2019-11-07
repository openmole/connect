package org.openmoleconnect.server

import java.nio.{ByteBuffer, ByteOrder}

import fr.hmil.roshttp.Method
import fr.hmil.roshttp.body.{ByteBufferBody, PlainTextBody}
import javax.servlet.http.HttpServletRequest
import org.openmoleconnect.server.JWT._
import org.scalatra._

import scala.concurrent.Await
import scala.collection.JavaConversions._
import scalatags.Text.all._
import scalatags.Text.{all => tags}

import scala.concurrent.duration._
import shared.Data._
import boopickle.Default._
import fr.hmil.roshttp.HttpRequest
import monix.execution.Scheduler.Implicits.global
import fr.hmil.roshttp.response.SimpleHttpResponse
import javax.servlet.ServletInputStream
import org.apache.commons.io.IOUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.HttpClients
import org.openmoleconnect.server.DB._


class ConnectServlet(arguments: ConnectServer.ServletArguments) extends ScalatraServlet {


  implicit val secret: JWT.Secret = arguments.secret

  val allowHeaders = Seq(
    ("Access-Control-Allow-Origin", "*"),
    ("Access-Control-Allow-Methods", "POST, GET, PUT, UPDATE, OPTIONS"),
    ("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With")
  )

  def waitForGet(httpRequest: HttpRequest) = {
    Await.result(
      httpRequest.get()
      , 1 minute)
  }

  def waitForPost(httpRequest: HttpRequest) = {
    Await.result(
      httpRequest.withMethod(Method.POST).send(),
      1 minute)
  }

  def waitForPost2(httpRequest: HttpRequest) = {
    Await.result(
      httpRequest.withMethod(Method.POST).send(),
      1 minute)
  }


  val baseForwardRequest = HttpRequest()
    .withProtocol(fr.hmil.roshttp.Protocol.HTTP)

  def headers(request: HttpServletRequest) = request.getHeaderNames.map { hn => hn -> request.getHeader(hn) }.toSeq

  def proxyRequest(hostIP: Option[String]) = {
    hostIP.map { hip =>
      println("HIP " + hip)
      withForwardRequest(hip) { forwardRequest =>
        val req = forwardRequest.withHeaders((headers(request)): _*).withHeaders(allowHeaders: _*)
        val fR = waitForGet(req)
        Ok(fR.body, fR.headers)
      }
    }
  }

  //def withForwardRequest(hostIP: String)(action: HttpRequest => ActionResult): ActionResult = {
  def withForwardRequest(hostIP: String)(action: HttpRequest => ActionResult): ActionResult = {
    action(baseForwardRequest.withHost(hostIP).withPort(80).withPath(""))
  }

  def withAccesToken(action: TokenData => ActionResult): Serializable = {
    Authentication.tokenData(request, TokenType.accessToken) match {
      case Some(tokenData: TokenData) => action(tokenData)
      case None =>
        Authentication.isValid(request, TokenType.refreshToken) match {
          case true =>
            withRefreshToken { refreshToken =>
              val tokenData = TokenData.accessToken(refreshToken.host, refreshToken.login)
              buildAndAddCookieToHeader(tokenData)
              action(tokenData)
            }
          case false => connectionHtml
        }
    }
  }

  def withRefreshToken(action: TokenData => ActionResult): Serializable = {
    Authentication.tokenData(request, TokenType.refreshToken) match {
      case Some(tokenData: TokenData) => action(tokenData)
      case None => connectionHtml
    }
  }

  def connectionAppRedirection = {
    withAccesToken { tokenData =>
      proxyRequest(tokenData.host.hostIP).getOrElse(NotFound())
    }
  }

  notFound {
    wrongWay
  }


  get("/*") {
    NotFound()
  }

  // OM instance requests
  post("/*") {
    withAccesToken { tokenData =>
      tokenData.host.hostIP.map { hip =>
        // withForwardRequest(hip) { forwardRequest =>
        val forwardRequest = baseForwardRequest.withHost(hip).withPort(80).withPath("")
        multiParams("splat").headOption match {
          case Some(path) =>
            val is = request.getInputStream

            println("request: " + s"http://$hip/$path")
            val uri = new URIBuilder()
              .setScheme("http")
              .setHost(hip)
              .setPort(80)
              .setPath(path)
              .build()

            val httpPost = new HttpPost(uri)
            httpPost.setEntity(new InputStreamEntity(is))


            val filtred = Seq("Content-Length")
            request.getHeaderNames.filter(n => !filtred.contains(n)).foreach {
              n => httpPost.setHeader(n, request.getHeader(n))
            }

            // Add timeout
            val client = HttpClients.createDefault()

            val forwardResponse = client.execute(httpPost)

            response.setStatus(forwardResponse.getStatusLine.getStatusCode)
            IOUtils.copy(forwardResponse.getEntity.getContent, response.getOutputStream())

            Ok()
          case None => NotFound()
          //  }
        }
      }.getOrElse(NotFound())
    }
  }

  case class ListFilesData(list: Seq[TreeNodeData], nbFilesOnServer: Int)

  case class DirData(isEmpty: Boolean)

  case class TreeNodeData(
                           name: String,
                           dirData: Option[DirData],
                           size: Long,
                           time: Long
                         )


  post(connectionRoute) {
    Authentication.isValid(request, TokenType.accessToken) match {
      case false =>
        val login = params.getOrElse("login", "")

        // Get login and password from the post request parameters
        val password = params.getOrElse("password", "")
        if (login.isEmpty || password.isEmpty) connectionHtml

        //Build cookie with JWT token if login/password are valid and redirect to the openmole manager url
        else {
          DB.uuid(DB.User(DB.Login(login), DB.Password(password))) match {
            case Some(uuid) =>
              val host = Host(uuid, K8sService.hostIP(uuid))
              buildAndAddCookieToHeader(TokenData.accessToken(host, DB.Login(login)))
              buildAndAddCookieToHeader(TokenData.refreshToken(host, DB.Login(login)))
              redirect("/")
            case _ => connectionHtml
          }
        }
      case true =>
        //Already logged
        redirect("/")
    }
  }


  private def buildAndAddCookieToHeader(tokenData: TokenData) = {

    val format = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", new java.util.Locale("en"))

    response.addHeader(
      "Set-Cookie",
      s"${tokenData.tokenType.cookieKey}=${tokenData.toContent};Expires=${format.format(tokenData.expirationTime)};HttpOnly;SameSite=Strict")
  }

  private def getResource(path: String, requestContentType: String) = {
    val localPath = new java.io.File(arguments.resourceBase, request.uri.getPath)

    if (localPath.exists()) {
      contentType = requestContentType
      response.setHeader("Content-Disposition", "attachment; filename=" + localPath.getName)
      localPath
    } else {
      withAccesToken { tokenData =>
        tokenData.host.hostIP.map { hip =>
          withForwardRequest(hip) { forwardRequest =>
            Ok(
              waitForGet(
                forwardRequest.withHeader("Content-Type", requestContentType).withPath(s"$path")
              ).body
            )
          }
        }.getOrElse(NotFound())
      }
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
        Seq("connect-deps.js", "connect.js").map {
          jf => tags.script(tags.`type` := "text/javascript", tags.src := s"js/$jf ")
        }
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