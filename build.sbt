name := "akka-vidispine-components"

version := "0.7"

scalaVersion := "2.12.10"

val akkaVersion = "2.5.23"
val circeVersion = "0.13.0"
val slf4jVersion = "1.7.25"
val sttpVersion = "1.7.2"

crossScalaVersions := List("2.12.10","2.13.4")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-agent" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-backend-future" % sttpVersion,
  "org.asynchttpclient" % "async-http-client" % "2.0.37",
  "com.softwaremill.sttp" %% "akka-http-backend" % sttpVersion,
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0",
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
publishConfiguration := publishConfiguration.value.withOverwrite(true)

// Add sonatype repository settings
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)
