name := "foursquare-fhttp"

version := "0.1.5.1"

organization := "com.foursquare"

crossScalaVersions := Seq("2.9.1", "2.8.1")

libraryDependencies <++= (scalaVersion) { scalaVersion =>
  val specsVersion = scalaVersion match {
    case "2.9.1" => "1.6.9"
    case _       => "1.6.8"
  }
  val finagleSuffix = scalaVersion match {
    case "2.9.1" => "_" + scalaVersion
    case _ => ""
  }
  Seq(
    "com.twitter"                   %  ("finagle-core" + finagleSuffix) % "1.9.12",
    "com.twitter"                   %  ("finagle-http" + finagleSuffix) % "1.9.12", 
    "commons-httpclient"            %  "commons-httpclient"             % "3.1",
    "junit"                         %  "junit"                          % "4.5"        % "test",
    "com.novocode"                  %  "junit-interface"                % "0.6"        % "test",
    "org.scala-tools.testing"       %% "specs"                          % specsVersion % "test"
  )
}



resolvers += "twitter mvn" at "http://maven.twttr.com"

scalacOptions ++= Seq("-deprecation", "-unchecked")

testFrameworks += new TestFramework("com.novocode.junit.JUnitFrameworkNoMarker")

publishTo <<= (version) { v =>
  val nexus = "https://oss.sonatype.org/"
  if (v.endsWith("-SNAPSHOT"))
    Some("snapshots" at nexus+"content/repositories/snapshots/")
  else
    Some("releases" at nexus+"service/local/staging/deploy/maven2")
}

credentials ++= {
  val sonaType = ("Sonatype Nexus Repository Manager", "oss.sonatype.org")
  def loadMavenCredentials(file: java.io.File) : Seq[Credentials] = {
    xml.XML.loadFile(file) \ "servers" \ "server" map (s => {
      val host = (s \ "id").text
      val realm = if (host == sonaType._2) sonaType._1 else "Unknown"
      Credentials(realm, host, (s \ "username").text, (s \ "password").text)
    })
  }
  val ivyCredentials   = Path.userHome / ".ivy2" / ".credentials"
  val mavenCredentials = Path.userHome / ".m2"   / "settings.xml"
  (ivyCredentials.asFile, mavenCredentials.asFile) match {
    case (ivy, _) if ivy.canRead => Credentials(ivy) :: Nil
    case (_, mvn) if mvn.canRead => loadMavenCredentials(mvn)
    case _ => Nil
  }
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
<url>https://github.com/foursquare/foursquare-fhttp</url>
<licenses>
  <license>
    <name>Apache 2</name>
    <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    <distribution>repo</distribution>
    <comments>A business-friendly OSS license</comments>
  </license>
</licenses>
<scm>
  <url>git@github.com/foursquare/foursquare-fhttp.git</url>
  <connection>scm:git:git@github.com/foursquare/foursquare-fhttp.git</connection>
</scm>
<developers>
   <developer>
   <id>john</id>
   <name>John Gallagher</name>
   <email>john@foursquare.com</email>
 </developer>
</developers>
)

