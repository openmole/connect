package org.openmole.connect.client

import org.openmole.connect.shared.*
import org.openmole.connect.shared.Data.{PodInfo, RegisterUser, Role, User, UserAndPodInfo}
import com.raquo.laminar.api.L.*
import org.openmoleconnect.client.Css
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data
import com.raquo.laminar.nodes.ReactiveElement.isActive
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.shared.Data.PodInfo.Status.Running
import scaladget.bootstrapnative.Selector._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import scala.runtime.LazyVals.Waiting

object UIUtils:

  case class DetailedInfo(role: Role, omVersion: String, usedStorage: Option[Int], availableStorage: Int, memory: Int, cpu: Double, openMOLEMemory: Int)

  def toGB(size: Int, float: Boolean = false): String =
    val gs = size.toDouble / 1024
    if float
    then f"$gs%.2f"
    else s"${gs.round.toString}"

  def textBlock(title: String, text: String) =
    div(Css.centerColumnFlex,
      div(cls := "statusBlock",
        div(title, cls := "info"),
        div(text, cls := "infoContent")
      )
    )

  def badgeBlock(title: String, text: String) =
    div(Css.centerColumnFlex,
      div(cls := "statusBlock",
        div(title, cls := "info"),
        div(text, badge_info, cls := "userBadge")
      )
    )

  def memoryBar(title: String, value: Int, max: Int) =
    val bar1 = ((value.toDouble / max) * 100).floor.toInt
    val bar2 = 100 - bar1
    val memory: String = value.toString
    div(Css.columnFlex, justifyContent.spaceBetween, cls := "statusBlock barBlock",
      div(title, cls := "info"),
      div(cls := "stacked-bar-graph",
        span(width := s"${bar1}%", cls := "bar-1"),
        span(width := s"${bar2}%", cls := "bar-2")
      ),
      span(Css.centerColumnFlex, fontFamily := "gi", fontSize := "14", s"${toGB(value, float = true)}/${toGB(max)} GB")
    )

  def userInfoBlock(user: User, space: Var[Option[Storage]]) =
    div(
      child <-- space.signal.distinct.map: storage =>
        div(Css.centerRowFlex, justifyContent.center, padding := "30px",
          badgeBlock("Role", user.role.toString),
          textBlock("OpenMOLE version", user.omVersion),
          textBlock("CPU", user.cpu.toString),
          textBlock("Memory", s"${toGB(user.memory)} GB"),
          textBlock("OpenMOLE memory", s"${toGB(user.openMOLEMemory)} GB"),
          storage.toSeq.map: storage =>
            memoryBar("Storage", storage.used.toInt, (storage.used + storage.available).toInt)
        )
    )

  def mainPanel(panel: HtmlElement) =
    div(margin := "40px auto",
      img(src := "img/logo.png", Css.centerRowFlex, width := "500", margin.auto),
      a(cls := "bi-power power", href := s"/${Data.disconnectRoute}", Css.rowFlex),
      div(marginTop := "50px", panel)
    )

  def instanceFuture(uuid: Option[String]) =
    uuid match
      case Some(uuid) => AdminAPIClient.instance(uuid).future
      case None => UserAPIClient.instance(()).future

  class Switch(labelOn: String, labelOff: String, uuid: Option[String], initialState: Boolean):
    val isSet: Var[Boolean] = Var(initialState)

    val in: Input =
      input(
        `type` := "checkbox",
        checked <-- isSet.signal,
        onInput --> { _ =>
          isSet.set(in.ref.checked)
        }
      )

    val element = div(display.flex, flexDirection.row,
      div(
        child <-- isSet.signal.map(b =>
          if b
          then labelOn
          else labelOff),
        height := "34px", marginRight := "10px", display.flex, flexDirection.row, alignItems.center
      ),

      label(cls := "switch", in, span(cls := "slider round"))
    )

  def switch(labelsOn: String, labelsOff: String, uuid: Option[String], initialState: Boolean): Switch =
    Switch(labelsOn, labelsOff, uuid, initialState)

  def element(color: String) = div(cls := "statusCircle", background := color)

  def statusElement(status: Option[Data.PodInfo.Status]) =
    status match
      case Some(Data.PodInfo.Status.Running(_)) => element("#73AD21")
      case Some(Data.PodInfo.Status.Terminated(_, _)) => element("#D40000")
      case Some(Data.PodInfo.Status.Waiting(_) | Data.PodInfo.Status.Creating) => element("#73AD21").amend(cls := "blink_me")
      case Some(Data.PodInfo.Status.Terminating) => element("#D40000").amend(cls := "blink_me")
      case Some(Data.PodInfo.Status.Inactive) => element("#D40000")
      case None => element("#D40000")

  def statusLine(status: Option[PodInfo.Status]) =
    div(cls := "statusLine",
      div(
        status.map(_.value).getOrElse("Inactive"),
        cls := "badge"
      ),
      UIUtils.statusElement(status).amend(marginLeft := "10")
    )

  def launch(uuid: Option[String], status: Option[PodInfo.Status]) =
    status match
      case Some(PodInfo.Status.Running(_)) | Some(PodInfo.Status.Waiting(_)) =>
      case _ =>
        uuid match
          case None => UserAPIClient.launch(()).future
          case Some(uuid) => AdminAPIClient.launch((uuid)).future

  def stop(uuid: Option[String], status: Option[PodInfo.Status]) =
    status match
      case Some(PodInfo.Status.Terminated(_, _) | PodInfo.Status.Terminating) =>
      case _ =>
        uuid match
          case None => UserAPIClient.stop(()).future
          case Some(uuid) => AdminAPIClient.stop((uuid)).future

  def openmoleBoard(uuid: Option[String] = None, status: PodInfo.Status) =

    val waiting: Var[Boolean] = Var(false)

    val statusDiv =
      def statusSeq(status: PodInfo.Status, message: Option[String] = None) =
        Seq(
          statusLine(Some(status)),
          message.map(m => div(m, fontStyle.italic)).getOrElse(div()).amend(cls := "statusLine")
        )

      div(Css.columnFlex, justifyContent.flexEnd,
        status match
          case PodInfo.Status.Creating => statusSeq(PodInfo.Status.Creating)
          case PodInfo.Status.Terminating => statusSeq(PodInfo.Status.Terminating)
          case t: PodInfo.Status.Terminated => statusSeq(t, Some(s"Stopped since ${t.finishedAt.toStringDate}: ${t.message}"))
          case t: PodInfo.Status.Waiting => statusSeq(t, Some(t.message))
          case t: PodInfo.Status.Running => statusSeq(t, Some(t.startedAt.toStringDate))
          case PodInfo.Status.Inactive => div()
      )

    def impersonationLink(uuid: String) = a("Log as user", href := s"/${Data.impersonateRoute}?uuid=$uuid", cls := "statusLine", marginTop := "20")

    def isSwitchActivated(status: PodInfo.Status) =
      status match
        case _: PodInfo.Status.Waiting | _: PodInfo.Status.Running => true
        case _ => false

    lazy val sw = switch("Stop OpenMOLE", "Start OpenMOLE", uuid, isSwitchActivated(status))

    div(
      sw.isSet.signal --> {
        case true =>
          status match
            case PodInfo.Status.Terminating | _: PodInfo.Status.Terminated | PodInfo.Status.Inactive =>
              waiting.set(true)
              launch(uuid, None)
            case _ =>
        case false =>
          status match
            case _: PodInfo.Status.Waiting | _: PodInfo.Status.Running | PodInfo.Status.Creating =>
              waiting.set(true)
              stop(uuid, Some(status))
            case _ =>
      },
      div(
        child <--
          waiting.signal.map:
            case true => div(Css.rowFlex, justifyContent.flexEnd, marginRight := "30", waiter)
            case false =>
              div(
                Css.columnFlex, justifyContent.flexEnd,
                sw.element.amend(Css.rowFlex, justifyContent.flexEnd, marginRight := "30"),
                statusDiv.amend(marginTop := "20")
              )
      ),
      div(Css.rowFlex, justifyContent.flexEnd,
        uuid match
          case None =>
            status match
              case _: PodInfo.Status.Running => a("Go to OpenMOLE", href := s"/${Data.openMOLERoute}/", cls := "statusLine", marginTop := "20", target := "_blank")
              case _ => div()
          case Some(uuid) => impersonationLink(uuid)
      )
    )


  def versionChanger(currentVersion: String, availableVersions: Seq[String]) =
    lazy val versionChanger: Options[String] =
      availableVersions.options(
        availableVersions.indexOf(currentVersion),
        Seq(cls := "btn btnUser", width := "160"),
        (m: String) => m,
        onclose = () => UserAPIClient.setOpenMOLEVersion(versionChanger.content.now().get).future
      )

    versionChanger.selector


  def buildInput(attr: String) = inputTag("")
    .amend(
      nameAttr := attr,
      placeholder := attr,
      cls := "formField"
    )


  case class Settings(element: HtmlElement, save: () => Unit)

  def settings(uuid: String): Settings =
    lazy val in: Input = UIUtils.buildInput("New password").amend(
      `type` := "password",
      cls := "inPwd"
    )

    Settings(
      div(margin := "30",
        Css.columnFlex,
        in
      ),
      () =>
        val pwd = in.ref.value
        if (!pwd.isEmpty)
        then AdminAPIClient.changePassword(uuid, in.ref.value)
    )


  def waiter = span(cls := "loader")

  def longTimeToString(lg: Long): String =
    val date = new scalajs.js.Date(lg)
    s"${date.toLocaleDateString} ${date.toLocaleTimeString.dropRight(3)}"
