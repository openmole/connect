package org.openmole.connect.shared

import endpoints4s.{algebra, circe}
import io.circe.*
import io.circe.generic.auto.*
import org.openmole.connect.shared.Data.{PodInfo, User, RegisterUser}


trait AdminAPI
  extends algebra.Endpoints
    with algebra.circe.JsonEntitiesFromCodecs
    with circe.JsonSchemas:

  val users: Endpoint[Unit, Seq[User]] =
    endpoint(
      post(path / "users", jsonRequest[Unit]),
      ok(jsonResponse[Seq[User]])
    )

  val registeringUsers: Endpoint[Unit, Seq[RegisterUser]] =
    endpoint(
      post(path / "registering-users", jsonRequest[Unit]),
      ok(jsonResponse[Seq[RegisterUser]])
    )

  val promoteRegisteringUser: Endpoint[String, Unit] =
    endpoint(
      post(path / "promote-registering-users", jsonRequest[String]),
      ok(jsonResponse[Unit])
    )
    
  val deleteRegisteringUser: Endpoint[String, Unit] =
    endpoint(
      post(path / "delete-registering-users", jsonRequest[String]),
      ok(jsonResponse[Unit])
    )

  val usedSpace: Endpoint[String, Option[Storage]] =
    endpoint(
      post(path / "used-space", jsonRequest[String]),
      ok(jsonResponse[Option[Storage]])
    )

  val instance: Endpoint[String, Option[Data.PodInfo]] =
    endpoint(
      post(path / "instance", jsonRequest[String]),
      ok(jsonResponse[Option[Data.PodInfo]])
    )

  val allInstances: Endpoint[Unit, Seq[Data.UserAndPodInfo]] =
    endpoint(
      post(path / "all-instances", jsonRequest[Unit]),
      ok(jsonResponse[Seq[Data.UserAndPodInfo]])
    )

  val changePassword: Endpoint[(String, String), Boolean] =
    endpoint(
      post(path / "change-password", jsonRequest[(String, String)]),
      ok(jsonResponse[Boolean])
    )
    
  val launch: Endpoint[String, Unit] =
    endpoint(
      post(path / "launch", jsonRequest[String]),
      ok(jsonResponse[Unit])
    )

  val stop: Endpoint[String, Unit] =
    endpoint(
      post(path / "stop", jsonRequest[String]),
      ok(jsonResponse[Unit])
    )


//  val upserted: Endpoint[User, Seq[User]] =
//    endpoint(
//      post(path / "upserted", jsonRequest[User]),
//      ok(jsonResponse[Seq[User]])
//    )
//
//  val delete: Endpoint[User, Seq[User]] =
//    endpoint(
//      post(path / "delete", jsonRequest[User]),
//      ok(jsonResponse[Seq[User]])
//    )
//
//  val startOpenMOLE: Endpoint[User, Seq[User]] =
//    endpoint(
//      post(path / "start-openmole", jsonRequest[User]),
//      ok(jsonResponse[Seq[User]])
//    )
//
//  val stopOpenMOLE: Endpoint[User, Seq[User]] =
//    endpoint(
//      post(path / "stop-openmole", jsonRequest[User]),
//      ok(jsonResponse[Seq[User]])
//    )
//
//  val updateOpenMOLE: Endpoint[User, Seq[User]] =
//    endpoint(
//      post(path / "update-openmole", jsonRequest[User]),
//      ok(jsonResponse[Seq[User]])
//    )
//
//  val updateOpenMOLEStorage: Endpoint[User, Seq[User]] =
//    endpoint(
//      post(path / "update-openmole-storage", jsonRequest[User]),
//      ok(jsonResponse[Seq[User]])
//    )
//
//  val podInfo: Endpoint[Unit, Seq[PodInfo]] =
//    endpoint(
//      post(path / "pod-info", jsonRequest[Unit]),
//      ok(jsonResponse[Seq[PodInfo]])
//    )


//trait AdminApi {
//
//  // USERS
//  def users(): Seq[UserData]
//
//  def upserted(userData: UserData): Seq[UserData]
//
//  def delete(userData: UserData): Seq[UserData]
//
//  def stopOpenMOLE(userData: UserData): Seq[UserData]
//
//  def startOpenMOLE(userData: UserData): Seq[UserData]
//
//  def updateOpenMOLE(userData: UserData): Seq[UserData]
//
//  def updateOpenMOLEPersistentVolumeStorage(userData: UserData): Seq[UserData]
//
//  // PODS
//  def podInfos(): Seq[PodInfo]
//}