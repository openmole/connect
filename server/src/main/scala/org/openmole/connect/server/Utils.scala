package org.openmole.connect.server

import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.config.SocketConfig
import org.apache.http.impl.EnglishReasonPhraseCatalog
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}

import java.io.{PrintWriter, StringWriter}
import java.text.SimpleDateFormat
import java.util.Locale
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration


extension [T](inline f: scala.concurrent.Future[T])
  inline def await: T = Await.result(f, Duration.Inf)

object Utils:

  def buildHttpClient() =
    HttpClientBuilder.create().setDefaultSocketConfig(socketConfig()).build()

  def socketConfig(timeout: Int = 60000) =
    SocketConfig.custom().setSoTimeout(timeout).build()

  implicit class ST(throwable: Throwable):
    def toStackTrace =
      val sw = new StringWriter()
      throwable.printStackTrace(new PrintWriter(sw))
      sw.toString


  def now = System.currentTimeMillis()

  def hash(v: String, salt: String) =
    val shaHex: String = DigestUtils.sha256Hex(salt + v)
    s"sha256:$shaHex"

  def tags(group: String, image: String, pageSize: Int = 100): Seq[String] =
    import org.json4s.*
    import org.json4s.jackson.JsonMethods.*

    val httpClient = buildHttpClient()
    try
      val httpGet = new HttpGet(s"https://hub.docker.com/v2/namespaces/$group/repositories/$image/tags?page_size=$pageSize")
      val response = httpClient.execute(httpGet)
      try
        val json = parse(response.getEntity.getContent)
        (json \\ "name").children.map(_.values.toString)
      finally response.close()
    finally httpClient.close()

