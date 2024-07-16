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
