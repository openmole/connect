package org.openmoleconnect.server

import java.time.temporal.ChronoField

import org.openmoleconnect.server.DB.UUID
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import shared.Data
import skuber._
import skuber.Timestamp
import skuber.json.format._
import shared.Data._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object K8sService {

  private def listPods = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher


    val k8s = k8sInit

//    val podList: Future[PodList] = k8s list[PodList]()    // list pod in default namespaces
    // list pods in all namespaces
    val allPodsMapFut: Future[Map[String, PodList]] = k8s listByNamespace[PodList]()
    val podList: Future[List[Pod]] = allPodsMapFut map { allPodsMap =>
      allPodsMap.values.flatMap(_.items).toList
    }

    podList map {
      _.flatMap {
        pod: Pod =>
          val name = pod.name
         // val ns = pod.namespace

          (for {
            stat <- pod.status.toList
            containerStat <- stat.containerStatuses
            status <- containerStat.state
            restarts <- stat.containerStatuses.headOption
            createTime <- pod.metadata.creationTimestamp
            podIP <- stat.podIP
          } yield {

            val st: Status = status match {
              case Container.Waiting(reason)=> Data.Waiting(reason.getOrElse(""))
              case _: Container.Running=> Data.Running()
              case Container.Terminated(_,_,_,message,_,finishedAt, _)=> Data.Terminated(message.getOrElse(""), finishedAt.map{_.toLocalTime.getLong(ChronoField.INSTANT_SECONDS)}.getOrElse(0L))
            }

            PodInfo(name, st.value, restarts.restartCount, createTime.toLocalTime.getLong(ChronoField.INSTANT_SECONDS), podIP, DB.email(UUID(pod.metadata.name)).map{_.value})
          })
      }
    }
  }

  private def podInfo(uuid: UUID) = {

    import monix.execution.Scheduler.Implicits.global
    Await.result(
      listPods.map { list =>
        list.find {
          _.name.contains(uuid.value)
        }
      }, 1 minute)
  }


  def isServiceUp(uuid: UUID): Boolean = {
    podInfo(uuid).map{_.status} == Some(Running)
  }

  def podInfos: Seq[PodInfo] = {
    for {
      uuid <- DB.uuids
      podInfo <- podInfo(uuid)
    } yield (podInfo)
  }

  def hostIP(uuid:UUID) = podInfo(uuid).map{_.podIP}
}
