package org.openmole.connect.shared

import endpoints4s.{algebra, circe}
import io.circe.*
import io.circe.generic.auto.*
import org.openmole.connect.shared.Data.{PodInfo, UserData}


trait AdminAPI
  extends algebra.Endpoints
    with algebra.circe.JsonEntitiesFromCodecs
    with circe.JsonSchemas {

  val users: Endpoint[Unit, Seq[UserData]] =
    endpoint(
      post(path / "users", jsonRequest[Unit]),
      ok(jsonResponse[Seq[UserData]])
    )

  val upserted: Endpoint[UserData, Seq[UserData]] =
    endpoint(
      post(path / "upserted", jsonRequest[UserData]),
      ok(jsonResponse[Seq[UserData]])
    )

  val delete: Endpoint[UserData, Seq[UserData]] =
    endpoint(
      post(path / "delete", jsonRequest[UserData]),
      ok(jsonResponse[Seq[UserData]])
    )

  val startOpenMOLE: Endpoint[UserData, Seq[UserData]] =
    endpoint(
      post(path / "start-openmole", jsonRequest[UserData]),
      ok(jsonResponse[Seq[UserData]])
    )

  val stopOpenMOLE: Endpoint[UserData, Seq[UserData]] =
    endpoint(
      post(path / "stop-openmole", jsonRequest[UserData]),
      ok(jsonResponse[Seq[UserData]])
    )

  val updateOpenMOLE: Endpoint[UserData, Seq[UserData]] =
    endpoint(
      post(path / "update-openmole", jsonRequest[UserData]),
      ok(jsonResponse[Seq[UserData]])
    )

  val updateOpenMOLEStorage: Endpoint[UserData, Seq[UserData]] =
    endpoint(
      post(path / "update-openmole-storage", jsonRequest[UserData]),
      ok(jsonResponse[Seq[UserData]])
    )

  val podInfo: Endpoint[Unit, Seq[PodInfo]] =
    endpoint(
      post(path / "pod-info", jsonRequest[Unit]),
      ok(jsonResponse[Seq[PodInfo]])
    )
}

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