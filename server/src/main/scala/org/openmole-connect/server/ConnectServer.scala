package org.openmoleconnect.server

import cats.effect.*
import org.http4s.blaze.server.*
import org.http4s.implicits.*
import org.http4s.server.Router

import java.io.File
import cats.effect.IO
import cats.implicits.*
import dev.profunktor.auth.*
import dev.profunktor.auth.jwt.*
import pdi.jwt.*
import org.http4s.*
import org.http4s.headers.*
import org.http4s.dsl.io.*
import cats.effect.unsafe.IORuntime


class ConnectServer(secret: String, kubeOff: Boolean):
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  //def dataFile = new File(data)

  def start() =
    case class AuthUser(id: Long, name: String)

//    enum TokenType:
//      case access, refresh

    //case class TokenData(email: DB.Email, host: String, issued: Long, expirationTime: Long, tokenType: TokenType)


    //JWT.TokenData.accessToken("test.org", "test@test.org")


    // i.e. retrieve user from database
    val authenticate: JwtToken => JwtClaim => IO[Option[AuthUser]] =
      token => claim =>
        println(claim)
        AuthUser(123L, "joe").some.pure[IO]

    val jwtAuth = JwtAuth.hmac("53cr3t", JwtAlgorithm.HS256)
    val middleware = JwtAuthMiddleware[IO, AuthUser](jwtAuth, authenticate)

//    println(jwtAuth.secretKey.value)
//    println(Jwt.encode(JwtClaim("{123}"), jwtAuth.secretKey.value, jwtAuth.jwtAlgorithms.head))
    // curl -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.ezEyM30.aK9o7JLIL1aMpvhCq7tY5FNoG7mdfJ1bm2paUFM6L2k" --cookie "USER_TOKEN=Yes" localhost:8080/admin
    // https://blog.rockthejvm.com/scala-http4s-authentication/
    
    val openRoute: HttpRoutes[IO] =
      HttpRoutes.of:
        case GET -> Root =>
          Ok.apply(ServerContent.someHtml("connection();").render)
            .map(_.withContentType(`Content-Type`(MediaType.text.html)))

        case req @ POST -> Root / shared.Data.connectionRoute =>
          println(req.params)
//          val cookie = req.headers.get[org.http4s.headers.Cookie]
//          val email =
//          DB.uuid(DB.Email(email), DB.Password(password))
          Ok("connect")

            //.map(_.addCookie(ResponseCookie("name", "test")))

    val adminRoute: AuthedRoutes[AuthUser, IO] = AuthedRoutes.of:
      case req @ GET -> Root / "admin" as user =>
        println("cookies " + req.req.headers.get[org.http4s.headers.Cookie])
        println(user)
        Ok(s"$user")


    //val connectAPI = new ConnectAPIImpl
    val routes = openRoute <+> ServerContent.contentRoutes <+> middleware(adminRoute)

    val httpApp = Router("/" -> routes).orNotFound

    val server =
      BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(httpApp)
        .resource.allocated.unsafeRunSync()._2

    server

object ServerContent:
  def webapp = "application/webapp"

  val contentRoutes: HttpRoutes[IO] = HttpRoutes.of:
    case request@GET -> Root / "js" / path =>
      val f = new File(webapp, s"js/$path")
      StaticFile.fromFile(f, Some(request)).getOrElseF(NotFound())
    case request@GET -> Root / "css" / path =>
      val f = new File(webapp, s"css/$path")
      StaticFile.fromFile(f, Some(request)).getOrElseF(NotFound())
    case request@GET -> Root / "img" / path =>
      val f = new File(webapp, s"img/$path")
      StaticFile.fromFile(f, Some(request)).getOrElseF(NotFound())

  def someHtml(jsCall: String) =
    import scalatags.Text.all.*

    //contentType = "text/html"
    html(
      head(
        meta(httpEquiv := "Content-Type", content := "text/html; charset=UTF-8"),
        //link(rel := "stylesheet", `type` := "text/css", href := "css/deps.css"),
        link(rel := "stylesheet", `type` := "text/css", href := "css/style-connect.css"),
        Seq(s"connect-deps.js", "connect.js").map(jf => script(`type` := "text/javascript", src := s"js/$jf "))
      ),
      body(
        onload := jsCall,
        div(id := "appContainer")
      )
    )

//package org.openmoleconnect.server
//
//import java.net.URI
//import java.util
//
//import javax.servlet.ServletContext
//import org.eclipse.jetty.server.{Server, ServerConnector}
//import org.eclipse.jetty.webapp.WebAppContext
//import org.scalatra.LifeCycle
//import org.scalatra.servlet.ScalatraListener
//
//object ConnectServer {
//  val servletArguments = "servletArguments"
//
//  case class ServletArguments(secret: String, resourceBase: java.io.File, kubeOff: Boolean)
//
//}
//
//class ConnectBootstrap extends LifeCycle {
//  override def init(context: ServletContext) = {
//    val args = context.get(ConnectServer.servletArguments).get.asInstanceOf[ConnectServer.ServletArguments]
//    context mount(new ConnectServlet(args), "/*")
//  }
//}
//
//class ConnectServer(secret: String, kubeOff: Boolean) {
//
//
//  def start() = {
//
//    val server = new Server()
//    val connector = new ServerConnector(server)
//    connector.setPort(8080)
//    server.addConnector(connector)
//
//    val startingContext = new WebAppContext()
//    startingContext.setResourceBase("application/target/webapp")
//    startingContext.setAttribute(ConnectServer.servletArguments, ConnectServer.ServletArguments(secret, new java.io.File(new URI(startingContext.getResourceBase)), kubeOff))
//    startingContext.setInitParameter(ScalatraListener.LifeCycleKey, classOf[ConnectBootstrap].getCanonicalName)
//    startingContext.setContextPath("/")
//    startingContext.addEventListener(new ScalatraListener)
//    server.setHandler(startingContext)
//
//    server.stop()
//    server.setHandler(startingContext)
//    server.start()
//  }
//}
