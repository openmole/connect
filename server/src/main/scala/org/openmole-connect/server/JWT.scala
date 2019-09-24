package org.openmoleconnect.server

import org.json4s.DefaultFormats
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtHeader, JwtTime}
import java.time.Clock

import DB._

object JWT {

  implicit val clock = Clock.systemUTC()

  type Secret = String
  type Token = String

  trait TokenType {
    def cookieKey: String
  }

  object TokenType  {
    val accessToken = AccessToken
    val refreshToken = RefreshToken
  }

  object AccessToken extends TokenType{
    def cookieKey = "access_openmole_cookie"
  }

  object RefreshToken extends TokenType  {
    def cookieKey = "refresh_openmole_cookie"
  }

  object TokenData {
    def fromTokenContent(content: String, tokenType: TokenType)(implicit secret: Secret) = {
      Jwt.decode(content, secret, Seq(JwtAlgorithm.HS256)).map { jwtClaim =>
        val login: Login = Login(Json.fromJson(jwtClaim.content, Json.key.login))
        val uuid: UUID = UUID(Json.fromJson(jwtClaim.content, Json.key.uuid))
        TokenData(login, uuid, jwtClaim.issuedAt.get, jwtClaim.expiration.get, tokenType)
      }.toOption.filter {
        hasExpired(_)
      }
    }

    def accessToken(uuid: UUID, login: Login) = TokenData(login, uuid, now, inFiveMinutes, TokenType.accessToken)

    def refreshToken(uuid: UUID, login: Login) = TokenData(login, uuid, now, inOneMonth, TokenType.refreshToken)
  }

  case class TokenData(login: Login, uuid: UUID, issued: Long, expirationTime: Long, tokenType: TokenType) {

    def toContent(implicit secret: Secret) = {
      implicit val clock = Clock.systemUTC()

      val claims = Seq((Json.key.uuid, uuid.value), (Json.key.login, login.value))

      val expandedClaims = claims.map { case (k, v) =>
        s"""
           |"$k":"$v"
           |""".stripMargin
      }.reduce(_ + "," + _)

      Jwt.encode(
        JwtHeader(algorithm),
        JwtClaim(s"{$expandedClaims}".stripMargin)
          .issuedNow.expiresAt(expirationTime / 1000),
        secret
      )
    }

  }

  implicit val formats = DefaultFormats
  val algorithm = JwtAlgorithm.HS256

  def now = System.currentTimeMillis()

  def inFiveMinutes = now + 300000L

  def inOneMonth = now + 2592000000L

  def hasExpired(token: String)(implicit secret: Secret): Option[JwtClaim] = {
    Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)).toOption.filter { claim =>
      hasExpired(claim.expiration.get)
    }
  }

  def hasExpired(time: Long): Boolean = {
    time > JwtTime.nowSeconds
  }

  def hasExpired(tokenData: TokenData)(implicit secret: Secret): Boolean = {
    hasExpired(tokenData.expirationTime)
  }

  def isTokenValid(token: String)(implicit secret: Secret): Boolean =
    Jwt.isValid(token, secret, Seq(algorithm))

}