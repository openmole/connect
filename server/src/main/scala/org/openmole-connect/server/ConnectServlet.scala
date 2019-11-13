package org.openmoleconnect.server

import java.net.URI

import org.openmoleconnect.server.JWT._
import org.scalatra._

import scala.collection.JavaConversions._
import scalatags.Text.all._
import scalatags.Text.{all => tags}

import scala.concurrent.duration._
import shared.Data._
import monix.execution.Scheduler.Implicits.global
import org.apache.commons.io.IOUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.HttpClients


class ConnectServlet(arguments: ConnectServer.ServletArguments) extends ScalatraServlet {


  implicit val secret: JWT.Secret = arguments.secret

  val httpClient = HttpClients.createDefault()

  def uriBuilder(hostIP: String, path: String) = new URIBuilder()
    .setScheme("http")
    .setHost(hostIP)
    .setPort(80)
    .setPath(path)

  def uri(hostIP: String, path: String) = uriBuilder(hostIP, path).build()

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
      tokenData.host.hostIP.map { hip =>
        getFromHip(hip)
        Ok()
      }.getOrElse(NotFound())
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
        multiParams("splat").headOption match {
          case Some(path) =>
            val is = request.getInputStream

            val httpPost = new HttpPost(uri(hip, path))
            httpPost.setEntity(new InputStreamEntity(is))

            val filtred = Seq("Content-Length")
            request.getHeaderNames.filter(n => !filtred.contains(n)).foreach {
              n => httpPost.setHeader(n, request.getHeader(n))
            }

            // TODO: Add timeout
            val forwardResponse = httpClient.execute(httpPost)
            response.setStatus(forwardResponse.getStatusLine.getStatusCode)
            IOUtils.copy(forwardResponse.getEntity.getContent, response.getOutputStream())

            Ok()
          case None => NotFound()
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


  val dateFormat = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", new java.util.Locale("en"))


  private def buildAndAddCookieToHeader(tokenData: TokenData) = {
    response.addHeader(
      "Set-Cookie",
      s"${tokenData.tokenType.cookieKey}=${tokenData.toContent};Expires=${dateFormat.format(tokenData.expirationTime)};HttpOnly;SameSite=Strict")
  }

  private def deleteCookie(tokenData: TokenData) = {
      response.addHeader("Set-Cookie", s"${tokenData.tokenType.cookieKey}=;Expires=${dateFormat.format(0L)}")
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

          val u = uriBuilder(hip, path)
          request.getParameterNames.foreach { pn =>
            u.addParameter(pn, request.getParameter(pn))
          }

          getFromURI(u.build(), requestContentType)
          response
          Ok()
        }.getOrElse(NotFound())
      }
    }
  }


  def getFromURI(uri: URI, requestContentType: String): Int = {
    val httpGet = new HttpGet(uri)

    httpGet.setHeader("Content-Type", requestContentType)
    val forwardResponse = httpClient.execute(httpGet)

    response.setStatus(forwardResponse.getStatusLine.getStatusCode)
    IOUtils.copy(forwardResponse.getEntity.getContent, response.getOutputStream())
  }


  def getFromHip(hip: String): Int = {
    getFromURI(uri(hip, ""), "html")
  }

  get("/*") {
    getResource(request.uri.getPath, request.contentType.getOrElse("html"))
  }

  get("/") {
    connectionAppRedirection
  }

  get("/logout") {
    withAccesToken { accessTokenData =>
      withRefreshToken {refreshTokenData =>
        deleteCookie(refreshTokenData)
        deleteCookie(accessTokenData)
        redirect("/")
      }
      redirect("/")
    }
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