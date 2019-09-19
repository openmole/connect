package org.openmoleconnect.server


import javax.servlet.http.{Cookie, HttpServletRequest}
import org.openmoleconnect.server.JWT.{Secret, TokenData}

object Authentication {

  val openmoleCookieKey = "openmole_cookie"

  def cookie(request: HttpServletRequest) = {
    if (request.getCookies != null)
      request.getCookies.find((c: Cookie) => c.getName == openmoleCookieKey)
    else None
  }

  def isValid(request: HttpServletRequest)(implicit secret: Secret): Boolean = {
    cookie(request) match {
      case None =>
        val authFailure = AuthenticationFailure(request.getHeader("User-Agent"),
          request.getRequestURL.toString,
          request.getRemoteAddr)
        println("Error: openmole_cookie cookie not found")
        println("More information:")
        println(authFailure.toString)
        false
      case Some(c: Cookie) => JWT.isTokenValid(c.getValue)
    }
  }

  def tokenData(request: HttpServletRequest)(implicit secret: Secret): Option[TokenData] = {
    cookie(request).flatMap { c =>
      JWT.tokenData(c.getValue)
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