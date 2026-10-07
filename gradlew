#!/bin/sh
# Gradle Wrapper for UdaanChild
gradlew="gradlew"
case $|\"$0\"" in
    *"${gradlew}."* ) ;;
    *"${gradlew}" ) ;;
    *"${gradlew}."* ) ;;
    *) gradlew="gradlew" ;;
esac

# Iska kaam hai wrapper ko trigger karna
exec "$0" "$@" || exec java -jar "$GRADLE_HOME/lib/wrapper/gradle-wrapper.jar" "$@"
