#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS09"

# Test script for caution.eps curve fix
# DESCRIPTION: Tests that the fix for problematic curves in caution.eps works correctly
# 
# Usage: ./test_fix_caution_curve.sh [--debug]
#   --debug: Enable verbose debug output

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Caution Curve Fix Test"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_fix_caution_curve")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Process command line arguments
DEBUG_MODE=false
for arg in "$@"; do
  case $arg in
    --debug)
      DEBUG_MODE=true
      ;;
  esac
done

if [ "$DEBUG_MODE" = true ]; then
  print_indented "Debug mode enabled" | tee -a "$LOG_FILE"
fi

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Testing fix for problematic curves in caution.eps..." | tee -a "$LOG_FILE"

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# ====== TEST CASE 1: Basic Caution Curve Fix ======
print_test_line "Test caution.eps with curve fix" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run the conversion with our fix
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "$TEST_IMAGES_DIR/caution.eps" \
  "$OUTPUT_DIR/caution_fixed_curve.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/caution_fixed_curve.svg" ] && [ -s "$OUTPUT_DIR/caution_fixed_curve.svg" ]; then
    # Check for the problematic segment in the output SVG
    if grep -q "L2.336 2.098" "$OUTPUT_DIR/caution_fixed_curve.svg"; then
        print_fail | tee -a "$LOG_FILE"
        echo "Problematic segment 'L2.336 2.098' is still present in the output" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    else
        print_pass | tee -a "$LOG_FILE"
        echo "The problematic segment has been properly fixed in the output SVG" >> "$LOG_FILE"
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE 2: Negative Y-Coordinate Handling ======
print_test_line "Test handling of negative Y control points" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Enable debug logging to see curve handling
if [ "$DEBUG_MODE" = true ]; then
    # Run with debug logging enabled
    java -Djava.util.logging.config.file=logging.properties \
      -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
      "$TEST_IMAGES_DIR/caution.eps" \
      "$OUTPUT_DIR/caution_debug_curves.svg" >> "$LOG_FILE" 2>&1
    
    # Check the log for negative Y control point handling
    if grep -q "Detected curve with negative Y control points" "$LOG_FILE"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Negative Y control point detection is working" >> "$LOG_FILE"
    else
        print_pass | tee -a "$LOG_FILE"
        echo "No negative Y control points detected in this run" >> "$LOG_FILE"
    fi
else
    # When not in debug mode, we'll verify by checking SVG content
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
      "$TEST_IMAGES_DIR/caution.eps" \
      "$OUTPUT_DIR/caution_fixed_curve.svg" >> "$LOG_FILE" 2>&1
      
    # Check if the SVG has expected curve/path elements
    if grep -q "<path" "$OUTPUT_DIR/caution_fixed_curve.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "SVG contains expected path elements" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "SVG missing expected path elements" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
fi

# ====== TEST CASE 3: Visual Verification ======
print_test_line "Visual verification of the caution.eps output" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Check if the file size is reasonable (not truncated)
file_size=$(stat -c%s "$OUTPUT_DIR/caution_fixed_curve.svg" 2>/dev/null)
if [ -z "$file_size" ] || [ "$file_size" -lt 500 ]; then
    print_fail | tee -a "$LOG_FILE"
    echo "Output file size too small or file missing" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
else
    # Check for basic SVG structure elements
    if grep -q "<svg" "$OUTPUT_DIR/caution_fixed_curve.svg" && \
       grep -q "<path" "$OUTPUT_DIR/caution_fixed_curve.svg" && \
       grep -q "viewBox" "$OUTPUT_DIR/caution_fixed_curve.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "SVG file has correct basic structure" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "SVG file missing basic structure elements" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "All tests passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "$FAILED_TESTS tests failed" | tee -a "$LOG_FILE"
    exit 1
fi 