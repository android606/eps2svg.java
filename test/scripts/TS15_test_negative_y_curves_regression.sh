#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS15"

# Test script for regression testing of negative Y control points in curves
# DESCRIPTION: Tests that the fix for negative Y control points in Bezier curves remains working
# 
# Usage: ./TS15_test_negative_y_curves_regression.sh [--debug]
#   --debug: Enable verbose debug output

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Negative Y Curve Control Points Regression"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_negative_y_curves_regression")"

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
print_indented "Testing negative Y control points bug fix regression..." | tee -a "$LOG_FILE"

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# Ensure reference directory exists
mkdir -p "$TEST_DIR/references"

# ====== TEST CASE 1: Verify negative Y control points are handled correctly ======
print_test_line "Test that negative Y control points are handled correctly" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run with caution.eps test file - this file is known to have negative Y control points
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/caution.eps" \
    "$OUTPUT_DIR/caution_negative_y_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/caution_negative_y_test.svg" ] && [ -s "$OUTPUT_DIR/caution_negative_y_test.svg" ]; then
    # Check whether the SVG has proper lines instead of problematic curves
    
    # Check for the problematic path pattern that contains negative Y control points
    negative_y_pattern="C 1.000 -0.693"
    problematic_paths=$(grep -c "$negative_y_pattern" "$OUTPUT_DIR/caution_negative_y_test.svg")
    
    if [ "$problematic_paths" -eq 0 ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "No problematic negative Y control points found in output SVG" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Found $problematic_paths paths with negative Y control points" >> "$LOG_FILE"
        echo "The negative Y control points fix is not working properly" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to generate SVG output for caution.eps" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE 2: Verify that the rendered output looks correct ======
print_test_line "Verify that the caution.eps rendering looks correct" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Create a reference file if it doesn't exist
if [ ! -f "$TEST_DIR/references/caution_reference.svg" ]; then
    print_indented "Creating reference file for caution.eps" | tee -a "$LOG_FILE"
    cp "$OUTPUT_DIR/caution_negative_y_test.svg" "$TEST_DIR/references/caution_reference.svg"
    print_pass | tee -a "$LOG_FILE"
    echo "Created new reference file for future comparison" >> "$LOG_FILE"
else
    # Compare the current output with the reference file
    diff_output=$(diff "$OUTPUT_DIR/caution_negative_y_test.svg" "$TEST_DIR/references/caution_reference.svg")
    
    if [ -z "$diff_output" ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "Generated SVG matches the reference file" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Generated SVG differs from the reference file" >> "$LOG_FILE"
        # Log the differences in a readable format
        echo "Differences found:" >> "$LOG_FILE"
        echo "$diff_output" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
fi

# ====== TEST CASE 3: Verify that both curve preservation and flattening work ======
print_test_line "Test negative Y curves with curve flattening enabled" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run with flattening enabled
java -Dconvert2web.flattenCurves=true -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/caution.eps" \
    "$OUTPUT_DIR/caution_negative_y_flattened.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/caution_negative_y_flattened.svg" ] && [ -s "$OUTPUT_DIR/caution_negative_y_flattened.svg" ]; then
    # The flattened version should have no problematic curves and fewer curve commands overall
    # Use wc -l to count lines properly
    preserved_curves=$(grep "C " "$OUTPUT_DIR/caution_negative_y_test.svg" | wc -l)
    flattened_curves=$(grep "C " "$OUTPUT_DIR/caution_negative_y_flattened.svg" | wc -l)
    
    # Add some debugging information
    echo "Preserved version curve count: $preserved_curves" | tee -a "$LOG_FILE"
    echo "Flattened version curve count: $flattened_curves" | tee -a "$LOG_FILE"
    
    # Check specifically for the negative Y control point pattern
    negative_y_in_flattened=$(grep "C 1.000 -0.693" "$OUTPUT_DIR/caution_negative_y_flattened.svg" | wc -l)
    echo "Negative Y control points in flattened version: $negative_y_in_flattened" | tee -a "$LOG_FILE"
    
    # Test passes if either:
    # 1. Flattened has fewer curves than preserved, OR
    # 2. Both are zero but there are no negative Y control points in flattened version 
    if [ "$flattened_curves" -lt "$preserved_curves" ] || { [ "$preserved_curves" -eq 0 ] && [ "$negative_y_in_flattened" -eq 0 ]; }; then
        print_pass | tee -a "$LOG_FILE"
        if [ "$preserved_curves" -eq 0 ] && [ "$flattened_curves" -eq 0 ]; then
            echo "Both versions properly convert problematic curves to lines" | tee -a "$LOG_FILE"
        else
            echo "Curve flattening reduces curve count as expected" | tee -a "$LOG_FILE"
        fi
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Curve flattening did not reduce curve count or remove problematic curves" | tee -a "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to generate flattened SVG output for caution.eps" | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "All negative Y curve regression tests passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "$FAILED_TESTS regression tests failed" | tee -a "$LOG_FILE"
    exit 1
fi 