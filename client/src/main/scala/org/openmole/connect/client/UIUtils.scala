package org.openmole.connect.client

import org.openmole.connect.shared.Data.{RegisterUser, Role}
import com.raquo.laminar.api.L.*
import org.openmoleconnect.client.Css
import scaladget.bootstrapnative.bsn.*

object UIUtils {

  case class DetailedInfo(role: Role, omVersion: String, usedStorage: Int, availableStorage: Int, memory: Int, cpu: Double, openMOLEMemory: Int)

  implicit def toGo(size: Int): String = s"${(size.toDouble / 1024).round.toString} Go"

  def textBlock(title: String, text: String) =
    div(Css.columnFlex,
      div(cls := "statusBlock",
        div(title, cls := "info"),
        div(text, cls := "infoContent")
      )
    )

  def badgeBlock(title: String, text: String) =
    div(Css.columnFlex,
      div(cls := "statusBlock",
        div(title, cls := "info"),
        div(text, badge_info, cls := "userBadge")
      )
    )

  def memoryBar(title: String, value: Int, max: Int) =
    val bar1 = (value.toDouble * 100 / max).toInt
    val bar2 = 100 - bar1
    val memory: String = value
    div(Css.columnFlex, cls := "statusBlock barBlock",
      div(title, cls := "info"),
      div(cls := "stacked-bar-graph", marginTop := "10px",
        span(width := s"${bar1}%", cls := "bar-1", memory),
        span(width := s"${bar2}%", cls := "bar-2"),
      )
    )

  def userInfoBlock(detailedInfo: DetailedInfo) =
    div(Css.rowFlex, justifyContent.center, padding := "10px",
      badgeBlock("Role", detailedInfo.role),
      textBlock("OpendMOLE version", detailedInfo.omVersion),
      textBlock("CPU", detailedInfo.cpu.toString),
      textBlock("Memory", detailedInfo.memory),
      textBlock("OpenMOLE memory", detailedInfo.openMOLEMemory),
      memoryBar("Storage", detailedInfo.usedStorage, detailedInfo.availableStorage),
    )

}
