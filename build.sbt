name := "tic-tac-toe-server"

organization := "me.marianonavas"

version := "0.0.1"

scalaVersion := "2.11.5"

lazy val dropwizardVersion = "0.8.0"

lazy val jacksonScalaVersion = "2.5.1"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1" % "test" withSources() withJavadoc(),
  "org.scalacheck" %% "scalacheck" % "1.12.1" % "test" withSources() withJavadoc(),
  "io.dropwizard" % "dropwizard-testing" % dropwizardVersion % "test" withSources(),
  "org.mockito" % "mockito-all" % "1.10.19" % "test" withSources(),
  "com.mashape.unirest" % "unirest-java" % "1.4.5" % "test" withSources(),
  "io.dropwizard" % "dropwizard-core" % dropwizardVersion withSources(),
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonScalaVersion withSources(),
  "org.reactivemongo" %% "reactivemongo" % "0.12.0" withSources(),
  "com.typesafe.akka" %% "akka-actor" % "2.4.12" withSources()
)

initialCommands := "import me.marianonavas.tictactoeserver._"

