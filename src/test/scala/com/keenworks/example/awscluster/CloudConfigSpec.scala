package com.keenworks.example.awscluster

import com.amazonaws.AmazonServiceException
import com.typesafe.config.Config
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{MustMatchers, OptionValues, WordSpec}
import org.mockito.Mockito._

import scala.collection.JavaConversions._
import scala.concurrent.Future

class CloudConfigSpec extends WordSpec with MustMatchers with OptionValues with MockitoSugar with ScalaFutures {

    implicit val defaultPatience =
        PatienceConfig(timeout = Span(2, Seconds), interval = Span(50, Millis))

    "CloudConfig#getCloudConfig" should {
        "log an error on future failure" in {
            val mockCloudFormationStack = mock[CloudFormationStack]
            when(mockCloudFormationStack.getSiblingIps) thenReturn Future.failed(new AmazonServiceException("failed"))
            val cloudConfig = new CloudConfig(mockCloudFormationStack)
            val response:Future[Config] = cloudConfig.getCloudConfig
            whenReady(response.failed, timeout(Span(1, Seconds))) { e =>
                e mustBe a [AmazonServiceException]
            }
        }

        "create seed node configs from list of ips" in {
            val mockCloudFormationStack = mock[CloudFormationStack]
            when(mockCloudFormationStack.getSiblingIps) thenReturn Future.successful(List("1.2.3.4", "5.6.7.8"))
            val cloudConfig = new CloudConfig(mockCloudFormationStack)
            val response: Future[Config] = cloudConfig.getCloudConfig
            whenReady(response) {
                config: Config =>
                    val seedNodes: List[String] = config.getStringList("akka.cluster.seed-nodes").toList
                    seedNodes.head mustEqual "akka.tcp://ClusterContentActorSystem@1.2.3.4:2552"
            }
        }

        "create sorted seed node config from unsorted list of ips" in {
            val mockCloudFormationStack = mock[CloudFormationStack]
            when(mockCloudFormationStack.getSiblingIps) thenReturn Future.successful(List("129.95.30.40","5.24.69.2","19.20.203.5","1.2.3.4","19.20.21.22","5.220.100.50"))
            val cloudConfig = new CloudConfig(mockCloudFormationStack)
            val response: Future[Config] = cloudConfig.getCloudConfig
            whenReady(response) {
                config: Config =>
                    val seedNodes: List[String] = config.getStringList("akka.cluster.seed-nodes").toList
                    seedNodes.head mustEqual "akka.tcp://ClusterContentActorSystem@1.2.3.4:2552"
            }
        }
    }

}
