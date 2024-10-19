name := "zion"

version := "0.1"

scalaVersion := "3.3.4"

lazy val akkaVersion = "1.1.2"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")
val AkkaVersion = "2.9.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test)

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.3.14",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test)
