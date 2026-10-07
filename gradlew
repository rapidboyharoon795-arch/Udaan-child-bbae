#!/bin/sh
# This is a standard Gradle wrapper script

# Get the directory of the script
DIRNAME=$(dirname "$0")

# Execute the wrapper jar
exec java -jar "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" "$@"
