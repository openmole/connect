package org.openmole.connect.client

import org.openmole.connect.shared.Data.{PodInfo, RegisterUser, Role}
import com.raquo.laminar.api.L.*
import org.openmoleconnect.client.Css
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data
import com.raquo.laminar.nodes.ReactiveElement.isActive
import org.openmole.connect.client.ConnectUtils.*

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID

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

  def userInfoBlock(detailedInfo: DetailedInfo) =
    div(Css.centerRowFlex, justifyContent.center, padding := "30px",
      badgeBlock("Role", detailedInfo.role),
      textBlock("OpendMOLE version", detailedInfo.omVersion),
      textBlock("CPU", detailedInfo.cpu.toString),
      textBlock("Memory", toGB(detailedInfo.memory)),
      textBlock("OpenMOLE memory", toGB(detailedInfo.openMOLEMemory)),
      //FIXME use another color when used storage is not set
      memoryBar("Storage", detailedInfo.usedStorage.getOrElse(0), detailedInfo.availableStorage),
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

  class Switch(labelOn: String, labelOff: String, uuid: Option[String]):
    def toBoolean(opt: Option[Boolean]): Boolean = opt.getOrElse(false)

    lazy val isSet: Var[Option[Boolean]] = Var(None)

    instanceFuture(uuid).foreach: x =>
      isSet.set:
        x match
        case None => None
        case Some(pi) =>
          pi.status match
            case Some(_: PodInfo.Status.Running | _: PodInfo.Status.Waiting) => Some(true)
            case _ => Some(false)

    //lazy val isTriggered: Var[Option[Boolean]] = Var(None)

    val in: Input =
      input(
        `type` := "checkbox", checked <-- isSet.signal.map(toBoolean),
        onInput --> { _ =>
          isSet.set(Some(in.ref.checked))
        }
      )

    val element = div(display.flex, flexDirection.row,
      div(
        child <-- isSet.signal.map(b =>
          if toBoolean(b)
          then labelOn
          else labelOff),
        height := "34px", marginRight := "10px", display.flex, flexDirection.row, alignItems.center
      ),

      label(cls := "switch", in, span(cls := "slider round"))
    )

  def switch(labelsOn: String, labelsOff: String, uuid: Option[String]): Switch =
    Switch(labelsOn, labelsOff, uuid)

  def element(color: String) = div(cls := "statusCircle", background := color)

  def statusElement(status: Option[Data.PodInfo.Status]) =
    status match
      case Some(Data.PodInfo.Status.Running(_)) => element("#73AD21")
      case Some(Data.PodInfo.Status.Terminated(_, _)) => element("#D40000")
      case Some(Data.PodInfo.Status.Waiting(_)) => element("#73AD21").amend(cls := "blink_me")
      case Some(Data.PodInfo.Status.Terminating()) => element("#D40000").amend(cls := "blink_me")
      case None => element("#D40000")

  def openmoleBoard(uuid: Option[String] = None) =
    val podInfo: Var[Option[PodInfo]] = Var(None)

    val statusDiv =
      def statusSeq(status: PodInfo.Status, message: Option[String] = None) =
        Seq(
          div(cls := "statusLine",
            div(
              status.value,
              cls := "badge"
            ),
            UIUtils.statusElement(Some(status)).amend(marginLeft := "10")
          ),
          message.map(m => div(m, fontStyle.italic)).getOrElse(div()).amend(cls := "statusLine")
        )

      div(Css.columnFlex, justifyContent.flexEnd,
        children <--
          podInfo.signal.map:
              case None => statusSeq(PodInfo.Status.Terminated("", 0L))
              case Some(podInfo) =>
                podInfo.status match
                  case Some(t: PodInfo.Status.Terminating) => statusSeq(t)
                  case Some(t: PodInfo.Status.Terminated) => statusSeq(t, Some(s"Stopped since ${t.finishedAt.toStringDate}: ${t.message}"))
                  case Some(t: PodInfo.Status.Waiting) => statusSeq(t, Some(t.message))
                  case Some(t: PodInfo.Status.Running) => statusSeq(t, Some(t.startedAt.toStringDate))
                  case None => statusSeq(PodInfo.Status.Terminated("", 0L))

      )

    def impersonationLink(uuid: String) = a("Log as user", href := s"/${Data.impersonateRoute}?uuid=$uuid", cls := "statusLine", marginTop := "20")

    val sw = switch("Stop OpenMOLE", "Start OpenMOLE", uuid)

    div(
      EventStream.periodic(5000).toObservable -->
        Observer: _ =>
          instanceFuture(uuid).foreach(podInfo.set),
      div(
        sw.isSet.signal --> {
          case Some(true) =>
            uuid match
              case None => UserAPIClient.launch(()).future
              case Some(uuid) => AdminAPIClient.launch((uuid)).future
          case Some(false) =>
            uuid match
              case None => UserAPIClient.stop(()).future
              case Some(uuid) => AdminAPIClient.stop((uuid)).future
          case None =>
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

  def waiter =
    div(Css.centerColumnFlex,
      cls := "loading",
      div(
        cls := "loading-text",
        Seq("L", "O", "A", "D", "I", "N", "G").map: letter =>
          span(cls := "loading-text-words", letter)
      )
    )