package org.openmoleconnect.server

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtHeader, JwtTime}
import java.time.Clock

import DB._

import scala.util.Try

/*
* This object decodes JWTs that are created by another application.
* There are a few different options available for parsing JWTs and I went with:
* http://pauldijou.fr/jwt-scala/samples/jwt-core/
*/

object JWT {

  type Secret = String
  type Token = String
  case class TokenAndContext(token: Token, expiresTime: Long)

  case class TokenData(login: Login, issued: Long, expirationTime: Long)

  implicit val formats = DefaultFormats
  val algorithm = JwtAlgorithm.HS256

  def user(token: String)(implicit secret: Secret): Option[TokenData] = {
    Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)).map{ jwtClaim=>
      val login: Login = Json.fromJson(jwtClaim.content, Json.key.login)
      TokenData(login, jwtClaim.issuedAt.get, jwtClaim.expiration.get)
    }.toOption
  }

  def isTokenValid(token: String)(implicit secret: Secret): Boolean =
    Jwt.isValid(token, secret, Seq(algorithm))

  def writeToken(login: String)(implicit secret: Secret): TokenAndContext = {
    implicit val clock = Clock.systemUTC()

    val expires = 300
    val expirationTime = JwtTime.nowSeconds + expires
    TokenAndContext(
    Jwt.encode(
      JwtHeader(algorithm),
      JwtClaim({s"""{${Json.key.login}:$login}"""}).issuedNow.expiresIn(expirationTime),
      secret
    ),
      expirationTime
    )

  }
}