seq(conscriptSettings :_*)

name := "herald-app"

organization := "net.databinder.herald"

version := "0.4.0"

libraryDependencies ++= Seq(
                    "com.tristanhunt" %% "knockoff" % "0.8.0-16",
                    "net.databinder" %% "unfiltered-netty-server" % "0.6.0",
                    "net.databinder.dispatch" %% "core" % "0.9.0-alpha2"
)

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishMavenStyle := true

publishTo :=
  Some("releases" at
       "https://oss.sonatype.org/service/local/staging/deploy/maven2")

publishArtifact in Test := false

licenses := Seq("LGPL v3" -> url("http://www.gnu.org/licenses/lgpl.txt"))

pomExtra := (
  <scm>
    <url>git@github.com:n8han/herald.git</url>
    <connection>scm:git:git@github.com:n8han/herald.git</connection>
  </scm>
  <developers>
    <developer>
      <id>n8han</id>
      <name>Nathan Hamblen</name>
      <url>http://github.com/n8han</url>
    </developer>
  </developers>)
