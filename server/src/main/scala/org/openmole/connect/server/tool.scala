package org.openmole.connect.server

import com.google.common.cache.CacheBuilder
import org.apache.commons.codec.digest.DigestUtils
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.*
import org.apache.hc.client5.http.impl.io.{BasicHttpClientConnectionManager, PoolingHttpClientConnectionManagerBuilder}
import org.apache.hc.client5.http.io.HttpClientConnectionManager
import org.apache.hc.core5.pool.PoolReusePolicy

import java.io.{PrintWriter, StringWriter}
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration


extension [T](inline f: scala.concurrent.Future[T])
  inline def await: T = Await.result(f, Duration.Inf)

object tool:

  def buildHttpClient() =
    HttpClientBuilder.create().setConnectionManager(connectionManager()).build()

  def connectionManager(timeout: Int = 60000) =
    val connConfig = ConnectionConfig.custom()
      .setConnectTimeout(timeout, TimeUnit.MILLISECONDS)
      .setSocketTimeout(timeout, TimeUnit.MILLISECONDS)
      .build()

    PoolingHttpClientConnectionManagerBuilder.create().
      setDefaultConnectionConfig(connConfig).
      setMaxConnTotal(1000).
      setMaxConnPerRoute(20).
      setConnPoolPolicy(PoolReusePolicy.LIFO).
      build()

  implicit class ST(throwable: Throwable):
    def toStackTrace =
      val sw = new StringWriter()
      throwable.printStackTrace(new PrintWriter(sw))
      sw.toString

  def now = System.currentTimeMillis()

  def hash(v: String, salt: String) =
    val shaHex: String = DigestUtils.sha256Hex(salt + v)
    s"sha256:$shaHex"


  def cache[A, B]() =
    CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[A, B]].
      expireAfterAccess(30, TimeUnit.MINUTES).
      maximumSize(10000).
      build[A, B]

  extension [A, B](cache: com.google.common.cache.Cache[A, B])
    def getOptional(k: A, f: A => Option[B]): Option[B] =
      Option(cache.getIfPresent(k)) orElse
        cache.synchronized:
          Option(cache.getIfPresent(k)) match
            case Some(v) => Some(v)
            case None =>
              f(k) match
                case Some(v) =>
                  cache.put(k, v)
                  Some(v)
                case None => None