package org.openmoleconnect.adminclient

import org.scalajs.dom
import scaladget.bootstrapnative.bsn._
import org.scalajs.dom.raw.{Event, HTMLFormElement}
import scalatags.JsDom.all._
import scalatags.JsDom.tags

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel

object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {
    dom.document.body.appendChild(div("Admin").render)
  }
}
