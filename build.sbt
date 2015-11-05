name := "KnEvol"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies +=
  "com.typesafe.akka" %% "akka-actor" % "2.3.12"
libraryDependencies += "com.assembla.scala-incubator" %% "graph-core" % "1.9.4"
libraryDependencies += "com.assembla.scala-incubator" %% "graph-dot" % "1.10.0"
libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.1.0",
  "org.slf4j" % "slf4j-nop" % "1.6.4"
)

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.37"
    