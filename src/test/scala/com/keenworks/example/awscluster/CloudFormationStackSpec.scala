package com.keenworks.example.awscluster

import java.util.NoSuchElementException

import com.amazonaws.AmazonServiceException
import org.mockito.Mockito._
import org.scalatest.{MustMatchers, OptionValues, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CloudFormationStackSpec extends WordSpec with MustMatchers with OptionValues with MockitoSugar with ScalaFutures {

    implicit val defaultPatience =
        PatienceConfig(timeout = Span(6, Seconds), interval = Span(500, Millis))

    "CloudFormationStack#getCurrentInstanceId" should {
        "return a Future of a String if the call succeeds" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getCurrentInstanceId) thenReturn "testInstanceId"
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[String] = cloudFormationStack.getCurrentInstanceId

            val stringResponse: String = Await.result(response, 1.seconds)
            stringResponse mustEqual "testInstanceId"
        }

        "return a failed Future if the call fails" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getCurrentInstanceId) thenThrow new NoSuchElementException("test Exception")

            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[String] = cloudFormationStack.getCurrentInstanceId
            whenReady(response.failed) { e =>
                e mustBe a [NoSuchElementException]
            }
        }

    }

    "CloudFormationStack#getValidStackStatus" should {
        "return a valid stack status when call succeeds" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getStackStatus) thenReturn "CREATE_COMPLETE"
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[StackStatus] = cloudFormationStack.isStackComplete
            response.futureValue mustEqual StackComplete
        }
    }

    "CloudFormationStack#getGroupName" should {
        "return a valid group name when call succeeds" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getGroupName("testInstanceId")) thenReturn "testGroupName"
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[String] = cloudFormationStack.getGroupName("testInstanceId")
            response.futureValue mustEqual "testGroupName"
        }
    }

    "CloudFormationStack#getGroupInstances" should {
        "return a valid list of strings when call succeeds" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getGroupInstances("testGroupName")) thenReturn List("Instance1", "Instance2")
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[List[String]] = cloudFormationStack.getGroupInstances("testGroupName")
            whenReady(response) {
                s => s mustEqual List("Instance1", "Instance2")
            }
        }
    }

    "CloudFormationStack#getIpsFromInstances" should {
        "return a valid list of ip addresses when call succeeds" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getIpsFromInstances(List("Instance1", "Instance2"))) thenReturn List("1.2.3.4","5.6.7.8")
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[List[String]] = cloudFormationStack.getIpsFromInstances(List("Instance1", "Instance2"))
            whenReady(response) {
                s => s mustEqual List("1.2.3.4", "5.6.7.8")
            }

        }
    }

    "CloudFormationStack#getSiblingIps" should {
        "return a list of ips when all calls succeed" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getCurrentInstanceId) thenReturn "testInstanceId"
            when(mockAmazonWrapper.getStackStatus) thenReturn "CREATE_COMPLETE"
            when(mockAmazonWrapper.getGroupName("testInstanceId")) thenReturn "testGroupName"
            when(mockAmazonWrapper.getGroupInstances("testGroupName")) thenReturn List("Instance1", "Instance2")
            when(mockAmazonWrapper.getIpsFromInstances(List("Instance1", "Instance2"))) thenReturn List("1.2.3.4","5.6.7.8")
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[List[String]] = cloudFormationStack.getSiblingIps
            whenReady(response) {
                s: List[String] => s mustEqual List("1.2.3.4", "5.6.7.8")
            }
        }

        "return a failed future when a composed future throws an exception" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getCurrentInstanceId) thenReturn "testInstanceId"
            when(mockAmazonWrapper.getStackStatus) thenReturn "CREATE_COMPLETE"
            when(mockAmazonWrapper.getGroupName("testInstanceId")) thenThrow new AmazonServiceException("test Exception")
            when(mockAmazonWrapper.getGroupInstances("testGroupName")) thenReturn List("Instance1", "Instance2")
            when(mockAmazonWrapper.getIpsFromInstances(List("Instance1", "Instance2"))) thenReturn List("1.2.3.4","5.6.7.8")
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[List[String]] = cloudFormationStack.getSiblingIps
            whenReady(response.failed, timeout(Span(6, Seconds))) { e =>
                e mustBe a [AmazonServiceException]
            }
        }

        "return a failed future when the cloud formation stack fails" in {
            val mockAmazonWrapper = mock[AmazonWrapper]

            when(mockAmazonWrapper.getCurrentInstanceId) thenReturn "testInstanceId"
            when(mockAmazonWrapper.getStackStatus) thenReturn "CREATE_FAILED"
            when(mockAmazonWrapper.getGroupName("testInstanceId")) thenReturn "testGroupName"
            when(mockAmazonWrapper.getGroupInstances("testGroupName")) thenReturn List("Instance1", "Instance2")
            when(mockAmazonWrapper.getIpsFromInstances(List("Instance1", "Instance2"))) thenReturn List("1.2.3.4","5.6.7.8")
            val cloudFormationStack = new CloudFormationStack(mockAmazonWrapper)
            val response: Future[List[String]] = cloudFormationStack.getSiblingIps
            whenReady(response.failed, timeout(Span(6, Seconds))) { e =>
                e mustBe a [CloudFormationStackException]
            }
        }
    }

}
