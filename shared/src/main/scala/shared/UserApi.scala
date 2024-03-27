package shared

import shared.Data.UserData

import endpoints4s.{algebra, circe}
import io.circe._
import io.circe.generic.auto._


trait UserAPI
  extends algebra.Endpoints
    with algebra.circe.JsonEntitiesFromCodecs
    with circe.JsonSchemas:

  val user: Endpoint[Unit, Option[UserData]] =
    endpoint(
      post(path / "user", jsonRequest[Unit]),
      ok(jsonResponse[Option[UserData]])
    )

  val userWithData: Endpoint[Option[UserData], Option[UserData]] =
    endpoint(
      post(path / "user-with-data", jsonRequest[Option[UserData]]),
      ok(jsonResponse[Option[UserData]])
    )

  val upserted: Endpoint[UserData, Option[UserData]] =
    endpoint(
      post(path / "upserted", jsonRequest[UserData]),
      ok(jsonResponse[Option[UserData]])
    )

  val upsertedWithData: Endpoint[(UserData, Option[UserData]), Option[UserData]] =
    endpoint(
      post(path / "upserted-with-data", jsonRequest[(UserData, Option[UserData])]),
      ok(jsonResponse[Option[UserData]])
    )



//trait UserApi {
//
//  def user(): Option[UserData]
//
//  def userWithData(connectedUserData: Option[UserData]): Option[UserData]
//
//  def upserted(userData: UserData): Option[UserData]
//
//  def upsertedWithData(userData: UserData, connectedUserData: Option[UserData]): Option[UserData]
//}
