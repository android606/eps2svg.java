#!/bin/bash

# Script to verify that curve flattening is working properly
# This script should be run after implementing the flattenCurves functionality

echo "=== Testing Curve Flattening Fix ==="

# Set up paths
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test"
TEST_IMAGES_DIR="$TEST_DIR/test_images"
CURVE_TESTS_DIR="$TEST_DIR/curve_tests"
OUTPUT_DIR="$TEST_DIR/output"
LOG_FILE="$TEST_DIR/curve_flattening_verification.log"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Start a new log file
echo "=== Curve Flattening Verification Test - $(date) ===" > "$LOG_FILE"

# Compile the project if needed
echo "Checking jar file..." | tee -a "$LOG_FILE"
if [ ! -f "$PROJECT_ROOT/target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    echo "Compiling project..." | tee -a "$LOG_FILE"
    cd "$PROJECT_ROOT" && mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
    if [ $? -ne 0 ]; then
        echo "Failed to compile project!" | tee -a "$LOG_FILE"
        exit 1
    fi
fi

# Test with arc_test.eps file
echo "Converting arc_test.eps with curve preservation (default)..." | tee -a "$LOG_FILE"
java -jar "$PROJECT_ROOT/target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" \
  "$TEST_IMAGES_DIR/arc_test.eps" \
  "$OUTPUT_DIR/verify_preserved.svg" \
  >> "$LOG_FILE" 2>&1

echo "Converting arc_test.eps with curve flattening enabled..." | tee -a "$LOG_FILE"
java -Dconvert2web.flattenCurves=true -jar "$PROJECT_ROOT/target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" \
  "$TEST_IMAGES_DIR/arc_test.eps" \
  "$OUTPUT_DIR/verify_flattened.svg" \
  >> "$LOG_FILE" 2>&1

# Check if curves were preserved/flattened correctly
echo "Verifying curve commands in files..." | tee -a "$LOG_FILE"

curves_preserved=$(grep -c "C" "$OUTPUT_DIR/verify_preserved.svg")
echo "verify_preserved.svg curve commands: $curves_preserved" | tee -a "$LOG_FILE"

curves_flattened=$(grep -c "C" "$OUTPUT_DIR/verify_flattened.svg")
echo "verify_flattened.svg curve commands: $curves_flattened" | tee -a "$LOG_FILE"

# Determine if the test passed
if [ $curves_flattened -lt $curves_preserved ]; then
    echo "TEST PASSED: Curve flattening is working correctly!" | tee -a "$LOG_FILE"
    echo "Preserved file has $curves_preserved curve commands" | tee -a "$LOG_FILE"
    echo "Flattened file has $curves_flattened curve commands" | tee -a "$LOG_FILE"
    exit 0
else
    echo "TEST FAILED: Curve flattening didn't reduce curve count!" | tee -a "$LOG_FILE"
    echo "Preserved file has $curves_preserved curve commands" | tee -a "$LOG_FILE"
    echo "Flattened file has $curves_flattened curve commands" | tee -a "$LOG_FILE"
    exit 1
fi 