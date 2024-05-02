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

  class Switch(labelOn: String, labelOff: String, podInfo: Option[PodInfo]):

    val isActivated: Var[Boolean] =
      Var(
        podInfo match
          case None => false
          case Some(pi) =>
            pi.status match
              case Some(t: PodInfo.Status.Running) => true
              case _ => false
      )

    private lazy val in: Input =
      input(
        `type` := "checkbox", checked := isActivated.now(),
        onInput --> { _ => isActivated.set(in.ref.checked) }
      )

    val element = div(display.flex, flexDirection.row,
      div(
        child <-- isActivated.signal.map(b => if (b) labelOn else labelOff),
        height := "34px", marginRight := "10px", display.flex, flexDirection.row, alignItems.center
      ),
      label(cls := "switch",
        in,
        span(cls := "slider round"
        )
      )
    )

  def switch(labelsOn: String, labelsOff: String, podInfo: Option[PodInfo]): Switch =
    Switch(labelsOn, labelsOff, podInfo)

  def element(color: String) = div(cls := "statusCircle", background := color)

  val terminatedStatusElement = element("#D40000")

  def statusElement(status: Option[Data.PodInfo.Status]) =
    status match
      case Some(Data.PodInfo.Status.Running(_)) => element("#73AD21")
      case Some(Data.PodInfo.Status.Terminated(_, _)) => terminatedStatusElement
      case Some(Data.PodInfo.Status.Waiting(_)) => element("#73AD21").amend(cls := "blink_me")
      case Some(Data.PodInfo.Status.Terminating()) => element("#D40000").amend(cls := "blink_me")
      case None => terminatedStatusElement

  def openmoleBoard(uuid: String) =

    val podInfo: Var[Option[PodInfo]] = Var(None)
    UserAPIClient.instance(()).future.foreach(podInfo.set)

    val sw = switch("Stop OpenMOLE", "Start OpenMOLE", podInfo.now())

    div(Css.columnFlex, justifyContent.flexEnd,
      EventStream.periodic(5000).toObservable -->
        Observer: _ =>
          UserAPIClient.instance(()).future.foreach(podInfo.set),
      div(
        sw.isActivated.signal --> {
          _ match
            case true =>
              UserAPIClient.launch(()).future.foreach { pi =>
                podInfo.set(pi)
              }
            case false => UserAPIClient.stop(()).future.foreach(podInfo.set)
        },
        child <--
          podInfo.signal.map: pi =>
            val statusDiv =
              pi match
                case None => div("Terminated", UIUtils.terminatedStatusElement)
                case Some(podInfo) =>
                  podInfo.status match
                    case Some(t: PodInfo.Status.Terminating) => div(div("Terminating", cls := "badge"), marginRight := "60", UIUtils.statusElement(Some(t)).amend(marginLeft := "10"))
                    case Some(t: PodInfo.Status.Terminated) => div(div(cls := "badge", s"OpenMOLE is stopped since ${t.finishedAt}: ${t.message}"), marginRight := "60", UIUtils.statusElement(Some(t)).amend(marginLeft := "10"))
                    case Some(t: PodInfo.Status.Waiting) => div(div("Starting", cls := "badge"), t.message, UIUtils.statusElement(Some(t)).amend(marginLeft := "10"))
                    case _ =>
                      podInfo.podIP match
                        case Some(_) => div(div("Started", cls := "badge"), marginRight := "60", UIUtils.statusElement(podInfo.status).amend(marginLeft := "10"))
                        case None => div(div("Terminated", cls := "badge"), marginRight := "60", UIUtils.statusElement(podInfo.status).amend(marginLeft := "10"))

            div(
              Css.columnFlex,
              sw.element.amend(Css.rowFlex, justifyContent.flexEnd, marginRight := "30"),
              statusDiv.amend(Css.rowFlex, justifyContent.flexEnd, marginRight := "30")
            )
      ),
      div(Css.rowFlex, justifyContent.flexEnd,
        child <--
          podInfo.signal.map:
            case Some(podInfo) =>
              podInfo.status match
                case Some(_: PodInfo.Status.Terminating | _: PodInfo.Status.Terminated | _: PodInfo.Status.Waiting) | None => div()
                case _ => a("Go to OpenMOLE", href := s"/${Data.openMOLERoute}/")
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