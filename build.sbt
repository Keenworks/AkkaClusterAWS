name := "akka-cluster-aws"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.akka" %% "akka-actor" % "2.4.19",
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "org.mockito" % "mockito-all" % "1.10.19" % "test",
    "com.amazonaws" % "aws-java-sdk" % "1.10.77"
)
