#!/bin/sh
#
# Gradle start up script for UN*X
#

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] && [ ! -x "$(command -v "$JAVACMD")" ] ; then
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS -jar "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
