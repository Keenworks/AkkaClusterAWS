package com.keenworks.example.awscluster

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class CloudFormationStack(amazonWrapper: AmazonWrapper) {

    /**
      * isStackComplete: Query CloudFormation stack status every five seconds until
      *     it is complete and returns one of the valid status messages.
      *     Wrapped in "blocking" so it doesn't starve the thread pool.
      * @return Future[StackComplete] or an exception
      */
    def isStackComplete: Future[StackStatus] = Future {
        Iterator.continually {
            blocking {
                Thread.sleep(5000)
                amazonWrapper.getStackStatus
            }
        }.map(isStackValid).dropWhile(p => p.get == StackInProgress).take(1).next().get
    }

    private def isStackValid(status: String): Try[StackStatus] = {
        status match {
            case "CREATE_COMPLETE"          => Success(StackComplete)
            case "UPDATE_COMPLETE"          => Success(StackComplete)
            case "ROLLBACK_COMPLETE"        => Success(StackComplete)
            case "UPDATE_ROLLBACK_COMPLETE" => Success(StackComplete)
            case "CREATE_FAILED"            => Failure(CloudFormationStackException(status))
            case "ROLLBACK_FAILED"          => Failure(CloudFormationStackException(status))
            case "DELETE_FAILED"            => Failure(CloudFormationStackException(status))
            case "UPDATE_ROLLBACK_FAILED"   => Failure(CloudFormationStackException(status))
            case _                          => Success(StackInProgress)
        }
    }

    def getCurrentInstanceId: Future[String] = Future {
        val instanceId = amazonWrapper.getCurrentInstanceId
        instanceId
    }

    def getGroupName(instanceId: String): Future[String] = Future {
        val groupName = amazonWrapper.getGroupName(instanceId)
        groupName
    }

    def getGroupInstances(groupName: String): Future[List[String]] = Future {
        val groupInstances = amazonWrapper.getGroupInstances(groupName)
        groupInstances
    }

    def getIpsFromInstances(groupInstances: List[String]): Future[List[String]] = Future {
        val instanceIps = amazonWrapper.getIpsFromInstances(groupInstances)
        instanceIps
    }

    def getSiblingIps: Future[List[String]] = {
        for {
            _ <- isStackComplete
            instanceId <- getCurrentInstanceId
            groupName <- getGroupName(instanceId)
            groupInstances <- getGroupInstances(groupName)
            siblingIps <- getIpsFromInstances(groupInstances)
        } yield siblingIps
    }

}

sealed trait StackStatus
case object StackComplete extends StackStatus
case object StackInProgress extends StackStatus
case class CloudFormationStackException(msg: String) extends RuntimeException

object CloudFormationStack {
    val cloudFormationStack = new CloudFormationStack(new AmazonWrapper)
    def apply(): CloudFormationStack = cloudFormationStack
}