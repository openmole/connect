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
import org.apache.http.client.methods.{HttpGet, HttpPost}
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
//object ConnectServer:
//
//  case class Config(salt: String, kubeOff: Boolean)
//  def read(file: File): Config =
//    import better.files.*
//    yaml.parser.parse(file.toScala.contentAsString).toTry.get.as[Config].toTry.get




class ConnectServer(salt: String, secret: String,  kubeOff: Boolean, openmoleURL: String):
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  given jwtSecret: JWT.Secret = JWT.Secret(secret)

  val httpClient = HttpClients.custom().disableAutomaticRetries().disableRedirectHandling().build()


  def start() =
    val openRoute: HttpRoutes[IO] =
      HttpRoutes.of:
        case req @ GET -> Root =>
          Authentication.authenticatedUser(req) match
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
                DB.uuid(email, password, salt) match
                  case Some(uuid) =>
                    val token = JWT.TokenData(email)
                    val expirationDate = HttpDate.unsafeFromEpochSecond(token.expirationTime / 1000)
                    ServerContent.ok("user();").map(_.addCookie(ResponseCookie(Authentication.authorizationCookieKey, JWT.TokenData.toContent(token), expires = Some(expirationDate))))
                  case None => ServerContent.connectionError
              case None => ServerContent.connectionError

        case req if req.uri.path.startsWith(Root / Data.userAPIRoute) =>
          ServerContent.authenticated(req): user =>
            val userAPI = new UserAPIImpl(kubeOff, DB.User.toUserData(user))
            val apiPath = Root.addSegments(req.uri.path.segments.drop(1))
            val apiReq = req.withUri(req.uri.withPath(apiPath))
            userAPI.routes.apply(apiReq).getOrElseF(NotFound())

        case req if req.uri.path.startsWith(Root / "openmole") && (req.uri.path.segments.drop(1).nonEmpty || req.uri.path.endsWithSlash) =>
          val openmoleURI = java.net.URI(openmoleURL)

          def authority =
            Uri.Authority(host = Uri.Host.unsafeFromString(openmoleURI.getHost), port = Some(openmoleURI.getPort))

          val uri =
            def path = Path(req.uri.path.segments.drop(1))
            def scheme = Uri.Scheme.unsafeFromString(openmoleURI.getScheme)
            req.uri.copy(authority = Some(authority), scheme = Some(scheme)).withPath(path).toString

          val filteredHeaders = Set(CIString("Content-Length"))

          req.method match
            case p @ POST =>
              val res = fs2.io.toInputStreamResource(req.body).use: is =>
                val post = new HttpPost(uri)
                post.setEntity(new InputStreamEntity(is))

                req.headers.headers.filter(h => !filteredHeaders.contains(h.name)).foreach: h =>
                  post.setHeader(h.name.toString, h.value)

                val forwardResponse = httpClient.execute(post)
                def forwardStatus = Status.fromInt(forwardResponse.getStatusLine.getStatusCode).toTry.get

                Ok(fs2.io.readInputStream(IO(forwardResponse.getEntity.getContent), 10240)).map: r =>
                  val hs: Seq[Header.ToRaw] = forwardResponse.getAllHeaders.map(h => h.getName -> h.getValue: Header.ToRaw).toSeq
                  r.putHeaders(hs: _*).withStatus(forwardStatus)

              res
            case p @ GET =>
              val get = new HttpGet(uri)
              req.headers.headers.filter(h => !filteredHeaders.contains(h.name)).foreach: h =>
                get.setHeader(h.name.toString, h.value)

              val forwardResponse = httpClient.execute(get)

              def forwardStatus = Status.fromInt(forwardResponse.getStatusLine.getStatusCode).toTry.get

              Ok(fs2.io.readInputStream(IO(forwardResponse.getEntity.getContent), 10240)).map: r =>
                val hs: Seq[Header.ToRaw] = forwardResponse.getAllHeaders.map(h => h.getName -> h.getValue: Header.ToRaw).toSeq
                r.putHeaders(hs: _*).withStatus(forwardStatus)
            case _ => ???

        case req @ GET -> Root / "openmole" => SeeOther(Location(Uri.unsafeFromString("openmole/")))
//                    println("copy")
//                    val f = new File("/tmp/test.txt")
//                    val os = new BufferedOutputStream(new FileOutputStream(f))
//                    try IOUtils.copy(is, os, 10240)
//                    finally os.close()

                  //fs2.io.readInputStream[IO](req.body.in.widen[InputStream], DefaultChunkSize)

                  //val inputStream = fs2.io.readInputStream(req.body.chunkAll)

//
//                  as[java.io.InputStream].flatMap: is =>
//                    ???


//                  req.decode[Multipart[IO]]: parts =>
//                    val stream = fs2.io.toInputStreamResource(parts.body)
//                    stream.use { st =>
//                      IO:
//                        st.copy(destination)
//                        destination.setExecutable(true)
//                    }.unsafeRunSync()



//            val config = RequestConfig()
//            config.get
//            proxyRequest.setConfig()
//            proxyRequest.
//
//            val response =
//              val httpGet = new HttpGet("http://mirrors.lug.mtu.edu/debian-cd/12.5.0/amd64/jigdo-dvd/debian-12.5.0-amd64-DVD-10.jigdo")
//              val forwardResponse = httpClient.execute(httpGet)
//
//              Ok(fs2.io.readInputStream(IO(forwardResponse.getEntity.getContent), 10240))
//            response

            //Ok()

    val adminRoute: AuthedRoutes[DB.User, IO] = AuthedRoutes.of:
      case req @ GET -> Root / "admin" as user =>
        println("cookies " + req.req.headers.get[org.http4s.headers.Cookie])
        println(user)
        Ok(s"$user")


//    val middleware: AuthMiddleware[IO, DB.User] =
//      type PartialOptionT[T] = OptionT[IO, T]
//      def authUser: Kleisli[PartialOptionT, Request[IO], DB.User] =
//        Kleisli: r =>
//          OptionT.liftF(IO(???))
//
//      AuthMiddleware(authUser)



    val routes = openRoute <+> ServerContent.contentRoutes

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
