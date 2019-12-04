package org.openmoleconnect.server

import org.openmoleconnect.server.DB._
import slick.lifted.Query
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import shared._

object DBQueries {
  type UserQuery = Query[Users, (UUID, String, Email, Password, Role), Seq]

  def runQuery(query: UserQuery) =
    Await.result(
      db.run(
        query.result
      ), Duration.Inf
    ).map { case (u, n, e, p, r) => User(n, e, p, r, u) }

  def exists(email: Email) = get(email).isDefined

  def isAdmin(email: Email) = get(email).map{_.role} == Some(admin)

  def get(email: Email) = {
    runQuery(
      for {
        u <- userTable if (u.email === email)
      } yield (u)
    ).headOption
  }

  def users = runQuery(
    for {
      u <- userTable
    } yield (u)
  )

}
