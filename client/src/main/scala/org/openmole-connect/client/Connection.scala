package org.openmoleconnect.client

//import java.nio.ByteBuffer

//import boopickle.Default.{Pickle, Pickler, Unpickle}
import fr.hmil.roshttp.body.URLEncodedBody
import org.scalajs.dom
import scaladget.bootstrapnative.bsn._
import org.scalajs.dom.raw.{Event, HTMLFormElement}
import scalatags.JsDom.all._
import scalatags.JsDom.tags

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
//import boopickle.Default._

import fr.hmil.roshttp.HttpRequest
import monix.execution.Scheduler.Implicits.global
import scala.util.{Failure, Success}
import fr.hmil.roshttp.response.SimpleHttpResponse

import scala.collection.mutable


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

    def request = () => {
      val login = loginInput.value
      val password = passwordInput.value

      val httpRequest = HttpRequest("http://localhost:8180/auth/realms/test-kube/protocol/openid-connect/token")

      val urlEncodedData = URLEncodedBody(
        "client_id" -> "test-kube",
        "client_secret" -> "6e824d88-b17e-4534-8ed6-f69ba5f29845",
        "response_type"-> "code token",
        "grant_type" -> "password",
        "username" -> login,
        "password" -> password,
        "scope" -> "openid"
      )

      httpRequest.post(urlEncodedData).onComplete({
        case res: Success[SimpleHttpResponse] =>
          println(res.get.body)

          // Redirection
          dom.document.location.href = "https://iscpif.fr/"
        case e: Failure[SimpleHttpResponse] => println("Houston, we got a problem!")
          println(e)
      })
    }

    lazy val connectButton = tags.button("Connect", btn_primary, `type` := "submit", onclick := request).render

    lazy val loginInput = inputTag("")(
      placeholder := "Login",
      width := "130px",
      marginBottom := 15,
      autofocus := true
    ).render

    lazy val passwordInput = inputTag("")(
      placeholder := "Password",
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
      action := "#",
      loginInput,
      passwordInput,
      connectButton,
      onsubmit := { (e: Event) =>
        e.preventDefault()
        request
      }
    ).render

    val render = {
      div(
        div(css.connectionTabOverlay)(
          div(
            img(src := "img/logo.svg", css.openmoleLogo),
            div(marginLeft := 300)(
              connectionForm
            )
          )
        )
      ).render

    }

    dom.document.body.appendChild(render)
  }
}


//object Post extends autowire.Client[ByteBuffer, Pickler, Pickler] {
//
//  override def doCall(req: Request): Future[ByteBuffer] = {
//    dom.ext.Ajax.post(
//      url = req.path.mkString("/"),
//      data = Pickle.intoBytes(req.args),
//      responseType = "arraybuffer",
//      headers = Map("Content-Type" -> "application/octet-stream")
//    ).map(r => TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer]))
//  }
//
//  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
//
//  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)
//
//}
