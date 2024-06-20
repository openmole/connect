package org.openmole.connect.shared

import java.util.UUID

object Data:
  val connectionRoute = "connection"
  val registerRoute = "register"

  val userAPIRoute = "user"
  val adminAPIRoute = "admin"
  val openMOLERoute = "openmole"
  val disconnectRoute = "disconnect"
  val impersonateRoute = "impersonate"

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
            case _: Waiting => "Starting"
            case _: Terminating => "Terminating"
            case _: Terminated => "Terminated"
            case _: Inactive => "Inactive"

    enum Status:
      case Running(startedAt: Long) extends Status
      case Waiting(message: String) extends Status
      case Terminated(message: String, finishedAt: Long) extends Status
      case Terminating() extends Status
      case Inactive() extends Status

  case class PodInfo(
    name: String,
    status: Option[PodInfo.Status],
    restarts: Option[Int],
    createTime: Option[Long],
    podIP: Option[String],
    userEmail: Option[String])

  case class User(
    uuid: String,
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
  
  case class UserAndPodInfo(
     user: User, 
     podInfo: Option[PodInfo])
  
  case class RegisterUser(
    uuid: String,
    name: String,
    firstName: String,
    email: String,
    institution: String,
    status: EmailStatus)

  case class ConnectRegister(info1: String, info2: String)

enum K8ActionResult:
  case K8Success(message: String) extends K8ActionResult
  case K8Failure(message: String, stackTrace: String) extends K8ActionResult
