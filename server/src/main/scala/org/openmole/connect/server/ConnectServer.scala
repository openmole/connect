package org.openmole.connect.server

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import dev.profunktor.auth.*
import dev.profunktor.auth.jwt.*
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{CloseableHttpResponse, HttpDelete, HttpGet, HttpPost}
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.HttpClients
import org.http4s.*
import org.http4s.blaze.server.*
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.implicits.*
import org.http4s.multipart.Multipart
import org.http4s.server.*
import org.openmole.connect.server.ServerContent.connectionError
import org.openmole.connect.shared.Data
import org.typelevel.ci.CIString
import pdi.jwt.*

import java.io.{BufferedOutputStream, File, FileOutputStream}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import fs2.io.*
import io.circe.yaml
import io.circe.generic.auto.*

object ConnectServer:

  object Config:
    case class Kube(off: Option[Boolean] = None, storageClassName: Option[String] = None)

  case class Config(salt: String, secret: String, kube: Config.Kube)
  def read(file: File): Config =
    import better.files.*
    yaml.parser.parse(file.toScala.contentAsString).toTry.get.as[Config].toTry.get


  def apply(config: Config) =
    val k8s = K8sService(config.kube.storageClassName)
    new ConnectServer(config, k8s)


class ConnectServer(config: ConnectServer.Config, k8s: K8sService):
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  given jwtSecret: JWT.Secret = JWT.Secret(config.secret)
  given salt: DB.Salt = DB.Salt(config.salt)

  val httpClient =
    HttpClients.
      custom().
      disableAutomaticRetries().
      disableRedirectHandling().
      setDefaultSocketConfig(Utils.socketConfig()).
      build()


  def start() =
    DB.initDB()

    //println(K8sService.listPods)
    //println(K8sService.deployOpenMOLE("8888888", "latest", "5Gi", config.kube.storageClassName))

    val serverRoutes: HttpRoutes[IO] =
      HttpRoutes.of:
        case req @ GET -> Root =>
          Authentication.authenticatedUser(req) match
            case Some(user) if user.role == DB.admin => ServerContent.ok("admin();")
            case Some(user) => ServerContent.ok("user();")
            case None =>
              val uri = Uri.unsafeFromString(s"/${Data.connectionRoute}")
              TemporaryRedirect(Location(uri))
        case req @ GET -> Root / Data.connectionRoute =>
          //println("auth " + Authentication.isAuthenticated(req) + " " + Authentication.isAdmin(req))
          ServerContent.ok("connection(false);")
        case req @ POST -> Root / Data.connectionRoute =>
          req.decode[UrlForm]: r =>
            r.getFirst("Email") zip r.getFirst("Password") match
              case Some((email, password)) =>

                DB.uuid(email, password) match
                  case Some(uuid) =>
                    val token = JWT.TokenData(email, DB.salted(password))
                    val expirationDate = HttpDate.unsafeFromEpochSecond(token.expirationTime / 1000)
                    ServerContent.ok("user();").map(_.addCookie(ResponseCookie(Authentication.authorizationCookieKey, JWT.TokenData.toContent(token), expires = Some(expirationDate))))
                  case None => ServerContent.connectionError
              case None => ServerContent.connectionError

        case req if req.uri.path.startsWith(Root / Data.userAPIRoute) =>
          ServerContent.authenticated(req): user =>
            val impl = UserAPIImpl(user, k8s)
            val userAPI = new UserAPIRoutes(impl)
            val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
            val apiReq = req.withUri(req.uri.withPath(apiPath))
            userAPI.routes.apply(apiReq).getOrElseF(NotFound())

        case req if req.uri.path.startsWith(Root / "openmole") && (req.uri.path.segments.drop(1).nonEmpty || req.uri.path.endsWithSlash) =>
          ServerContent.authenticated(req): user =>
            K8sService.podInfo(user.uuid).flatMap(_.podIP) match
              case Some(ip) =>
                val openmoleURL = s"http://$ip:80"
                val openmoleURI = java.net.URI(openmoleURL)

                def authority =
                  Uri.Authority(host = Uri.Host.unsafeFromString(openmoleURI.getHost), port = Some(openmoleURI.getPort))

                val uri =
                  def path = Path(req.uri.path.segments.drop(1))
                  def scheme = Uri.Scheme.unsafeFromString(openmoleURI.getScheme)
                  req.uri.copy(authority = Some(authority), scheme = Some(scheme)).withPath(path).toString

                def forwadedHeaders(req: Request[IO]) =
                  val filteredHeaders = Set(CIString("Content-Length"))

                  req.headers.headers.filter(h => !filteredHeaders.contains(h.name))

                def response(forwardResponse: CloseableHttpResponse) =
                  def forwardStatus = Status.fromInt(forwardResponse.getStatusLine.getStatusCode).toTry.get

                  Ok(fs2.io.readInputStream(IO(forwardResponse.getEntity.getContent), 10240).onFinalize(IO(forwardResponse.close()))).map: r =>
                    val hs: Seq[Header.ToRaw] = forwardResponse.getAllHeaders.map(h => h.getName -> h.getValue: Header.ToRaw).toSeq
                    r.putHeaders(hs: _*).withStatus(forwardStatus)

                req.method match
                  case p @ POST =>
                    val res = fs2.io.toInputStreamResource(req.body).use: is =>
                      val post = new HttpPost(uri)
                      post.setEntity(new InputStreamEntity(is))
                      forwadedHeaders(req).foreach(h => post.setHeader(h.name.toString, h.value))
                      response(httpClient.execute(post))
                    res
                  case p @ GET =>
                    val get = new HttpGet(uri)
                    forwadedHeaders(req).foreach(h => get.setHeader(h.name.toString, h.value))
                    response(httpClient.execute(get))
                  case p @ DELETE =>
                    val delete = new HttpDelete(uri)
                    forwadedHeaders(req).foreach(h => delete.setHeader(h.name.toString, h.value))
                    response(httpClient.execute(delete))
                  case r => NotImplemented(s"Method ${r.method.name} is not supported by openmole-connect yet")
              case None => NotFound("OpenMOLE instance is not running")

        case req @ GET -> Root / "openmole" => SeeOther(Location(Uri.unsafeFromString("openmole/")))

    val routes = serverRoutes <+> ServerContent.contentRoutes

    val httpApp = Router("/" -> routes).orNotFound


    def errorHandler: ServiceErrorHandler[IO] = r => t =>
      def stack = ExceptionUtils.getStackTrace(t)
      InternalServerError("Error in openmole-connect:\n" + stack)

    val server =
      BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(httpApp)
        .withIdleTimeout(Duration.Inf)
        .withResponseHeaderTimeout(Duration.Inf)
        .withServiceErrorHandler(errorHandler)
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

  val connectionError =
    Forbidden.apply(ServerContent.someHtml("connection(true);").render)
      .map(_.withContentType(`Content-Type`(MediaType.text.html)))

  def authenticated[T](req: Request[IO])(using JWT.Secret)(f: DB.User => T) =
    Authentication.authenticatedUser(req) match
      case Some(user) => f(user)
      case None =>
        Forbidden.apply(ServerContent.someHtml("connection(false);").render)
          .map(_.withContentType(`Content-Type`(MediaType.text.html)))

  def ok(jsCall: String) =
    Ok.apply(ServerContent.someHtml(jsCall).render).map(_.withContentType(`Content-Type`(MediaType.text.html)))

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
