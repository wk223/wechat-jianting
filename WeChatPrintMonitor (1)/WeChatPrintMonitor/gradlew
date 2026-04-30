#!/bin/sh
DIRNAME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH=$DIRNAME/gradle/wrapper/gradle-wrapper.jar
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
