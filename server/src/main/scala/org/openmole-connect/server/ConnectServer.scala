package org.openmoleconnect.server

import java.net.URI
import java.util

import javax.servlet.ServletContext
import org.eclipse.jetty.server.{Server, ServerConnector}
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.LifeCycle
import org.scalatra.servlet.ScalatraListener

object ConnectServer {
  val servletArguments = "servletArguments"

  case class ServletArguments(secret: String, resourceBase: java.io.File)

}

class ConnectBootstrap extends LifeCycle {
  override def init(context: ServletContext) = {
    val args = context.get(ConnectServer.servletArguments).get.asInstanceOf[ConnectServer.ServletArguments]
    context mount(new ConnectServlet(args), "/*")
  }
}

class ConnectServer(secret: String) {


  def start() = {

    val server = new Server()
    val connector = new ServerConnector(server)
    connector.setPort(8080)
    server.addConnector(connector)

    val startingContext = new WebAppContext()
    startingContext.setResourceBase("application/target/webapp")
    startingContext.setAttribute(ConnectServer.servletArguments, ConnectServer.ServletArguments(secret, new java.io.File(new URI(startingContext.getResourceBase))))
    startingContext.setInitParameter(ScalatraListener.LifeCycleKey, classOf[ConnectBootstrap].getCanonicalName)
    startingContext.setContextPath("/")
    startingContext.addEventListener(new ScalatraListener)
    server.setHandler(startingContext)

    server.stop()
    server.setHandler(startingContext)
    server.start()
  }
}
