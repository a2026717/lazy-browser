#!/bin/bash
# setup-gradle.sh - Download Gradle wrapper jar
# Run this once before building

GRADLE_VERSION="8.2"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    echo "✅ Gradle wrapper jar already exists"
    exit 0
fi

echo "📥 Downloading Gradle ${GRADLE_VERSION}..."

# Create gradle wrapper directory
mkdir -p gradle/wrapper

# Download gradle wrapper jar
curl -L -o "$WRAPPER_JAR" \
    "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "⚠️  Direct download failed, trying alternative method..."
    # Alternative: download full gradle and extract wrapper jar
    TEMP_DIR=$(mktemp -d)
    curl -L -o "$TEMP_DIR/gradle.zip" "$WRAPPER_URL"
    unzip -q "$TEMP_DIR/gradle.zip" -d "$TEMP_DIR"
    cp "$TEMP_DIR/gradle-${GRADLE_VERSION}/lib/gradle-wrapper-${GRADLE_VERSION}.jar" "$WRAPPER_JAR" 2>/dev/null || \
    cp "$TEMP_DIR/gradle-${GRADLE_VERSION}/src/gradle-wrapper.jar" "$WRAPPER_JAR" 2>/dev/null
    rm -rf "$TEMP_DIR"
fi

if [ -f "$WRAPPER_JAR" ]; then
    echo "✅ Gradle wrapper jar downloaded"
else
    echo "❌ Failed to download. Please download manually from:"
    echo "   https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    echo "   Extract gradle-wrapper.jar to gradle/wrapper/"
fi
