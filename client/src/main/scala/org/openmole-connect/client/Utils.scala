package org.openmoleconnect.client


import scalatags.JsDom.styles
import scaladget.bootstrapnative.bsn._
import scaladget.tools._
import scalatags.JsDom.all._

object Utils {

  val logoutLogo = toClass("glyphicon glyphicon-off")

  val itemStyle: ModifierSeq = Seq(
    fontSize := 30,
    pointer,
    color := "#337ab7"
  )

  val logoutItem =
    div(logoutLogo, itemStyle, onclick := { () ⇒ org.scalajs.dom.window.location.href = s"${org.scalajs.dom.window.location.href}logout" })

}
