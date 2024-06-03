package org.openmole.connect.client

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

  def toGB(size: Int): String = s"${(size.toDouble / 1024).round.toString}"

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
      span(Css.centerColumnFlex, fontFamily := "gi", fontSize := "14", s"${toGB(value)}/${toGB(max)} GB")
    )

  def userInfoBlock(user: User) =
    div(
      child <-- Signal.fromFuture(AdminAPIClient.usedSpace(user.uuid).future).map: v =>
        div(Css.centerRowFlex, justifyContent.center, padding := "30px",
          badgeBlock("Role", user.role),
          textBlock("OpendMOLE version", user.omVersion),
          textBlock("CPU", user.cpu.toString),
          textBlock("Memory", toGB(user.memory)),
          textBlock("OpenMOLE memory", toGB(user.openMOLEMemory)),
          //FIXME use another color when used storage is not set
          memoryBar("Storage", v.flatten.map(_.toInt).getOrElse(0), user.storage),
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
      case Some(Data.PodInfo.Status.Waiting(_)) => element("#73AD21").amend(cls := "blink_me")
      case Some(Data.PodInfo.Status.Terminating()) => element("#D40000").amend(cls := "blink_me")
      case Some(Data.PodInfo.Status.Inactive()) => element("#D40000")
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
      case Some(PodInfo.Status.Terminated(_, _) | PodInfo.Status.Terminating()) =>
      case _ =>
        uuid match
          case None => UserAPIClient.stop(()).future
          case Some(uuid) => AdminAPIClient.stop((uuid)).future

  def openmoleBoard(uuid: Option[String] = None, initialPodInfo: Option[PodInfo]) =
    val podInfo: Var[Option[PodInfo]] = Var(initialPodInfo)

    val statusDiv =
      def statusSeq(status: PodInfo.Status, message: Option[String] = None) =
        Seq(
          statusLine(Some(status)),
          message.map(m => div(m, fontStyle.italic)).getOrElse(div()).amend(cls := "statusLine")
        )

      div(Css.columnFlex, justifyContent.flexEnd,
        children <--
          podInfo.signal.map:
            case None => statusSeq(PodInfo.Status.Inactive())
            case Some(podInfo) =>
              podInfo.status match
                case Some(t: PodInfo.Status.Terminating) => statusSeq(t)
                case Some(t: PodInfo.Status.Terminated) => statusSeq(t, Some(s"Stopped since ${t.finishedAt.toStringDate}: ${t.message}"))
                case Some(t: PodInfo.Status.Waiting) => statusSeq(t, Some(t.message))
                case Some(t: PodInfo.Status.Running) => statusSeq(t, Some(t.startedAt.toStringDate))
                case None | Some(_: PodInfo.Status.Inactive) => statusSeq(PodInfo.Status.Inactive())

      )

    def impersonationLink(uuid: String) = a("Log as user", href := s"/${Data.impersonateRoute}?uuid=$uuid", cls := "statusLine", marginTop := "20")

    def isSwitchActivated(status: Option[PodInfo.Status]) =
      status match
        case Some(_: PodInfo.Status.Waiting | _: PodInfo.Status.Running) => true
        case _ => false

    lazy val sw = switch("Stop OpenMOLE", "Start OpenMOLE", uuid, isSwitchActivated(initialPodInfo.flatMap(_.status)))

    div(
      EventStream.periodic(5000).toObservable -->
        Observer: _ =>
          instanceFuture(uuid).foreach(podInfo.set),
      div(
        sw.isSet.signal.combineWith(podInfo.signal.map(_.flatMap(_.status))) --> {
          case (true, x) =>
            x match
              case Some(_: PodInfo.Status.Terminating | _: PodInfo.Status.Waiting | _: PodInfo.Status.Running) =>
              case _ => launch(uuid, None)
          case (false, Some(x)) => stop(uuid, Some(x))
          case x: Any =>
        },
        div(
          Css.columnFlex, justifyContent.flexEnd,
          sw.element.amend(Css.rowFlex, justifyContent.flexEnd, marginRight := "30"),
          statusDiv.amend(marginTop := "20")
        ),
        div(Css.rowFlex, justifyContent.flexEnd,
          child <--
            podInfo.signal.map:
              case Some(podInfo) =>
                podInfo.status match
                  case Some(_: PodInfo.Status.Terminating | _: PodInfo.Status.Terminated | _: PodInfo.Status.Waiting) | None =>
                    uuid match
                      case None => div()
                      case Some(uuid) => impersonationLink(uuid)
                  case _ =>
                    uuid match
                      case None => a("Go to OpenMOLE", href := s"/${Data.openMOLERoute}/", cls := "statusLine", marginTop := "20")
                      case Some(uuid) => impersonationLink(uuid)
              case _ =>
                uuid match
                  case None => a()
                  case Some(uuid) => impersonationLink(uuid)
        )
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


  def waiter =
    div(Css.centerColumnFlex,
      cls := "loading",
      div(
        cls := "loading-text",
        Seq("L", "O", "A", "D", "I", "N", "G").map: letter =>
          span(cls := "loading-text-words", letter)
      )
    )