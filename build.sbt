name := "KnEvol"

version := "1.0"

scalaVersion := "2.12.6"

libraryDependencies +=
  "com.typesafe.akka" %% "akka-actor" % "2.5.13"
libraryDependencies += "org.scala-graph" %% "graph-core" % "1.12.5"
libraryDependencies += "org.scala-graph" %% "graph-dot" % "1.12.1"
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "org.slf4j" % "slf4j-nop" % "1.6.4"
)

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.37"
    