#!/usr/bin/env sh
# ----------------------------------------------------------------------------
# Maven Wrapper
# ----------------------------------------------------------------------------

# Maven Wrapper script generated for the project.
# Use './mvnw' on Unix-like systems.

BASEDIR=$(dirname "$0")
if [ -z "$BASEDIR" ] ; then
  BASEDIR="."
fi

MAVEN_PROJECTBASEDIR="$BASEDIR"

if [ -z "$MAVEN_HOME" ] ; then
  MAVEN_HOME="$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
fi

CLASSPATH="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
JAVA_CMD=${JAVA_HOME:+"$JAVA_HOME/bin/java"}
JAVA_CMD=${JAVA_CMD:-java}

exec "$JAVA_CMD" -cp "$CLASSPATH" org.apache.maven.wrapper.MavenWrapperMain "$@"
