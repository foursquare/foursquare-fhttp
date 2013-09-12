#!/bin/sh

SBT_VER=0.12.4
LATEST="http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/$SBT_VER/sbt-launch.jar"

if [ ! -d .sbtlib ]; then
  mkdir .sbtlib
fi

if [ ! -f .sbtlib/sbt-launcher-${SBT_VER}.jar ]; then
  wget -O .sbtlib/sbt-launcher-${SBT_VER}.jar $LATEST
fi

java \
-Duser.timezone=UTC \
-Djava.awt.headless=true \
-Dfile.encoding=UTF-8 \
-XX:MaxPermSize=256m \
-Xmx1g \
-noverify \
-jar .sbtlib/sbt-launcher-${SBT_VER}.jar \
"$@"
