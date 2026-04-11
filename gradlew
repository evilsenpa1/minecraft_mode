#!/bin/sh
# Gradle wrapper script for Unix
APP_HOME="$(dirname "$(realpath "$0")")"
exec "$APP_HOME/gradle/wrapper/gradlew-launcher.sh" "$@" 2>/dev/null || \
java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
