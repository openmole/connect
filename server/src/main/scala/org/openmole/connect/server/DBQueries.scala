package org.openmole.connect.server

import org.openmole.connect.server.DB.*
import slick.jdbc.H2Profile.api.*
import slick.lifted.Query

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object DBQueries:
  type UserQuery = Query[Users, User, Seq]
  type RegisteringUserQuery = Query[RegisteringUsers, RegisteringUser, Seq]

  def runUserQuery(query: UserQuery): Seq[User] =
    await:
      db.run(query.result)
  

  def runRegisteringUserQuery(query: RegisteringUserQuery): Seq[RegisteringUser] =
    await:
      db.run(query.result)
   // .map { case (u, n, e, p, r, v, s, l) => User(n, e, p, v, s, l, r, u) }

  def await[A](f: concurrent.Future[A]) =
    Await.result(f, Duration.Inf)


  // Query statements
  def queryUser(email: Email): UserQuery =
    for
      u <- userTable if u.email === email
    yield u

  def getLastAccesQuery(email: Email) =
    for {
      u <- userTable if (u.email === email)
    } yield (u.lastAccess)

