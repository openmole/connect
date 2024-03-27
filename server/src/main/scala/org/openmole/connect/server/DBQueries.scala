package org.openmole.connect.server

import org.openmole.connect.server.DB.*
import shared.*
import slick.jdbc.H2Profile.api.*
import slick.lifted.Query

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object DBQueries {
  type UserQuery = Query[Users, (UUID, String, Email, Password, Role, Version, Storage, Long), Seq]

  def runQuery(query: UserQuery) =
    Await.result(
      db.run(
        query.result
      ), Duration.Inf
    ).map { case (u, n, e, p, r, v, s, l) => User(n, e, p, v, s, l, r, u) }

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
