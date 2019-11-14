package org.openmoleconnect.server

import java.util

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.H2Profile.api._

object DB {


  case class UUID(value: String) extends MappedTo[String]

  case class Email(value: String) extends MappedTo[String]

  case class Password(value: String) extends MappedTo[String]

  case class Role(value: String) extends MappedTo[String]

  val admin = Role("admin")
  val simpleUser = Role("simpleUser")

  case class User(email: Email, password: Password, role: Role = simpleUser, uuid: UUID = UUID(""))


  class Users(tag: Tag) extends Table[(UUID, Email, Password, Role)](tag, "USERS") {
    def uuid = column[UUID]("UUID", O.PrimaryKey)

    def email = column[Email]("EMAIL")

    def password = column[Password]("PASSWORD")

    def role = column[Role]("ROLE")

    def * = (uuid, email, password, role)
  }

  val userTable = TableQuery[Users]

  val db: Database = Database.forDriver(
    driver = new org.h2.Driver,
    url = s"jdbc:h2:/${Settings.location}/db"
  )

  def users =
    Await.result(
      db.run(userTable.result).map { x =>
        x.map {
          case (uuid, email, password, role) => User(email, password, role, uuid)
        }
      }, Duration.Inf
    )

 // val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))


  def uuid(email: Email): Option[UUID] = users.find(_.email == email).map {
    _.uuid
  }

  def uuid(email: Email, password: Password): Option[UUID] = users.find(u => u.email == email && u.password == password).map {
    _.uuid
  }

  def runTransaction[E <: Effect](actions: DBIOAction[_, NoStream, E]*) =
    Await.result(
      db.run(
        DBIO.seq(
          actions: _*
        ).transactionally), Duration.Inf)

  def runQuery(query: TableQuery[DB.Users]) =
    Await.result(
      db.run(
        query.result
      ), Duration.Inf
    )

  def initDB = {
    runTransaction(userTable.schema.createIfNotExists)
    if (DB.users.isEmpty) {
      DB.addUser(DB.Email("admin@admin.com"), DB.Password("admin"), DB.admin)
    }
  }

  def exists(email: Email) = {
    Await.result(
      db.run(
        (for {
          u <- userTable if (u.email === email)
        } yield (u)).result
      ).map {
        _.length != 0
      }, Duration.Inf
    )
  }

  def addUser(email: Email, password: Password, role: Role = simpleUser) = {

    if (!exists(email)) {
      runTransaction(
        userTable += (UUID(util.UUID.randomUUID().toString), email, password, role)
      )
    }
  }

}
