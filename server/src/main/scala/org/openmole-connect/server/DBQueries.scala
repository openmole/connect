package org.openmoleconnect.server

import org.openmoleconnect.server.DB._
import slick.lifted.Query
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import shared._

object DBQueries {
  type UserQuery = Query[Users, (UUID, String, Email, Password, Role, Version, Long), Seq]

  def runQuery(query: UserQuery) =
    Await.result(
      db.run(
        query.result
      ), Duration.Inf
    ).map { case (u, n, e, p, r, v, l) => User(n, e, p, v, l, r, u) }

  // Query statements
  def getQuery(email: Email) =
    for {
      u <- userTable if (u.email === email)
    } yield (u)

  def getLastAccesQuery(email: Email) =
    for {
      u <- userTable if (u.email === email)
    } yield (u.lastAccess)

}
