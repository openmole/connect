package org.openmole.connect.server

import org.json4s.DefaultFormats
import org.openmole.connect.server.DB.*
import org.openmole.connect.server.JWT.TokenData.inOneMonth
import pdi.jwt.*

import java.time.Clock

object JWT:

  implicit val clock: Clock = Clock.systemUTC()

  object Secret:
    def apply(s: String): Secret = s

  opaque type Secret = String

//  type Token = String
//  case class Host(uuid: UUID, hostIP: Option[String])
  val algorithm = JwtAlgorithm.HS256

  def isTokenValid(token: String)(implicit secret: Secret): Boolean =
    Jwt.isValid(token, secret, Seq(algorithm))

//  trait TokenType:
//    def cookieKey: String
//
//  object TokenType :
//    val accessToken = AccessToken
//    val authorizedToken = AuthorizedToken
//
//
//  object AccessToken extends TokenType:
//    def cookieKey = "access_openmole_cookie"
//
//  object AuthorizedToken extends TokenType:
//    def cookieKey = "authorized_openmole_cookie"

  object TokenData:

    implicit val formats: DefaultFormats = DefaultFormats

    def inFiveMinutes = Utils.now + 300000L
    def inOneMonth = Utils.now + 2592000000L

    def hasExpired(token: String)(using secret: Secret): Option[JwtClaim] =
      Jwt.decode(token, secret, Seq(JWT.algorithm)).toOption.filter { claim =>
        hasExpired(claim.expiration.get)
      }

    def hasExpired(time: Long): Boolean =
      time > Utils.now

    def hasExpired(tokenData: TokenData)(implicit secret: Secret): Boolean =
      hasExpired(tokenData.expirationTime)

    def fromTokenContent(content: String)(using secret: Secret) =
      Jwt.decode(content, secret, Seq(JWT.algorithm)).map: jwtClaim =>
        val email: Email = Json.fromJson(jwtClaim.content, Json.key.email)
//        val host =
//          val uuid: UUID = Json.fromJson(jwtClaim.content, Json.key.uuid)
//          val hostIP: String = Json.fromJson(jwtClaim.content, Json.key.hostIP)
//          val hip =
//            if hostIP.isEmpty
//            then None
//            else Some(hostIP)
//
//          Host(uuid, hip)

        TokenData(email, jwtClaim.issuedAt.get, jwtClaim.expiration.get)
      .toOption.filter { t => !hasExpired(t) }

    def toContent(token: TokenData)(using secret: Secret) =
      import token.*
      val claims = Seq(Json.key.email -> email)

      val expandedClaims =
        claims.map: (k, v) =>
          s""""$k":"$v""""
        .mkString(",\n")

      Jwt.encode(
        JwtHeader(algorithm),
        JwtClaim(s"{$expandedClaims}".stripMargin)
          .issuedNow.expiresAt(expirationTime / 1000),
        secret
      )


  case class TokenData(email: Email, issued: Long = Utils.now, expirationTime: Long = inOneMonth)

