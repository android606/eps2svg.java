#!/bin/bash

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"

# Create output directory if it doesn't exist
mkdir -p "$SCRIPT_DIR/output"

# Make sure we're in the project root directory
cd "$PROJECT_ROOT"

# Build the project
mvn clean package

# Test each EPS file
for eps_file in "$SCRIPT_DIR/test_images/"*.eps; do
    filename=$(basename "$eps_file")
    svg_file="$SCRIPT_DIR/output/${filename%.eps}.svg"
    echo "Converting $filename to $(basename "$svg_file")..."
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$eps_file" "$svg_file"
done 