package org.openmole.connect.client

import sttp.tapir.client.sttp4.*
import sttp.client4.*
import sttp.tapir.PublicEndpoint
import org.openmole.connect.shared.{Data, TapirUserAPI, TapirAdminAPI, TapirAPI}

import concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class STTPInterpreter:

  lazy val sttpInterpreter = SttpClientInterpreter()
  lazy val backend = DefaultFutureBackend()

  def toRequest[I, E, O](basePath: Option[String], e: PublicEndpoint[I, E, O, Any])(i: I): Future[O] =
    val uri = basePath.map(u => sttp.model.Uri.pathRelative(u.split("/").toSeq))
    sttpInterpreter.toRequestThrowErrors(e, uri)(i).send(backend).flatMap: r =>
      Future.successful(r.body)

  def userAPIRequest[I, E, O](e: TapirUserAPI.type => PublicEndpoint[I, E, O, Any])(i: I): Future[O] =
    toRequest(Some(Data.userAPIRoute), e(TapirUserAPI))(i)

  def adminAPIRequest[I, E, O](e: TapirAdminAPI.type => PublicEndpoint[I, E, O, Any])(i: I): Future[O] =
    toRequest(Some(Data.adminAPIRoute), e(TapirAdminAPI))(i)

  def openAPIRequest[I, E, O](e: TapirAPI.type => PublicEndpoint[I, E, O, Any])(i: I): Future[O] =
    toRequest(Some(Data.openAPIRoute), e(TapirAPI))(i)
