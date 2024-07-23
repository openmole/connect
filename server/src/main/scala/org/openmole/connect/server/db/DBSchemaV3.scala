package org.openmole.connect.server.db

import org.openmole.connect.server.*
import org.openmole.connect.server.tool.*
import org.openmole.connect.shared.Data
import slick.*
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api.*

object DBSchemaV3:
  def dbVersion = 3

  import Data.*
  import DB.{Email, Password, Institution, Version, Memory, UUID, Secret, Upgrade, Storage, SecretType, randomUUID}

  export DBSchemaV2.{User, RegisterUser, Users, RegisterUsers, userTable, registerUserTable, DatabaseInfo, databaseInfoTable, given}

  given BaseColumnType[SecretType] = MappedColumnType.base[SecretType, Int](
    s => s.ordinal,
    v => SecretType.fromOrdinal(v)
  )

  case class ValidationSecret(
    uuid: UUID,
    secret: Secret,
    creationTime: Long = tool.now,
    `type`: SecretType = SecretType.Email)

  class ValidationSecrets(tag: Tag) extends Table[ValidationSecret](tag, "VALIDATION_SECRETS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def validationSecret = column[DB.Secret]("VALIDATION_SECRET")
    def creationTime = column[Long]("CREATION_TIME")
    def secretType = column[SecretType]("TYPE")

    def * = (uuid, validationSecret, creationTime, secretType).mapTo[ValidationSecret]

  val validationSecretTable = TableQuery[ValidationSecrets]

  def upgrade =
    val modif =
      sqlu"""
        ALTER TABLE VALIDATION_SECRETS ADD CREATION_TIME LONG;
        ALTER TABLE VALIDATION_SECRETS ADD TYPE INT;"""

    val now = tool.now
    val fill = validationSecretTable.map(v => (v.creationTime, v.secretType)).update((now, SecretType.Email))

    Upgrade(
      upgrade = DBIO.seq(modif, fill),
      version = dbVersion
    )


