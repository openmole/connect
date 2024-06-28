package org.openmole.connect.server

//import javax.servlet.http.{Cookie, HttpServletRequest}
import cats.effect.IO
import org.http4s.Request
import org.http4s.headers.Cookie
import org.openmole.connect.server.JWT.*
import org.openmole.connect.server.db.v1.DB
import tool.*
import org.openmole.connect.shared.Data

object Authentication:

  object AuthenticationCache:
    type Cached = DB.User
    case class Key(uuid: DB.UUID, password: DB.Password)

    import com.google.common.cache.*
    import java.util.concurrent.TimeUnit

    def apply(): AuthenticationCache =
      val user = tool.cache[Key, Cached]()
      AuthenticationCache(user)


  case class AuthenticationCache(user: com.google.common.cache.Cache[AuthenticationCache.Key, AuthenticationCache.Cached])



  def authorizationCookieKey = "authorized_openmole_cookie"

  def authorizationToken(req: Request[IO])(using JWT.Secret) =
    val cookie =
      req.headers.get[org.http4s.headers.Cookie].flatMap: c =>
        c.values.find(_.name == authorizationCookieKey).flatMap: c =>
          JWT.TokenData.fromTokenContent(c.content)

    cookie

  def authenticatedUser[T](request: Request[IO])(using JWT.Secret, AuthenticationCache): Option[DB.User] =
    authorizationToken(request) match
      case Some(t) =>
        def queryUser(k: AuthenticationCache.Key) =
          val user = DB.userFromSaltedPassword(k.uuid, k.password)
          user.foreach(u => DB.updadeLastAccess(k.uuid))
          user

        summon[AuthenticationCache].user.getOptional(AuthenticationCache.Key(t.uuid, t.password), queryUser)
      case _ => None

  def isAuthenticated(request: Request[IO])(using JWT.Secret) =
    authorizationToken(request) match
      case Some(t) => DB.userFromSaltedPassword(t.uuid, t.password).isDefined
      case _ => false

  def isAdmin(request: Request[IO])(using JWT.Secret) =
    authorizationToken(request) match
      case Some(t) => DB.userFromSaltedPassword(t.uuid, t.password).exists(_.role == Data.Role.Admin)
      case _ => false
