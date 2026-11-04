ThisBuild / scalaVersion := "3.9.0"
ThisBuild / crossScalaVersions := Seq("2.12.21", "2.13.18", "3.9.0")

lazy val root = project
  .in(file("."))
  .settings(
    name := "scalanative-httpserver-jvm-reference",
    Compile / unmanagedSources := Seq(
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HeadersContractScenarios.scala",
      baseDirectory.value / "src/main/scala/org/scalanative/testsuite/httpserver/HeadersReferenceMain.scala"
    ),
    Compile / scalacOptions += "-deprecation",
    Compile / scalacOptions += "-feature",
    Compile / scalacOptions += "-unchecked"
  )
