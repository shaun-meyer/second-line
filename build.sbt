name := "twitter-project"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq("com.typesafe.akka" % "akka" % "2.1.4",
 	                    "com.typesafe.akka" %% "akka-actor" % "2.1.4",
                            "com.typesafe.akka" %%  "akka-testkit" % "2.1.4" % "test",
                            "org.twitter4j" % "twitter4j-core" % "4.0.0",
                            "org.twitter4j" % "twitter4j-stream" % "4.0.0",
                            "org.specs2" %% "specs2" % "2.3.9" % "test")
