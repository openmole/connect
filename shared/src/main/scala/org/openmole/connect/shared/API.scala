package org.openmole.connect.shared

import io.circe.*
import io.circe.generic.auto.*

import io.circe.*
import io.circe.generic.auto.*

object TapirAPI:

  import sttp.tapir.*
  import sttp.model.*
  import sttp.tapir.generic.auto.*
  import sttp.tapir.json.circe.*


  lazy val institutions = endpoint.get.in("institutions").out(jsonBody[Seq[String]])
  lazy val signup = endpoint.post.in("signup").in(jsonBody[(String, String, String, String, String, String)]).out(jsonBody[Option[String]])
  lazy val askResetPassword = endpoint.post.in("ask-reset-password").in(jsonBody[(String, String)]).out(jsonBody[Option[String]])
  lazy val resetPassword = endpoint.post.in("reset-password").in(jsonBody[(String, String, String)]).out(jsonBody[Option[String]])


//trait API
//  extends algebra.Endpoints
//    with algebra.circe.JsonEntitiesFromCodecs
//    with circe.JsonSchemas:
//
//  val institutions: Endpoint[Unit, Seq[String]] =
//    endpoint(
//      get(path / "institutions"),
//      ok(jsonResponse[Seq[String]])
//    )
//
//  val signup: Endpoint[(String, String, String, String, String, String), Option[String]] =
//    endpoint(
//      post(path / "signup", jsonRequest[(String, String, String, String, String, String)]),
//      ok(jsonResponse[Option[String]])
//    )
//
//  val askResetPassword: Endpoint[(String, String), Option[String]] =
//    endpoint(
//      post(path / "ask-reset-password", jsonRequest[(String, String)]),
//      ok(jsonResponse[Option[String]])
//    )
//
//  val resetPassword: Endpoint[(String, String, String), Option[String]] =
//    endpoint(
//      post(path / "reset-password", jsonRequest[(String, String, String)]),
//      ok(jsonResponse[Option[String]])
//    )
