name := "akka-vidispine-components"

version := "1.0-SNAPSHOT"

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

// POM settings for Sonatype
organization := "com.gu"
homepage := Some(url("https://github.com/guardian/akka-vidispine-components"))
scmInfo := Some(ScmInfo(url("https://github.com/guardian/akka-vidispine-components"), "git@github.com:guardian/akka-vidispine-components.git"))
developers := List(Developer("andyg",
  "Andy Gallagher",
  "andy.gallagher@theguardian.com",
  url("https://github.com/fredex42")))
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
publishMavenStyle := true

// Add sonatype repository settings
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)
