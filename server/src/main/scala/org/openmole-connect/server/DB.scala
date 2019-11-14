package org.openmoleconnect.server

import java.util

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.H2Profile.api._

object DB {


  case class UUID(value: String) extends MappedTo[String]

  case class Login(value: String) extends MappedTo[String]

  case class Password(value: String) extends MappedTo[String]

  case class Role(value: String) extends MappedTo[String]

  val admin = Role("admin")
  val simpleUser = Role("simpleUser")

  case class User(login: Login, password: Password, email: String, role: Role = simpleUser, uuid: UUID = UUID(""))


  class Users(tag: Tag) extends Table[(UUID, Login, Password, String, Role)](tag, "USERS") {
    def uuid = column[UUID]("UUID", O.PrimaryKey)

    def login = column[Login]("LOGIN")

    def password = column[Password]("PASSWORD")

    def email = column[String]("EMAIL")

    def role = column[Role]("ROLE")

    def * = (uuid, login, password, email, role)
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
          case (uuid, login, password, email, role) => User(login, password, email, role, uuid)
        }
      }, Duration.Inf
    )

 // val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))


  def uuid(login: Login): Option[UUID] = users.find(_.login == login).map {
    _.uuid
  }

  def uuid(login: Login, password: Password): Option[UUID] = users.find(u => u.login == login && u.password == password).map {
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
      DB.addUser(DB.Login("admin"), DB.Password("admin"), "", DB.admin)
    }
  }

  def exists(email: String) = {
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

  def addUser(login: Login, password: Password, email: String, role: Role = simpleUser) = {

    if (!exists(email)) {
      runTransaction(
        userTable += (UUID(util.UUID.randomUUID().toString), login, password, email, role)
      )
    }
  }

}
