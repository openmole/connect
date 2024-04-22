package org.openmole.connect.shared

import java.util.UUID

object Data:
  val connectionRoute = "connection"
  val userAPIRoute = "user"
  val adminAPIRoute = "admin"
  val openMOLERoute = "openmole"
  val disconnectRoute = "disconnect"

  type Role = String
  val admin: Role = "Admin"
  val user: Role = "User"

  type EmailStatus = String
  val checked: EmailStatus = "Email checked"
  val unchecked: EmailStatus = "Email unchecked"

  object PodInfo:
    object Status:
      extension (s: Status)
        def value =
          s match
            case _: Running => "Running"
            case _: Waiting => "Waiting"
            case _: Terminated => "Terminated"

    enum Status:
      case Running(startedAt: Long) extends Status
      case Waiting(message: String) extends Status
      case Terminated(message: String, finishedAt: Long) extends Status

  case class PodInfo(
    name: String,
    status: Option[PodInfo.Status],
    restarts: Option[Int],
    createTime: Option[Long],
    podIP: Option[String],
    userEmail: Option[String])

  case class User(
    name: String,
    firstName: String,
    email: String,
    institution: String,
    role: Role,
    omVersion: String,
    storage: Int,
    memory: Int,
    cpu: Double,
    openMOLEMemory: Int,
    lastAccess: Long, created: Long)

  case class RegisterUser(
    uuid: String,
    name: String,
    firstName: String,
    email: String,
    institution: String,
    status: EmailStatus)

enum K8ActionResult:
  case K8Success(message: String) extends K8ActionResult
  case K8Failure(message: String, stackTrace: String) extends K8ActionResult
