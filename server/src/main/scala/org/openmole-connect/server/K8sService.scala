package org.openmoleconnect.server

import org.openmoleconnect.server.DB.UUID
import shared.Data
import skuber._
import skuber.json.ext.format._
import akka.actor.ActorSystem
import skuber.json.format._
import akka.stream.ActorMaterializer
import shared.Data._
import skuber.api.client.KubernetesClient
import skuber.ext.{Deployment, Ingress, IngressList}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object K8sService {


  def listPods = {
    withK8s { k8s =>

      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()
      implicit val dispatcher = system.dispatcher

      val allPodsMapFut: Future[Map[String, PodList]] = k8s.listByNamespace[PodList]()
      val allPodsFuture: Future[List[Pod]] = allPodsMapFut map { allPodsMap =>
        allPodsMap.values.flatMap(_.items).toList
      }


      def listPods0(pods: List[Pod]) = {
        pods.flatMap { pod: Pod =>
          val name = pod.name
          val ns = pod.namespace

          for {
            stat <- pod.status.toList
            containerStat <- stat.containerStatuses
            status <- containerStat.state
            restarts <- stat.containerStatuses.headOption
            createTime <- pod.metadata.creationTimestamp
            podIP <- stat.podIP
          } yield {
            val st: Status = status match {
              case Container.Waiting(reason) => Data.Waiting(reason.getOrElse(""))
              case _: Container.Running => Data.Running()
              case Container.Terminated(_, _, _, message, _, finishedAt, _) => Data.Terminated(message.getOrElse(""), finishedAt.map {
                _.toEpochSecond
              }.getOrElse(0L))
            }

            PodInfo(pod.name, st.value, restarts.restartCount, createTime.toEpochSecond, podIP, DB.email(UUID(pod.metadata.name)).map {
              _.value
            })
          }
        }
      }

      allPodsFuture map { pods => listPods0(pods) }

    }
  }

  def withK8s[T](kubeAction: KubernetesClient => Future[T]) = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    val k8s = k8sInit

    Await.result(kubeAction(k8s), Duration.Inf)
  }

  def getIngress = withK8s { k8s =>

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher

    val allIngressMapFut: Future[ListResource[Ingress]] = k8s listInNamespace[IngressList] ("ingress-nginx")
    val allIngressFuture: Future[List[Ingress]] = allIngressMapFut map { allIngressMap =>
      allIngressMap.items
    }


    def listIngress(ingresses: List[Ingress]) = ingresses.headOption

    allIngressFuture map { ingresses => listIngress(ingresses) }
  }

  def ingressIP: Option[String] =
    getIngress.flatMap { ing =>
      ing.status.flatMap {
        _.loadBalancer.flatMap {
          _.ingress.headOption.flatMap {
            _.ip
          }
        }
      }
    }

  def deployOpenMOLE(uuid: UUID) = {
    withK8s { k8s =>
      val podName = s"${uuid.value}"

      val openmoleContainer = Container(
        name = "openmole",
        image = "openmole/openmole",
        command = List("bin/bash", "-c", "openmole --port 80 --password password --http --remote --mem 1G")).exposePort(80)

      val openmolePod = Pod(spec = Some(Pod.Spec().addContainer(openmoleContainer)), metadata = ObjectMeta(name = podName, namespace = "openmole"))


        k8s create openmolePod
    }
  }

  def deployIfNotDeployedYet(uuid: UUID) = {
    if (!isDeploymentExists(uuid))
      deployOpenMOLE(uuid)

  }

  private def podInfo(uuid: UUID) = {

    //  import monix.execution.Scheduler.Implicits.global
    val lp = listPods
    println("pods " + lp)
    lp.find {
      _.name.contains(uuid.value)
    }
  }


  def isServiceUp(uuid: UUID): Boolean = {
    podInfo(uuid).map {
      _.status
    } == Some(Running)
  }

  def isDeploymentExists(uuid: UUID) = podInfo(uuid).isDefined

  def podInfos: Seq[PodInfo] = {

    for {
      uuid <- DB.uuids
      podInfo <- podInfo(uuid)
    } yield (podInfo)
  }

  def hostIP(uuid: UUID) = {
    podInfo(uuid).map {
      _.podIP
    }
  }
}