package org.openmole.connect.server

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.hc.client5.http.classic.methods.{ClassicHttpRequests, HttpDelete, HttpGet, HttpPost}
import org.http4s.*
import org.http4s.blaze.server.*
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.implicits.*
import org.http4s.server.*
import org.openmole.connect.shared.Data
import org.typelevel.ci.CIString
import pdi.jwt.*

import java.io.{BufferedOutputStream, File, FileOutputStream}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import fs2.io.*
import io.circe.yaml
import io.circe.generic.auto.*
import org.apache.hc.client5.http.impl.classic.*
import org.apache.hc.core5.http.{ConnectionReuseStrategy, ContentType}
import org.apache.hc.core5.http.io.entity.InputStreamEntity
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder
import org.openmole.connect.server.db.DB

import java.net.URLDecoder
import java.util.concurrent.TimeUnit


object ConnectServer:

  object Config:
    // Hours
    val resetPasswordExpire = 24

    case class Kube(storageClassName: Option[String] = None, storageSize: Int)
    case class OpenMOLE(versionHistory: Option[Int], minimumVersion: Option[Int])
    case class SMTP(server: String, port: Int, user: String, password: String, from: String)
    case class Shutdown(days: Int, checkAt: Option[Int] = None, remind: Option[Seq[Int]] = None)

  case class Config(
                     salt: String,
                     secret: String,
                     kube: Config.Kube,
                     openmole: Config.OpenMOLE,
                     smtp: Option[Config.SMTP] = None,
                     shutdown: Option[Config.Shutdown] = None)

  def read(file: File): Config =
    import better.files.*
    yaml.parser.parse(file.toScala.contentAsString).toTry.get.as[Config].toTry.get


  def apply(config: Config) =
    val k8s = K8sService(config.kube.storageClassName, config.kube.storageSize)
    new ConnectServer(config, k8s)


