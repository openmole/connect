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

import java.util.concurrent.TimeUnit

object ConnectServer:

  object Config:
    case class Kube(storageClassName: Option[String] = None)
    case class OpenMOLE(versionHistory: Int)

  case class Config(salt: String, secret: String, kube: Config.Kube, openmole: Config.OpenMOLE)

  def read(file: File): Config =
    import better.files.*
    yaml.parser.parse(file.toScala.contentAsString).toTry.get.as[Config].toTry.get


  def apply(config: Config) =
    val k8s = K8sService(config.kube.storageClassName)
    new ConnectServer(config, k8s)


  object IPCache:

    import com.google.common.cache.*

    case class Cached(user: DB.User, ip: String)

    def apply(): IPCache = CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[DB.UUID, Cached]].
      expireAfterAccess(30, TimeUnit.MINUTES).
      build[DB.UUID, Cached]

  type IPCache = com.google.common.cache.Cache[DB.UUID, IPCache.Cached]

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

  val ipCache = ConnectServer.IPCache()

  def start() =
    DB.initDB()
    
    val serverRoutes: HttpRoutes[IO] =
      HttpRoutes.of:
        case req@GET -> Root =>
          Authentication.authenticatedUser(req) match
            case Some(user) if user.role == DB.admin => ServerContent.ok("admin();").map(ServerContent.addJWTToken(user.uuid, user.password))
            case Some(user) => ServerContent.ok("user();").map(ServerContent.addJWTToken(user.uuid, user.password))
            case None => ServerContent.redirect(s"/${Data.connectionRoute}")

        case req@GET -> Root / Data.connectionRoute => ServerContent.ok("connection(false);")

        case req@POST -> Root / Data.registerRoute =>
          req.decode[UrlForm]: r =>
            r.getFirst("Email") zip r.getFirst("Password") zip r.getFirst("Name") zip r.getFirst("First name") zip r.getFirst("Institution") match
              case Some(((((email, password), name), firstName), institution)) =>
                DB.addRegisteringUser(DB.RegisterUser(name, firstName, email, DB.salted(password), institution))
                ServerContent.ok("connection(false)")
              case None => ServerContent.connectionError
        case req@POST -> Root / Data.connectionRoute =>
          req.decode[UrlForm]: r =>
            r.getFirst("Email") zip r.getFirst("Password") match
              case Some((email, password)) =>
                DB.user(email, password) match
                  case Some(user) => ServerContent.redirect("/").map(ServerContent.addJWTToken(user.uuid, DB.salted(password)))
                  case None => ServerContent.connectionError
              case None => ServerContent.connectionError

        case req @ GET -> Root / Data.disconnectRoute =>
          val cookie = ResponseCookie(Authentication.authorizationCookieKey, "expired", expires = Some(HttpDate.MinValue))
          ServerContent.redirect(s"/${Data.connectionRoute}").map(_.addCookie(cookie))

        case req @ GET -> Root / Data.impersonateRoute =>
          ServerContent.authenticated(req): admin =>
            req.params.get("uuid") match
              case Some(uuid) =>
                if DB.User.isAdmin(admin)
                then
                  DB.userFromUUID(uuid) match
                    case Some(user) => ServerContent.redirect(s"/").map(ServerContent.addJWTToken(user.uuid, user.password))
                    case None => NotFound(s"User not found ${uuid}")
                else
                  Forbidden(s"User ${admin.name} is not admin")
              case None => BadRequest("No uuid parameter found")

        case req if req.uri.path.startsWith(Root / Data.userAPIRoute) =>
          ServerContent.authenticated(req): user =>
            val impl = UserAPIImpl(user, k8s, config.openmole.versionHistory)
            val userAPI = new UserAPIRoutes(impl)
            val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
            val apiReq = req.withUri(req.uri.withPath(apiPath))
            userAPI.routes.apply(apiReq).getOrElseF(NotFound())

        case req if req.uri.path.startsWith(Root / Data.adminAPIRoute) =>
          ServerContent.authenticated(req): user =>
            if DB.User.isAdmin(user)
            then
              val impl = AdminAPIImpl(k8s)
              val adminAPI = new AdminAPIRoutes(impl)
              val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
              val apiReq = req.withUri(req.uri.withPath(apiPath))
              adminAPI.routes.apply(apiReq).getOrElseF(NotFound())
            else
              Forbidden(s"User ${user.name} is not admin")

        case req if req.uri.path.startsWith(Root / Data.openMOLERoute) && (req.uri.path.segments.drop(1).nonEmpty || req.uri.path.endsWithSlash) =>
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
                  case p@POST =>
                    val res = fs2.io.toInputStreamResource(req.body).use: is =>
                      val post = new HttpPost(uri)
                      post.setEntity(new InputStreamEntity(is))
                      forwadedHeaders(req).foreach(h => post.setHeader(h.name.toString, h.value))
                      response(httpClient.execute(post))
                    res
                  case p@GET =>
                    val get = new HttpGet(uri)
                    forwadedHeaders(req).foreach(h => get.setHeader(h.name.toString, h.value))
                    response(httpClient.execute(get))
                  case p@DELETE =>
                    val delete = new HttpDelete(uri)
                    forwadedHeaders(req).foreach(h => delete.setHeader(h.name.toString, h.value))
                    response(httpClient.execute(delete))
                  case r => NotImplemented(s"Method ${r.method.name} is not supported by openmole-connect yet")
              case None => NotFound("OpenMOLE instance is not running")

        case req@GET -> Root / "openmole" => SeeOther(Location(Uri.unsafeFromString("openmole/")))

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
    case request@GET -> Root / "fonts" / path =>
      val f = new File(webapp, s"fonts/$path")
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
        link(rel := "stylesheet", `type` := "text/css", href := "css/bootstrap.css"),
        link(rel := "stylesheet", `type` := "text/css", href := "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.3.0/font/bootstrap-icons.css"),
        Seq(s"connect-deps.js", "connect.js").map(jf => script(`type` := "text/javascript", src := s"js/$jf "))
      ),
      body(
        onload := jsCall,
        div(id := "appContainer", cls := "centerColumnFlex")
      )
    )

  def addJWTToken(uuid: DB.UUID, hashedPassword: DB.Password)(response: Response[IO])(using JWT.Secret) =
    val token = JWT.TokenData(uuid, hashedPassword)
    val expirationDate = HttpDate.unsafeFromEpochSecond(token.expirationTime / 1000)
    response.addCookie(ResponseCookie(Authentication.authorizationCookieKey, JWT.TokenData.toContent(token), expires = Some(expirationDate)))

  def redirect(to: String) =
    val uri = Uri.unsafeFromString(to)
    SeeOther(Location(uri))
