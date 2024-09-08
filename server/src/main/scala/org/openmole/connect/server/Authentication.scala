package org.openmole.connect.server

//import javax.servlet.http.{Cookie, HttpServletRequest}
import cats.effect.IO
import org.http4s.{AuthScheme, Request}
import org.http4s.headers.Cookie
import org.openmole.connect.server.JWT.*
import org.openmole.connect.server.db.DB
import org.openmole.connect.server.db.DB.{Email, Password, Salt, UUID}
import tool.*
import org.openmole.connect.shared.Data

object Authentication:

  object UserCache:
    type Cached = DB.User
    case class UUIDKey(uuid: DB.UUID, password: DB.Password)
    case class EmailKey(email: DB.Email, password: DB.Password)

    import com.google.common.cache.*
    import java.util.concurrent.TimeUnit

    def apply(): UserCache =
      val userUUID = tool.cache[UUIDKey, Cached]()
      val userEmail = tool.cache[EmailKey, Cached]()
      UserCache(userUUID, userEmail)


  case class UserCache(
    userUUID: com.google.common.cache.Cache[UserCache.UUIDKey, UserCache.Cached],
    userEmail: com.google.common.cache.Cache[UserCache.EmailKey, UserCache.Cached])

  object Authorization:
    case class TokenUser(uuid: UUID, password: Password)
    case class BasicUser(email: Email, clearPassword: Password)

  def authorizationCookieKey = "authorized_openmole_cookie"

  def authorization(req: Request[IO])(using JWT.Secret): Option[Authorization.TokenUser | Authorization.BasicUser] =
    def cookie =
      req.headers.get[org.http4s.headers.Cookie].flatMap: c =>
        c.values.find(_.name == authorizationCookieKey).flatMap: c =>
          JWT.TokenData.fromTokenContent(c.content)

    def basicAuthentication =
      req.headers.get[org.http4s.headers.Authorization].flatMap: a =>
        if a.credentials.authScheme == AuthScheme.Basic
        then
          import java.util.Base64
          val auth = a.credentials.renderString.replaceAll(" +", " ").split(" ").drop(1).headOption.getOrElse("")
          val authString = new String(Base64.getDecoder.decode(auth))
          val separator = authString.indexOf(':')
          val user = authString.take(separator)
          val password = authString.drop(separator + 1)
          Some(Authorization.BasicUser(user, password))
        else None

    cookie.map(t => Authorization.TokenUser(t.uuid, t.password)) orElse basicAuthentication

  def authenticatedUser[T](request: Request[IO])(using JWT.Secret, UserCache, Salt): Option[DB.User] =
    authorization(request) match
      case Some(t: Authorization.TokenUser) =>
        def queryUser(k: UserCache.UUIDKey) =
          val user = DB.userFromSaltedPassword(k.uuid, k.password)
          user.foreach(u => DB.updadeLastAccess(u.uuid))
          user

        summon[UserCache].userUUID.getOptional(UserCache.UUIDKey(t.uuid, t.password), queryUser)
      case Some(t: Authorization.BasicUser) =>
        def queryUser(k: UserCache.EmailKey) =
          val user = DB.userFromEmailSaltedPassword(k.email, DB.salted(k.password))
          user.foreach(u => DB.updadeLastAccess(u.uuid))
          user

        summon[UserCache].userEmail.getOptional(UserCache.EmailKey(t.email, t.clearPassword), queryUser)
      case _ => None

//  def isAuthenticated(request: Request[IO])(using JWT.Secret) =
//    authorization(request) match
//      case Some(t: Authorization.TokenUser) => DB.userFromSaltedPassword(t.uuid, t.password).isDefined
//      case _ => false

//  def isAdmin(request: Request[IO])(using JWT.Secret) =
//    authorization(request) match
//      case Some(t: Authorization.TokenUser) => DB.userFromSaltedPassword(t.uuid, t.password).exists(_.role == Data.Role.Admin)
//      case _ => false
