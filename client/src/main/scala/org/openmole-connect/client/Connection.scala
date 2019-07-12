package org.openmoleconnect.client

import java.nio.ByteBuffer

import boopickle.Default.{Pickle, Pickler, Unpickle}
import org.scalajs.dom
import scaladget.bootstrapnative.bsn._
import org.scalajs.dom.raw.HTMLFormElement
import scalatags.JsDom.all._
import scalatags.JsDom.tags

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import boopickle.Default._

import scala.collection.mutable
import shared.Data._


/*
 * Copyright (C) 11/07/19 // mathieu.leclaire@openmole.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object Connection {

  @JSExportTopLevel("connection")
  def connect() = {
    lazy val connectButton = tags.button("Connect", btn_primary, `type` := "submit").render

    val loginInput = inputTag("")(
      placeholder := "Login",
      width := "130px",
      marginBottom := 15,
      name := "login",
      autofocus := true
    ).render

    val passwordInput = inputTag("")(
      placeholder := "Password",
      name := "password",
      `type` := "password",
      width := "130px",
      marginBottom := 15
    ).render

    def cleanInputs = {
      passwordInput.value = ""
      loginInput.value = ""
    }

    val connectionForm: HTMLFormElement = form(
      method := "post",
      action := connectionRoute,
      loginInput,
      passwordInput,
      connectButton
    ).render

    val render = {
      div(
        div(css.connectionTabOverlay)(
          div(
            img(src := "img/logo.svg", css.openmoleLogo),
            div( marginLeft := 300)(
              connectionForm
            )
          )
        )
      ).render

    }

    dom.document.body.appendChild(render)
  }
}


object Post extends autowire.Client[ByteBuffer, Pickler, Pickler] {

  override def doCall(req: Request): Future[ByteBuffer] = {
    dom.ext.Ajax.post(
      url = req.path.mkString("/"),
      data = Pickle.intoBytes(req.args),
      responseType = "arraybuffer",
      headers = Map("Content-Type" -> "application/octet-stream")
    ).map(r => TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer]))
  }

  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)

  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

}
