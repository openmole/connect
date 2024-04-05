package org.openmole.connect.server

//import javax.servlet.http.{Cookie, HttpServletRequest}
import cats.effect.IO
import org.http4s.Request
import org.http4s.headers.Cookie
import org.openmole.connect.server.JWT.*

object Authentication:

  def authorizationCookieKey = "authorized_openmole_cookie"

  def authorizationToken(req: Request[IO])(using JWT.Secret) =
    val cookie =
      req.headers.get[org.http4s.headers.Cookie].flatMap: c =>
        c.values.find(_.name == authorizationCookieKey).flatMap: c =>
          JWT.TokenData.fromTokenContent(c.content)

    cookie

  def authenticatedUser[T](request: Request[IO])(using JWT.Secret): Option[DB.User] =
    authorizationToken(request) match
      case Some(t) => DB.userSaltedPassword(t.email, t.password)
      case _ => None

  def isAuthenticated(request: Request[IO])(using JWT.Secret) =
    authorizationToken(request) match
      case Some(t) => DB.userSaltedPassword(t.email, t.password).isDefined
      case _ => false

  def isAdmin(request: Request[IO])(using JWT.Secret) =
    authorizationToken(request) match
      case Some(t) => DB.userSaltedPassword(t.email, t.password).exists(_.role == DB.admin)
      case _ => false

//
//  def cookie(request: Request[IO]) = request.headers.get[org.http4s.headers.Cookie]
//
//  def isValid(request: Request[IO])(using JWT.Secret): Boolean =
//    cookie(request) match
//      case None =>
////        val authFailure = AuthenticationFailure(request.getHeader("User-Agent"),
////          request.getRequestURL.toString,
////          request.getRemoteAddr)
//
//        //println(s"Error: cookie not found")
//        //println("More information:")
//        //println(authFailure.toString)
//        false
//      case Some(c: Cookie) => JWT.isTokenValid(c.values.head.renderString)
//
////  def tokenData(request: HttpServletRequest, tokenType: TokenType)(implicit secret: Secret): Option[TokenData] = {
////    cookie(request, tokenType).flatMap { c =>
////      JWT.TokenData.fromTokenContent(c.getValue, tokenType)
////    }
////  }
//
//
//case class AuthenticationFailure(
//  userAgent: String,
//  url: String,
//  remoteAddr: String):
//
//  override def toString =
//    "AuthenticationFailure(\n" +
//      "  User-Agent: " + userAgent + "\n" +
//      "  Request URL: " + url + "\n" +
//      "  Remote Address: " + remoteAddr + "\n" +
//      ")"
