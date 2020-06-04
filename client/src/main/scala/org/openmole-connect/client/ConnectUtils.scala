package org.openmoleconnect.client


import scalatags.JsDom.styles
import scaladget.bootstrapnative.bsn._
import scaladget.tools._
import scalatags.JsDom.all._

import scala.scalajs.js.Date

object ConnectUtils {

  val logoutLogo = toClass("glyphicon glyphicon-off")

  val itemStyle: ModifierSeq = Seq(
    fontSize := 30,
    pointer,
    color := "#337ab7"
  )

  def logoutItem =
    div(logoutLogo, itemStyle, onclick := { () ⇒ org.scalajs.dom.window.location.href = s"${org.scalajs.dom.window.location.href}logout" })

  implicit class FromDouble(i: Double) {
    def toDayString = i match {
      case 0 => "Mon"
      case 1 => "Tue"
      case 2 => "Wed"
      case 3 => "Thu"
      case 4 => "Fri"
      case 5 => "Sat"
      case 6 => "Sun"
      case _ => "ERROR"
    }

    def toMonthString = i match {
      case 0 => "Jan"
      case 1 => "Feb"
      case 2 => "Mar"
      case 3 => "Apr"
      case 4 => "May"
      case 5 => "Jun"
      case 6 => "Jul"
      case 7 => "Aug"
      case 8 => "Sept"
      case 9 => "Oct"
      case 10 => "Nov"
      case 11 => "Dec"
    }

    def toMinOrSecString = {
      if (i < 10 ) s"0$i"
      else i
    }
  }

  implicit class FromLong(date: Long) {
    def toStringDate = {
      if (date == 0L) "NEVER CONNECTED"
      else {
        val d = new Date(date)
        s"${d.getDay.toDayString}, ${d.getDate} ${d.getMonth.toMonthString} ${d.getFullYear} ${d.getHours}:${d.getMinutes.toMinOrSecString}:${d.getSeconds.toMinOrSecString}"
      }
    }
  }

}
