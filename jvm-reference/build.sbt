ThisBuild / scalaVersion := "3.9.0"
ThisBuild / crossScalaVersions := Seq("2.12.21", "2.13.18", "3.9.0")

lazy val conformanceClasspath = taskKey[File]("Write the classpath for the standalone conformance server")

lazy val root = project
  .in(file("."))
  .settings(
    name := "scalanative-httpserver-jvm-reference",
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "conformance/shared",
    conformanceClasspath := Def.uncached {
      given xsbti.FileConverter = fileConverter.value
      val output = baseDirectory.value / "target/conformance-classpath.txt"
      IO.write(output, (Compile / fullClasspath).value.files.mkString(java.io.File.pathSeparator))
      output
    },
    Test / fork := sys.env.contains("HTTP_SERVER_TEST_JAVA_HOME"),
    Test / javaHome := sys.env.get("HTTP_SERVER_TEST_JAVA_HOME").map(file),
    Test / unmanagedSources := Seq(
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HeadersTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/ReferenceRuntime.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/HeadersNullContractTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/FilterNullContractTest.scala",
      baseDirectory.value.getParentFile / "src/test/scala/org/scalanative/testsuite/httpserver/BasicAuthenticatorBaselineTest.scala",
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
