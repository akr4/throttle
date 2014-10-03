import sbt._
import sbt.Keys._

object MyBuild extends Build {

  lazy val main = Project("throttle", file("."),
    settings = Defaults.defaultSettings ++ Seq(
      version := "0.1",
      organization := "net.physalis",
      crossScalaVersions := Seq("2.11.2"),
      scalaVersion := "2.11.2",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers ++= Seq(
        "typesafe" at "http://repo.typesafe.com/typesafe/releases/"
      ),
      publishTo <<= (version) { version: String =>
        val local = Path("target/publish")
        val path = local / (if (version.trim.endsWith("SNAPSHOT")) "snapshots" else "releases")
        Some(Resolver.file("Github Pages", path)(Patterns(true, Resolver.mavenStyleBasePattern)))
      },
      publishMavenStyle := true,
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "2.2.1" % "test"
      )
    )
  )
}

