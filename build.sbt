ThisBuild / organization := "ba.sake"
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / crossScalaVersions := Seq("2.12.21", "2.13.18", "3.9.0")
ThisBuild / homepage := Some(uri("https://github.com/sake92/scalanative-jdk-sun-httpserver"))
ThisBuild / licenses := List("Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    uri("https://github.com/sake92/scalanative-jdk-sun-httpserver"),
    "scm:git:git@github.com:sake92/scalanative-jdk-sun-httpserver.git"
  )
)
ThisBuild / developers := List(
  Developer("sake92", "Sakib Hadžiavdić", "sakib@sake.ba", uri("https://sake.ba"))
)

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaNativePlugin, ScalaNativeJUnitPlugin)
  .settings(
    name := "scalanative-httpserver",
    description := "JDK 8 com.sun.net.httpserver implementation for Scala Native",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    nativeConfig ~= (_.withMultithreading(true)),
    Test / parallelExecution := false
  )
