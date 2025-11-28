package org.openmole.connect.server

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.models.V1JobStatus
import io.kubernetes.client.openapi.apis.{ApiextensionsV1Api, CoreV1Api}
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import org.openmole.connect.shared.{Data, Storage}
import org.openmole.connect.shared.Data.*

import java.util
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import org.openmole.connect.server.db.DB
import org.openmole.connect.server.db.DB.UUID
import tool.*

import java.time.Duration

import io.kubernetes.client.openapi.*
import io.kubernetes.client.openapi.models.*
import io.kubernetes.client.*
import io.kubernetes.client.util.*
import scala.jdk.CollectionConverters.*

import io.kubernetes.client.openapi.apis.*

object KubeService:

  object KubeCache:
    type Cached = String

    def apply(): KubeCache =
      val ipCache = tool.cache[DB.UUID, Cached]()

      KubeCache(ipCache)

  case class KubeCache(ipCache: com.google.common.cache.Cache[DB.UUID, KubeCache.Cached])


  object Namespace:
    val openmole = "openmole"

  val userLabel = "user"
  val emailLabel = "email"
  //val connect = "connect"

  def createPVC(pvcName: String, uuid: String, storage: Int, storageClassName: Option[String]) =
    val api = kubeAPI
    import io.kubernetes.client.custom.*

    val metadata = new V1ObjectMeta()
      .name(pvcName)
      .namespace(Namespace.openmole)
      .labels(Map("user" -> uuid).asJava)

    val resources = new V1VolumeResourceRequirements()
      .requests(Map("storage" -> Quantity(s"${storage}Mi")).asJava)

    val spec = new V1PersistentVolumeClaimSpec()
      .accessModes(List("ReadWriteOnce").asJava)
      .resources(resources)

    storageClassName.foreach(spec.setStorageClassName)

    val claim =
      new V1PersistentVolumeClaim()
        .metadata(metadata)
        .spec(spec)

    try
      api.createNamespacedPersistentVolumeClaim(Namespace.openmole, claim).execute()
      true
    catch
      case e: ApiException if e.getCode == 409 => false


  def deployOpenMOLE(
    uuid: DB.UUID,
    email: DB.Email,
    omVersion: String,
    openMOLEMemory: Int,
    memoryLimit: Int,
    cpuLimit: Double,
    storageClassName: Option[String],
    storageSize: Int,
    tmpSize: Int = 51200,
    initialize: Boolean = false)(using KubeCache) =

    import io.kubernetes.client.custom.*

    summon[KubeCache].ipCache.invalidate(uuid)

    val client = Config.defaultClient()
    val appsApi = AppsV1Api(client)

    val podName = uuid
    val pvcName = s"pvc-$podName"

    createPVC(pvcName, uuid, storageSize, storageClassName)

    def emailTag: String =
      def replaceChar(c: Char): Char =
        c match
          case c if c.isLetterOrDigit => c
          case c => '_'

      String.valueOf(email.replace("@", "_at_").toCharArray.map(replaceChar))

    val labels = Map(
      "app" -> "openmole",
      "podName" -> podName,
      "user" -> uuid,
      "email" -> emailTag
    )

    val volumeMounts = List(
      new V1VolumeMount().name("data").mountPath("/var/openmole/"),
      new V1VolumeMount().name("tmp").mountPath("/var/openmole/.openmole/tmp/")
    )

    val limits =
      if !initialize
      then
        Seq() ++
          //Seq(memoryLimit).filter(_ > 0).map(m => "memory" -> Quantity(s"${m}Mi")) ++
          Seq(cpuLimit).filter(_ > 0).map(c => "cpu" -> Quantity(c.toString))
      else
        Seq() ++
          Seq(memoryLimit).filter(_ > 0).map(m => "memory" -> Quantity(s"${m}Mi")) ++
          Seq(cpuLimit).filter(_ > 0).map(c => "cpu" -> Quantity(c.toString))

    val resources = new V1ResourceRequirements()
      .requests(
        Map(
          "memory" -> Quantity(s"${openMOLEMemory}Mi"),
          "cpu" -> Quantity("0")
        ).asJava
      )
      .limits(Map(limits*).asJava)

    val initializeContainer = new V1Container()
      .name("openmole-init")
      .image(s"openmole/openmole:$omVersion")
      .command(List("bin/bash", "-c").asJava)
      .args(List(s"openmole-docker --port 80 --remote --mem 2G --workspace /var/openmole/.openmole --gui-initialize").asJava)
      .volumeMounts(volumeMounts.asJava)
      .imagePullPolicy("Always")
      .ports(List(
        new V1ContainerPort().containerPort(80)
      ).asJava)
      .securityContext(
        new V1SecurityContext().privileged(true)
      )


    val container = new V1Container()
      .name("openmole")
      .image(s"openmole/openmole:$omVersion")
      .resources(resources)
      .command(List("bin/bash", "-c").asJava)
      .args(List(s"openmole-docker --port 80 --remote --mem ${openMOLEMemory}m --workspace /var/openmole/.openmole").asJava)
      .volumeMounts(volumeMounts.asJava)
      .imagePullPolicy("Always")
      .ports(List(
        new V1ContainerPort().containerPort(80)
      ).asJava)
      .securityContext(
        new V1SecurityContext().privileged(true)
      )

    val volumes = List(
      new V1Volume()
        .name("data")
        .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource().claimName(pvcName)),
      new V1Volume()
        .name("tmp")
        .emptyDir(new V1EmptyDirVolumeSource().sizeLimit(Quantity(s"${tmpSize}Mi")))
    )

    val podSpec =
      if !initialize
      then
        new V1PodSpec()
          .containers(List(container).asJava)
          .volumes(volumes.asJava)
          .terminationGracePeriodSeconds(60L)
      else
        new V1PodSpec()
          .initContainers(List(initializeContainer).asJava)
          .containers(List(container).asJava)
          .volumes(volumes.asJava)
          .terminationGracePeriodSeconds(60L)

    val podTemplate = new V1PodTemplateSpec()
      .metadata(new V1ObjectMeta().labels(labels.asJava))
      .spec(podSpec)

    val deploymentSpec = new V1DeploymentSpec()
      .replicas(1)
      .selector(new V1LabelSelector().matchLabels(Map("app" -> "openmole").asJava))
      .template(podTemplate)
      .strategy(new V1DeploymentStrategy().`type`("Recreate"))

    val deployment = new V1Deployment()
      .metadata(new V1ObjectMeta().name(podName).namespace(Namespace.openmole))
      .spec(deploymentSpec)

    try
      appsApi.createNamespacedDeployment(Namespace.openmole, deployment).execute()
    catch
      case e: ApiException if e.getCode == 409 =>
        val existing = appsApi.readNamespacedDeployment(podName, Namespace.openmole).execute()
        deployment.getMetadata.setResourceVersion(existing.getMetadata.getResourceVersion)
        appsApi.replaceNamespacedDeployment(podName, Namespace.openmole, deployment).execute()
      case e: ApiException =>
        throw e

  def launch(user: DB.User)(using KubeCache, KubeService): Unit =
    KubeService.startOpenMOLEPod(user.uuid, user.email, user.omVersion, user.openMOLEMemory, user.memory, user.cpu)

  def stopOpenMOLEPod(uuid: DB.UUID)(using KubeCache) =
    val client = Config.defaultClient()
    val appsApi = AppsV1Api(client)

    try appsApi.deleteNamespacedDeployment(uuid, Namespace.openmole).execute()
    catch
      case e: ApiException if e.getCode == 404 =>
      case e: ApiException =>
        throw e

    summon[KubeCache].ipCache.invalidate(uuid)

  def startOpenMOLEPod(uuid: DB.UUID, email: String, omVersion: String, openmoleMemory: Int, memoryLimit: Int, cpuLimit: Double)(using KubeCache, KubeService) =
    val k8sService = summon[KubeService]
    stopOpenMOLEPod(uuid)

    def initialize =
      import org.openmole.connect.server.OpenMOLE.OpenMOLEVersion
      for
        v1 <- OpenMOLEVersion.parse(omVersion)
        v2 <- OpenMOLEVersion.parse("21.0")
      yield v1.compare(v2) > 0

    deployOpenMOLE(uuid, email, omVersion, openmoleMemory, memoryLimit, cpuLimit, k8sService.storageClassName, k8sService.storageSize, initialize = initialize.getOrElse(false))
    summon[KubeCache].ipCache.invalidate(uuid)

  def getPVC(uuid: String): Option[V1PersistentVolumeClaim] =
    val coreApi = kubeAPI

    def byLabel =
      val labelSelector = s"user=$uuid"
      coreApi.listNamespacedPersistentVolumeClaim(Namespace.openmole).labelSelector(labelSelector).execute()

    def byName =
      val pvcName = s"pvc-$uuid"
      scala.util.Try(coreApi.readNamespacedPersistentVolumeClaim(pvcName, Namespace.openmole).execute()).toOption

    byLabel.getItems.asScala.headOption orElse byName


  def updateOpenMOLEPersistentVolumeStorage(uuid: String, sizeGi: Int): Unit =
    import io.kubernetes.client.custom.*

    val maybePvc = getPVC(uuid)

    maybePvc.foreach: pvc =>
      val name = pvc.getMetadata.getName
      val namespace = pvc.getMetadata.getNamespace

      val updatedResources = new V1VolumeResourceRequirements()
        .requests(Map("storage" -> new Quantity(s"${sizeGi}Gi")).asJava)

      pvc.getSpec.setResources(updatedResources)

      val coreApi = kubeAPI
      try coreApi.replaceNamespacedPersistentVolumeClaim(name, namespace, pvc).execute()
      catch
        case e: ApiException if e.getCode == 403 =>
          throw new UnsupportedOperationException(s"Storage class does not support resizing", e)

  def getPVCSize(uuid: String)(using k8sService: KubeService): Option[Int] =
    getPVC(uuid).flatMap: pvc =>
      Option(pvc.getSpec)
        .flatMap(spec => Option(spec.getResources))
        .flatMap(resources => Option(resources.getRequests))
        .flatMap(reqs => Option(reqs.get("storage")))
        .map(q => q.getNumber.intValue)

  def deleteOpenMOLE(uuid: DB.UUID)(using KubeCache): Unit =
    summon[KubeCache].ipCache.invalidate(uuid)
    val api = kubeAPI

    stopOpenMOLEPod(uuid)
    getPVC(uuid).foreach: pvc =>
      api.deleteNamespacedPersistentVolumeClaim(pvc.getMetadata.getName, pvc.getMetadata.getNamespace).execute()


  def kubeClient =
    import io.kubernetes.client.util.*
    ClientBuilder.standard().setReadTimeout(Duration.ofMinutes(2)).build()

  def kubeAPI =
    import io.kubernetes.client.util.*
    CoreV1Api(kubeClient)

  def toPodInfo(pod: V1Pod): PodInfo =
    val metadata = Option(pod.getMetadata)
    val status = Option(pod.getStatus)

    val containerStatus =
      for
        st <- status
        list <- Option(st.getContainerStatuses).map(_.asScala)
        first <- list.headOption
      yield first

    val deletionTimestamp = metadata.flatMap(m => Option(m.getDeletionTimestamp))

    val podStatus =
      deletionTimestamp match
        case Some(_) => Some(Data.PodInfo.Status.Terminating)
        case None =>
          containerStatus.flatMap: cs =>
            Option(cs.getState) match
              case Some(state) if state.getRunning != null =>
                Some:
                  Data.PodInfo.Status.Running(
                    Option(state.getRunning.getStartedAt)
                      .map(_.toEpochSecond)
                      .getOrElse(0L)
                  )
              case Some(state) if state.getWaiting != null =>
                Some:
                  Data.PodInfo.Status.Waiting(
                    Option(state.getWaiting.getReason).getOrElse("")
                  )
              case Some(state) if state.getTerminated != null =>
                Some:
                  Data.PodInfo.Status.Terminated(
                    Option(state.getTerminated.getMessage).getOrElse(""),
                    Option(state.getTerminated.getFinishedAt).map(_.toEpochSecond).getOrElse(0L)
                  )
              case _ =>
                metadata.flatMap: m =>
                  Option(m.getCreationTimestamp).map(_ => Data.PodInfo.Status.Creating)


    val restartCount = containerStatus.flatMap(cs => Option(cs.getRestartCount)).map(_.toInt)

    val creationTime =
      metadata.flatMap: m =>
        Option(m.getCreationTimestamp).map(_.toEpochSecond)

    val podIP = status.flatMap(s => Option(s.getPodIP))

    val label =
      metadata.flatMap: m =>
        Option(m.getLabels).flatMap: labels =>
          Option(labels.get("user")).orElse(Option(labels.get("podName")))

    PodInfo(
      name = metadata.map(_.getName).getOrElse(""),
      status = podStatus,
      restarts = restartCount,
      createTime = creationTime,
      podIP = podIP,
      userUUID = label
    )

  def listPods: List[PodInfo] =
    val client = kubeAPI

    val podList = client.listNamespacedPod(Namespace.openmole).execute()

    podList.getItems.asScala.toList.map(toPodInfo)

  def podInfo(uuid: DB.UUID): Option[PodInfo] =
    val client = kubeAPI

    val labelSelector = s"podName=${uuid}"

    val podList = client.listNamespacedPod(Namespace.openmole).labelSelector(labelSelector).execute()

    podList.getItems.asScala.toList.map(toPodInfo).headOption

  def podIP(uuid: DB.UUID)(using KubeCache) =
    def ip(uuid: DB.UUID) = podInfo(uuid).flatMap(_.podIP)
    summon[KubeCache].ipCache.getOptional(uuid, ip)

  def usedSpace(uuid: DB.UUID): Option[Storage] =
    import io.kubernetes.client.openapi.*
    import io.kubernetes.client.*
    import io.kubernetes.client.util.*

    val podName = podInfo(uuid).map(_.name)

    podName.flatMap: name =>
      val client = kubeClient
      val exec = new Exec(client)
      val builder = exec.newExecutionBuilder(Namespace.openmole, name, Array("df", "-a"))
      builder.setStdout(true)

      scala.util.Try(builder.execute()).toOption.flatMap: proc =>
        val result =
          scala.io.Source.fromInputStream(proc.getInputStream).getLines().find(_.endsWith("/var/openmole")).map: l =>
            val fields = l.replaceAll("  *", " ").split(' ')
            val used = fields(2).toDouble
            val free = fields(3).toDouble
            Storage(
              used / 1024,
              free / 1024
            )

        if proc.waitFor() == 0
        then result
        else None

  def podExists(uuid: DB.UUID) = podInfo(uuid).isDefined

  def podInfos: Seq[PodInfo] =
    val pods = listPods
    for
      uuid <- DB.users.map(_.uuid)
      podInfo <- pods.find { _.name.contains(uuid) }
    yield podInfo


case class KubeService(storageClassName: Option[String], storageSize: Int)
