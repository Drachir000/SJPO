#!/bin/bash

# SJPO Startup Script for Linux

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Simple Java Process Orchestrator (SJPO)                     ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 25 or higher"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "WARNING: Java version $JAVA_VERSION detected. Java 25 or higher recommended."
fi

# Check if JAR exists
JAR_FILE="target/SJPO.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found: $JAR_FILE"
    echo "Please build the project first: mvn clean package"
    exit 1
fi

echo "Starting SJPO..."
echo ""

# Run SJPO
java -jar "$JAR_FILE" "$@"

echo ""
echo "SJPO has been shut down."
