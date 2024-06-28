package org.openmole.connect.server

import com.google.common.base.{Supplier, Suppliers}
import com.google.common.cache.CacheBuilder
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.openmole.connect.server.tool.cache

import java.util.concurrent.TimeUnit

/*
 * Copyright (C) 2024 Romain Reuillon
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object OpenMOLE:
  val versionPattern = "[0-9]*\\.[0-9]*"
  val snapshotPattern = versionPattern + "-SNAPSHOT"

  def wellFormedVersion(v: String) =
    v.matches(versionPattern) || v.matches(snapshotPattern)

  def availableVersions(withSnapshot: Boolean = true, history: Option[Int], minVersion: Option[Int], lastMajors: Boolean)(using DockerHubCache): Seq[String] =
    val tags = summon[DockerHubCache].get()
    val snapshot: Seq[String] = if withSnapshot then tags.find(_.endsWith("SNAPSHOT")).toSeq else Seq()

    val wellFormed = tags.filter(_.matches(versionPattern))

    val majors: Seq[String] =
      val ms = wellFormed.flatMap(_.split('.').headOption).distinct
      val majors =
        val h =
          history match
            case Some(h) => ms.take(h)
            case None => ms

        minVersion match
          case Some(m) =>
            def accept(v: String) = util.Try(v.toInt).map(_ >= m).getOrElse(false)
            h.filter(accept)
          case None => h

      majors.flatMap: m =>
        if lastMajors
        then wellFormed.find(_.startsWith(m))
        else wellFormed.filter(_.startsWith(m))

    snapshot ++ majors

  object DockerHubCache:
    def apply(): DockerHubCache =
      Suppliers.memoizeWithExpiration(() => dockerHubTags("openmole", "openmole"), 2, TimeUnit.MINUTES)

  opaque type DockerHubCache = Supplier[Seq[String]]

  def dockerHubTags(group: String, image: String, pageSize: Int = 100): Seq[String] =
    import org.json4s.*
    import org.json4s.jackson.JsonMethods.*

    val httpClient = tool.buildHttpClient()
    try
      val httpGet = new HttpGet(s"https://hub.docker.com/v2/namespaces/$group/repositories/$image/tags?page_size=$pageSize")
      val response = httpClient.execute(httpGet)
      try
        val json = parse(response.getEntity.getContent)
        (json \\ "name").children.map(_.values.toString)
      finally response.close()
    finally httpClient.close()

