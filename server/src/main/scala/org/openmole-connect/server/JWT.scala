package org.openmoleconnect.server

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import pdi.jwt.{Jwt, JwtAlgorithm, JwtBase64, JwtClaim, JwtHeader, JwtTime}
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

  case class TokenData(login: Login, uuid: String, issued: Long, expirationTime: Long)

  implicit val formats = DefaultFormats
  val algorithm = JwtAlgorithm.HS256

  def tokenData(token: String)(implicit secret: Secret): Option[TokenData] = {
    implicit val clock = Clock.systemUTC()

    Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)).map { jwtClaim =>
      val login: Login = Json.fromJson(jwtClaim.content, Json.key.login)
      val uuid: UUID = Json.fromJson(jwtClaim.content, Json.key.uuid)
      TokenData(login, uuid, jwtClaim.issuedAt.get, jwtClaim.expiration.get)
    }.toOption.filter {
      hasExpired(_)
    }
  }

  def hasExpired(token: String)(implicit secret: Secret): Option[JwtClaim] = {
    Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)).toOption.filter { claim =>
      hasExpired(claim.expiration.get)
    }
  }

  def hasExpired(time: Long): Boolean = {
    implicit val clock = Clock.systemUTC()
    time > JwtTime.nowSeconds
  }

  def hasExpired(tokenData: TokenData)(implicit secret: Secret): Boolean = {
    hasExpired(tokenData.expirationTime)
  }

  def isTokenValid(token: String)(implicit secret: Secret): Boolean =
    Jwt.isValid(token, secret, Seq(algorithm))

  def writeToken(uuid: String)(implicit secret: Secret): TokenAndContext = {
    implicit val clock = Clock.systemUTC()

    val expires = 300L
    val expirationTime = JwtTime.nowSeconds + expires
    TokenAndContext(
      Jwt.encode(
        JwtHeader(algorithm),
        JwtClaim({
          s"""{"${Json.key.uuid}":"$uuid"}"""
        }).issuedNow.expiresIn(expires),
        secret
      ),
      expirationTime
    )

  }
}