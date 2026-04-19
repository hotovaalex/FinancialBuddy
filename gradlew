#!/usr/bin/env sh

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

APP_HOME=$(cd "$(dirname "$0")" && pwd)

DEFAULT_JVM_OPTS=""

# Find java
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
