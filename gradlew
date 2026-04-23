#!/bin/sh
# Gradle wrapper - downloads and executes Gradle
DEFAULT_GRADLE_VERSION=8.11.1
GRADLE_VERSION="${GRADLE_VERSION:-$DEFAULT_GRADLE_VERSION}"
APP_BASE_NAME=$(dirname "$0")
APP_HOME=$(cd "$APP_BASE_NAME" >/dev/null 2>&1 && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVA_HOME/bin/java" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
