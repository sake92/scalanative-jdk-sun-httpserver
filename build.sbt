ThisBuild / organization := "ba.sake"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.9.0"

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaNativePlugin, ScalaNativeJUnitPlugin)
  .settings(
    name := "scalanative-httpserver",
    licenses += "Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0"),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    nativeConfig ~= (_.withMultithreading(true)),
    Test / parallelExecution := false
  )
