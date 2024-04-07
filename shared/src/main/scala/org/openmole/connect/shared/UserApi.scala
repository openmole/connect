package org.openmole.connect.shared

import endpoints4s.{algebra, circe}
import io.circe.*
import io.circe.generic.auto.*
import org.openmole.connect.shared.Data.User


trait UserAPI
  extends algebra.Endpoints
    with algebra.circe.JsonEntitiesFromCodecs
    with circe.JsonSchemas:

  val user: Endpoint[Unit, Data.User] =
    endpoint(
      post(path / "user", jsonRequest[Unit]),
      ok(jsonResponse[Data.User])
    )

  val instance: Endpoint[Unit, Option[Data.PodInfo]] =
    endpoint(
      post(path / "instance", jsonRequest[Unit]),
      ok(jsonResponse[Option[Data.PodInfo]])
    )

  val launch: Endpoint[Unit, Unit] =
    endpoint(
      post(path / "launch", jsonRequest[Unit]),
      ok(jsonResponse[Unit])
    )

//  val userWithData: Endpoint[Option[UserData], Option[UserData]] =
//    endpoint(
//      post(path / "user-with-data", jsonRequest[Option[UserData]]),
//      ok(jsonResponse[Option[UserData]])
//    )
//
//  val upserted: Endpoint[UserData, Option[UserData]] =
//    endpoint(
//      post(path / "upserted", jsonRequest[UserData]),
//      ok(jsonResponse[Option[UserData]])
//    )
//
//  val upsertedWithData: Endpoint[(UserData, Option[UserData]), Option[UserData]] =
//    endpoint(
//      post(path / "upserted-with-data", jsonRequest[(UserData, Option[UserData])]),
//      ok(jsonResponse[Option[UserData]])
//    )



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
