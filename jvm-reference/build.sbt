ThisBuild / scalaVersion := "3.9.0"
ThisBuild / crossScalaVersions := Seq("2.12.21", "2.13.18", "3.9.0")

lazy val root = project
  .in(file("."))
  .settings(
    name := "scalanative-httpserver-jvm-reference",
    Test / unmanagedSources := Seq(
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HeadersTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HttpPrincipalTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/FilterTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HttpServerContractTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HeaderExchange.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/BasicAuthenticatorTest.scala"
    ),
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      "junit" % "junit" % "4.13.2" % Test
    ),
    Compile / scalacOptions += "-deprecation",
    Compile / scalacOptions += "-feature",
    Compile / scalacOptions += "-unchecked"
  )
