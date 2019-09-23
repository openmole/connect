package org.openmoleconnect.server


import javax.servlet.http.{Cookie, HttpServletRequest}
import org.openmoleconnect.server.JWT._

object Authentication {


  def cookie(request: HttpServletRequest, tokenType: TokenType) = {
    if (request.getCookies != null)
      request.getCookies.find((c: Cookie) => c.getName == tokenType.cookieKey)
    else None
  }

  def isValid(request: HttpServletRequest, tokenType: TokenType)(implicit secret: Secret): Boolean = {
    cookie(request, tokenType) match {
      case None =>
        val authFailure = AuthenticationFailure(request.getHeader("User-Agent"),
          request.getRequestURL.toString,
          request.getRemoteAddr)
        println(s"Error: cookie not found")
        println("More information:")
        println(authFailure.toString)
        false
      case Some(c: Cookie) => JWT.isTokenValid(c.getValue)
    }
  }

  def tokenData(request: HttpServletRequest, tokenType: TokenType)(implicit secret: Secret): Option[TokenData] = {
    cookie(request, tokenType).flatMap { c =>
      JWT.TokenData.fromTokenContent(c.getValue, tokenType)
    }
  }
}

case class AuthenticationFailure(userAgent: String,
                                 url: String,
                                 remoteAddr: String) {
  override def toString = {
    "AuthenticationFailure(\n" +
      "  User-Agent: " + userAgent + "\n" +
      "  Request URL: " + url + "\n" +
      "  Remote Address: " + remoteAddr + "\n" +
      ")"
  }
}