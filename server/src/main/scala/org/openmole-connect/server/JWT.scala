package org.openmoleconnect.server

import org.json4s.DefaultFormats
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtHeader, JwtTime}
import java.time.Clock

import DB._

object JWT:

  implicit val clock: Clock = Clock.systemUTC()

  type Secret = String
  type Token = String
  case class Host(uuid: UUID, hostIP: Option[String])

  trait TokenType:
    def cookieKey: String

  object TokenType :
    val accessToken = AccessToken
    val refreshToken = RefreshToken

  object AccessToken extends TokenType:
    def cookieKey = "access_openmole_cookie"

  object RefreshToken extends TokenType:
    def cookieKey = "refresh_openmole_cookie"

  object TokenData:
    def fromTokenContent(content: String, tokenType: TokenType)(implicit secret: Secret) =
      Jwt.decode(content, secret, Seq(JwtAlgorithm.HS256)).map { jwtClaim =>
        val email: Email = Json.fromJson(jwtClaim.content, Json.key.email)

        val host =
          val uuid: UUID = Json.fromJson(jwtClaim.content, Json.key.uuid)
          val hostIP: String = Json.fromJson(jwtClaim.content, Json.key.hostIP)
          val hip =
            if hostIP.isEmpty
            then None
            else Some(hostIP)

          Host(uuid, hip)

        TokenData(email, host, jwtClaim.issuedAt.get, jwtClaim.expiration.get, tokenType)
      }.toOption.filter {
        hasExpired(_)
      }

    def accessToken(host: Host, email: Email) = TokenData(email, host, now, inFiveMinutes, TokenType.accessToken)
    def refreshToken(host: Host, email: Email) = TokenData(email, host, now, inOneMonth, TokenType.refreshToken)

    def toContent(token: TokenData)(implicit secret: Secret) =
      import token.*
      val claims = Seq((Json.key.uuid, host.uuid), (Json.key.hostIP, host.hostIP.getOrElse("")), (Json.key.email, email))

      val expandedClaims =
        claims.map: (k, v) =>
          s"""
             |"$k":"$v"
             |""".stripMargin
        .reduce(_ + "," + _)

      Jwt.encode(
        JwtHeader(algorithm),
        JwtClaim(s"{$expandedClaims}".stripMargin)
          .issuedNow.expiresAt(expirationTime / 1000),
        secret
      )


  case class TokenData(email: Email, host: Host, issued: Long, expirationTime: Long, tokenType: TokenType)

  implicit val formats: DefaultFormats = DefaultFormats
  val algorithm = JwtAlgorithm.HS256

  def now = System.currentTimeMillis()
  def inFiveMinutes = now + 300000L
  def inOneMonth = now + 2592000000L

  def hasExpired(token: String)(using secret: Secret): Option[JwtClaim] =
    Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)).toOption.filter { claim =>
      hasExpired(claim.expiration.get)
    }

  def hasExpired(time: Long): Boolean =
    time > JwtTime.nowSeconds

  def hasExpired(tokenData: TokenData)(implicit secret: Secret): Boolean =
    hasExpired(tokenData.expirationTime)

  def isTokenValid(token: String)(implicit secret: Secret): Boolean =
    Jwt.isValid(token, secret, Seq(algorithm))

