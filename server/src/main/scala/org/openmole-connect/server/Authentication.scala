package org.openmoleconnect.server


//import javax.servlet.http.{Cookie, HttpServletRequest}
import org.openmoleconnect.server.JWT._
import org.http4s.headers.Cookie
import org.http4s.Request
import cats.effect.IO

object Authentication:

  def cookie(request: Request[IO], tokenType: TokenType) = request.headers.get[org.http4s.headers.Cookie]

  def isValid(request: Request[IO], tokenType: TokenType)(implicit secret: Secret): Boolean =
    cookie(request, tokenType) match
      case None =>
//        val authFailure = AuthenticationFailure(request.getHeader("User-Agent"),
//          request.getRequestURL.toString,
//          request.getRemoteAddr)

        //println(s"Error: cookie not found")
        //println("More information:")
        //println(authFailure.toString)
        false
      case Some(c: Cookie) => JWT.isTokenValid(c.values.head.renderString)

//  def tokenData(request: HttpServletRequest, tokenType: TokenType)(implicit secret: Secret): Option[TokenData] = {
//    cookie(request, tokenType).flatMap { c =>
//      JWT.TokenData.fromTokenContent(c.getValue, tokenType)
//    }
//  }


case class AuthenticationFailure(
  userAgent: String,
  url: String,
  remoteAddr: String):

  override def toString =
    "AuthenticationFailure(\n" +
      "  User-Agent: " + userAgent + "\n" +
      "  Request URL: " + url + "\n" +
      "  Remote Address: " + remoteAddr + "\n" +
      ")"