class ConnectServer(config: ConnectServer.Config, k8s: K8sService):
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  given jwtSecret: JWT.Secret = JWT.Secret(config.secret)
  given salt: DB.Salt = DB.Salt(config.salt)
  given authenticationCache: Authentication.UserCache = Authentication.UserCache()
  given kubeCache: K8sService.KubeCache = K8sService.KubeCache()
  given dockerHubCache: OpenMOLE.DockerHubCache = OpenMOLE.DockerHubCache()
  given K8sService = k8s
  given Email.Sender = Email.Sender(config.smtp)

  val httpClient =
    HttpClients.
      custom().
      disableAutomaticRetries().
      disableRedirectHandling().
      setConnectionManager(tool.connectionManager()).
      build()

  def start() =
    DB.initDB()
    ScheduledTask.schedule(config.shutdown)

    val serverRoutes: HttpRoutes[IO] =
      HttpRoutes.of:
        case req@GET -> Root =>
          Authentication.authenticatedUser(req) match
            case Some(user) => ServerContent.ok("user();").map(ServerContent.addJWTToken(user.uuid, user.password))
            case None => ServerContent.redirect(s"/${Data.connectionRoute}")

        case req@GET -> Root / Data.connectionRoute => ServerContent.ok(ServerContent.connectionFunction(None))

        case req@GET -> Root / Data.validateRoute =>
          def sendNotification(r: Seq[DB.RegisterUser]) =
            if r.nonEmpty
            then
              def emails = DB.admins.map(_.email)

              Email.sendNotification(emails, s"${r.size} user waiting for validation", s"A user mail has been checked. ${r.size} waiting for validation.")

          req.params.get("uuid") zip req.params.get("secret") match
            case Some((uuid, secret)) =>
              val res = DB.validateUserEmail(uuid, secret)
              sendNotification(DB.registerUsers)

              if res
              then Ok("Thank you, your email has been validated")
              else NotFound("validation not found")
            case None => BadRequest("Expected uuid and secret")

        case req@POST -> Root / Data.connectionRoute =>
          def connectionError(error: String) =
            Forbidden.apply(ServerContent.someHtml(ServerContent.connectionFunction(Some(error))).render)
              .map(_.withContentType(`Content-Type`(MediaType.text.html)))

          req.decode[UrlForm]: r =>
            r.getFirst("Email") zip r.getFirst("Password") match
              case Some((email, password)) =>
                DB.user(email, password) match
                  case Some(user) => ServerContent.redirect("/").map(ServerContent.addJWTToken(user.uuid, DB.salted(password)))
                  case None =>
                    DB.registerUser(email) match
                      case Some(u) if u.emailStatus == Data.EmailStatus.Unchecked => connectionError("User email has not been validated and the account has not been validated by an admin")
                      case Some(u) => connectionError("User account has not been validated by an admin")
                      case None => connectionError("Invalid email or password")
              case None => BadRequest("Missing email or password")

        case req@GET -> Root / Data.`resetPasswordRoute` =>
          req.params.get("uuid") zip req.params.get("secret") match
            case Some((uuid, secret)) => ServerContent.ok(s"""resetPassword("$uuid", "$secret")""")
            case None => BadRequest("Expected uuid and secret")

        case req@GET -> Root / Data.disconnectRoute =>
          val cookie = ResponseCookie(Authentication.authorizationCookieKey, "expired", expires = Some(HttpDate.MinValue))
          ServerContent.redirect(s"/${Data.connectionRoute}").map(_.addCookie(cookie))

        case req@GET -> Root / Data.impersonateRoute =>
          ServerContent.authenticated(req, admin = true): admin =>
            req.params.get("uuid") match
              case Some(uuid) =>
                DB.userFromUUID(uuid) match
                  case Some(user) => ServerContent.redirect(s"/").map(ServerContent.addJWTToken(user.uuid, user.password))
                  case None => NotFound(s"User not found ${uuid}")
              case None => BadRequest("No uuid parameter found")

        case req if req.uri.path.startsWith(Root / Data.openAPIRoute) =>
          val impl = APIImpl()
          val userAPI = new APIRoutes(impl)
          val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
          val apiReq = req.withUri(req.uri.withPath(apiPath))
          userAPI.routes.apply(apiReq).getOrElseF(NotFound())

        case req if req.uri.path.startsWith(Root / Data.userAPIRoute) =>
          ServerContent.authenticated(req): user =>
            val impl = UserAPIImpl(user.uuid, config.openmole)
            val userAPI = new UserAPIRoutes(impl)
            val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
            val apiReq = req.withUri(req.uri.withPath(apiPath))
            userAPI.routes.apply(apiReq).getOrElseF(NotFound())

        case req if req.uri.path.startsWith(Root / Data.adminAPIRoute) =>
          ServerContent.authenticated(req, admin = true): user =>
            val impl = AdminAPIImpl()
            val adminAPI = new AdminAPIRoutes(impl)
            val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
            val apiReq = req.withUri(req.uri.withPath(apiPath))
            adminAPI.routes.apply(apiReq).getOrElseF(NotFound())

        case req@GET -> Root / Data.downloadUserRoute =>
          ServerContent.authenticated(req, admin = true): user =>
            def dbAsCSV =
              DB.users.map:u=>
                s"${u.name},${u.email},${u.institution}"
              .mkString("\n")

            Ok(dbAsCSV).map: r =>
              r.withHeaders(org.http4s.headers.`Content-Disposition`("attachment", Map(CIString("filename") -> "users.csv")))
        case req if req.uri.path.startsWith(Root / Data.openMOLERoute) && (req.uri.path.segments.drop(1).nonEmpty || req.uri.path.endsWithSlash) =>
          ServerContent.authenticated(req, challenge = true): user =>
            K8sService.podIP(user.uuid) match
              case Some(ip) =>
                val openmoleURL = s"http://$ip:80"
                val openmoleURI = java.net.URI(openmoleURL)

                def authority =
                  Uri.Authority(host = Uri.Host.unsafeFromString(openmoleURI.getHost), port = Some(openmoleURI.getPort))

                val forwardURI =
                  def path = Path(req.uri.path.segments.drop(1))

                  def scheme = Uri.Scheme.unsafeFromString(openmoleURI.getScheme)

                  req.uri.copy(authority = Some(authority), scheme = Some(scheme)).withPath(path).toString

                def forwardedHeaders(req: Request[IO]) =
                  val filteredHeaders = Set(CIString("Content-Length"))
                  req.headers.headers.filter(h => !filteredHeaders.contains(h.name)) ++ Seq(
                    Header.Raw(CIString("X-Forwarded-URI"), req.uri.renderString)
                  )

                def response(forwardResponse: CloseableHttpResponse) =
                  def forwardStatus = Status.fromInt(forwardResponse.getCode).toTry.get

                  Ok(fs2.io.readInputStream(IO(forwardResponse.getEntity.getContent), 10240).onFinalize(IO(forwardResponse.close()))).map: r =>
                    val hs: Seq[Header.ToRaw] = forwardResponse.getHeaders.map(h => h.getName -> h.getValue: Header.ToRaw).toSeq
                    r.putHeaders(hs: _*).withStatus(forwardStatus)

                val fr =
                  req.method match
                    case GET => Some(ClassicRequestBuilder.get(forwardURI).build())
                    case POST => Some(ClassicRequestBuilder.post(forwardURI).build())
                    case DELETE => Some(ClassicRequestBuilder.delete(forwardURI).build())
                    case OPTIONS => Some(ClassicRequestBuilder.options(forwardURI).build())
                    case PUT => Some(ClassicRequestBuilder.put(forwardURI).build())
                    case HEAD => Some(ClassicRequestBuilder.head(forwardURI).build())
                    case Method.MOVE => Some(ClassicRequestBuilder.create("MOVE").setUri(forwardURI).build())
                    case Method.MKCOL => Some(ClassicRequestBuilder.create("MKCOL").setUri(forwardURI).build())
                    case Method.PROPFIND => Some(ClassicRequestBuilder.create("PROPFIND").setUri(forwardURI).build())
                    case _ => None

                fr match
                  case Some(fr) =>
                    val res = fs2.io.toInputStreamResource(req.body).use: is =>
                      fr.setEntity(new InputStreamEntity(is, null))
                      forwardedHeaders(req).foreach(h => fr.setHeader(h.name.toString, h.value))
                      response(httpClient.execute(fr))
                    res
                  case None =>
                    NotImplemented(s"Method ${req.method.name} is not supported by openmole-connect yet")
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
        .withMaxConnections(10240)
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

  def authenticated[T](req: Request[IO], challenge: Boolean = false, admin: Boolean = false)(using JWT.Secret, Authentication.UserCache, DB.Salt)(f: DB.User => T) =
    Authentication.authenticatedUser(req) match
      case Some(user) =>
        if !admin then f(user)
        else
          if DB.userIsAdmin(user)
          then f(user)
          else Forbidden(s"User ${user.name} is not admin")
      case _ =>
        if challenge
        then Unauthorized.apply(`WWW-Authenticate`(Challenge("Basic", "OpenMOLE")))
        else
          Forbidden.apply(ServerContent.someHtml(ServerContent.connectionFunction(None)).render).map(
            _.withContentType(`Content-Type`(MediaType.text.html))
          )


  def ok(jsCall: String) =
    Ok.apply(ServerContent.someHtml(jsCall).render).map(_.withContentType(`Content-Type`(MediaType.text.html)))

  def someHtml(jsCall: String) =
    import scalatags.Text.all.*

    //contentType = "text/html"
    html(
      head(
        meta(httpEquiv := "Content-Type", content := "text/html; charset=UTF-8"),
        link(rel := "icon", href := "img/favicon.svg", `type` := "img/svg+xml"),
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

  def connectionFunction(error: Option[String]) =
    val value =
      error match
        case Some(e) => s"\"$e\""
        case None => "null"
    s"""connection($value);"""

