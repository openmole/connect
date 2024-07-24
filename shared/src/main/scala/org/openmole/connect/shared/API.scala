package org.openmole.connect.shared

import endpoints4s.{algebra, circe}
import io.circe.*
import io.circe.generic.auto.*
import org.openmole.connect.shared.Data.User


trait API
  extends algebra.Endpoints
    with algebra.circe.JsonEntitiesFromCodecs
    with circe.JsonSchemas:

  val institutions: Endpoint[Unit, Seq[String]] =
    endpoint(
      get(path / "institutions"),
      ok(jsonResponse[Seq[String]])
    )

  val signup: Endpoint[(String, String, String, String, String, String), Option[String]] =
    endpoint(
      post(path / "signup", jsonRequest[(String, String, String, String, String, String)]),
      ok(jsonResponse[Option[String]])
    )

  val askResetPassword: Endpoint[(String, String), Option[String]] =
    endpoint(
      post(path / "ask-reset-password", jsonRequest[(String, String)]),
      ok(jsonResponse[Option[String]])
    )

  val resetPassword: Endpoint[(String, String, String), Option[String]] =
    endpoint(
      post(path / "reset-password", jsonRequest[(String, String, String)]),
      ok(jsonResponse[Option[String]])
    )
