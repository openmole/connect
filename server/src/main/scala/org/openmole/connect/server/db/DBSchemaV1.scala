package org.openmole.connect.server.db

import better.files.*
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.server.tool.*
import org.openmole.connect.server.*
import org.openmole.connect.shared.Data

import slick.*
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api.*
import slick.model.ForeignKey

import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object DBSchemaV1:

  // USERS
  def dbVersion = 1

  import DB.*

  given BaseColumnType[EmailStatus] = MappedColumnType.base[EmailStatus, Int] (
    s => s.ordinal,
    v => EmailStatus.fromOrdinal(v)
  )


  given BaseColumnType[Role] = MappedColumnType.base[Role, Int] (
    s => s.ordinal,
    v => Role.fromOrdinal(v)
  )


  given BaseColumnType[UserStatus] = MappedColumnType.base[UserStatus, Int](
    s => s.ordinal,
    v => UserStatus.fromOrdinal(v)
  )

  case class User(
    name: String,
    firstName: String,
    email: Email,
    emailStatus: Data.EmailStatus,
    password: Password,
    institution: Institution,
    omVersion: Version,
    memory: Memory,
    cpu: Double,
    openMOLEMemory: Memory,
    lastAccess: Long,
    created: Long,
    role: Data.Role = Role.User,
    status: Data.UserStatus = Data.UserStatus.Active,
    uuid: UUID = randomUUID)

  case class RegisterUser(
    name: String,
    firstName: String,
    email: Email,
    password: Password,
    institution: Institution,
    created: Long = now,
    emailStatus: Data.EmailStatus = Data.EmailStatus.Unchecked,
    uuid: UUID = randomUUID,
    validationSecret: Secret = randomUUID)

  class Users(tag: Tag) extends Table[User](tag, "USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def firstName = column[String]("FIRST_NAME")

    def email = column[Email]("EMAIL", O.Unique)
    def emailStatus = column[Data.EmailStatus]("EMAIL_STATUS")

    def password = column[Password]("PASSWORD")
    def institution = column[Institution]("INSTITUTION")
    def role = column[Role]("ROLE")
    def status = column[UserStatus]("STATUS")
    def memory = column[Storage]("MEMORY_LIMIT")
    def cpu = column[Double]("CPU_LIMIT")
    def omMemory = column[Storage]("OPENMOLE_MEMORY")

    def omVersion = column[Version]("OPENMOLE_VERSION")

    def lastAccess = column[Long]("LAST_ACCESS")
    def created = column[Long]("CREATED")

    def * = (name, firstName, email, emailStatus, password, institution, omVersion, memory, cpu, omMemory, lastAccess, created, role, status, uuid).mapTo[User]
    def mailIndex = index("index_mail", email, unique = true)

  val userTable = TableQuery[Users]

  class RegisterUsers(tag: Tag) extends Table[RegisterUser](tag, "REGISTERING_USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def firstName = column[String]("FIRST_NAME")
    def email = column[Email]("EMAIL", O.Unique)
    def emailStatus = column[Data.EmailStatus]("EMAIL_STATUS")
    def password = column[Password]("PASSWORD")
    def institution = column[Institution]("INSTITUTION")
    def validationSecret = column[Secret]("VALIDATION_SECRET")
    def created = column[Long]("CREATED")

    def * = (name, firstName, email, password, institution, created, emailStatus, uuid, validationSecret).mapTo[RegisterUser]

  val registerUserTable = TableQuery[RegisterUsers]

  def upgrade =
    val schema = userTable.schema ++ registerUserTable.schema
    def upgrade = schema.createIfNotExists
    Upgrade(upgrade = upgrade, version = dbVersion)

