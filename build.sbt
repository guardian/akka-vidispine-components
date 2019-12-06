name := "akka-vidispine-components"

version := "1.0"

scalaVersion := "2.12.10"

val akkaVersion = "2.5.22"
val circeVersion = "0.9.3"
val slf4jVersion = "1.7.25"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-agent" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "com.softwaremill.sttp" %% "core" % "0.0.20",
  "com.softwaremill.sttp" %% "async-http-client-backend-future" % "0.0.20",
  "org.asynchttpclient" % "async-http-client" % "2.0.37",
  "com.softwaremill.sttp" %% "akka-http-backend" % "0.0.20",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  "org.specs2" %% "specs2-core" % "4.6.0" % Test,
  "org.specs2" %% "specs2-mock" % "4.6.0" % Test,

)