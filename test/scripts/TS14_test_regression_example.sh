#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS14"

# Test script for regression testing of curve flattening
# DESCRIPTION: Tests that curve flattening works correctly and previous bugs remain fixed
# 
# Usage: ./test_regression_example.sh [--debug]
#   --debug: Enable verbose debug output

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Curve Flattening Regression"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_regression_example")"

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
print_indented "Testing curve flattening regression..." | tee -a "$LOG_FILE"

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# Ensure reference directory exists
mkdir -p "$TEST_DIR/references"

# ====== TEST CASE 1: Verify flattenCurves system property ======
print_test_line "Test that flattenCurves system property is recognized" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Create a simple SVG with default curve preservation
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/curve_demo.eps" \
    "$OUTPUT_DIR/regression_preserved.svg" >> "$LOG_FILE" 2>&1

# Create the same SVG with curve flattening enabled
java -Dconvert2web.flattenCurves=true -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/curve_demo.eps" \
    "$OUTPUT_DIR/regression_flattened.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/regression_preserved.svg" ] && [ -f "$OUTPUT_DIR/regression_flattened.svg" ]; then
    # Count curve commands in both files
    curves_preserved=$(grep -c "C " "$OUTPUT_DIR/regression_preserved.svg")
    curves_flattened=$(grep -c "C " "$OUTPUT_DIR/regression_flattened.svg")
    
    echo "Preserved curves: $curves_preserved" >> "$LOG_FILE"
    echo "Flattened curves: $curves_flattened" >> "$LOG_FILE"
    
    if [ $curves_flattened -lt $curves_preserved ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "System property convert2web.flattenCurves is working as expected" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "System property convert2web.flattenCurves is not affecting curve count" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "One or both output files missing" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE 2: Bug Fix Regression - Subtle Curves ======
print_test_line "Regression test: Subtle curves handling" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run with subtle curve test case
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_DIR/curve_tests/subtle_curve_test.eps" \
    "$OUTPUT_DIR/subtle_regression.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/subtle_regression.svg" ] && [ -s "$OUTPUT_DIR/subtle_regression.svg" ]; then
    # Verify the SVG has valid curve data
    valid_svg=true
    
    # Check for basic structure indicators
    if ! grep -q "<svg" "$OUTPUT_DIR/subtle_regression.svg"; then
        echo "Missing <svg> tag" >> "$LOG_FILE"
        valid_svg=false
    fi
    
    if ! grep -q "path" "$OUTPUT_DIR/subtle_regression.svg"; then
        echo "Missing path elements" >> "$LOG_FILE"
        valid_svg=false
    fi
    
    # Check file size is reasonable (not truncated)
    file_size=$(stat -c%s "$OUTPUT_DIR/subtle_regression.svg")
    if [ "$file_size" -lt 500 ]; then
        echo "File size too small ($file_size bytes), may be truncated" >> "$LOG_FILE"
        valid_svg=false
    fi
    
    if [ "$valid_svg" = true ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "Subtle curves handling passed regression check" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Subtle curves regression check failed" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Subtle curves output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE 3: Bug Fix for Negative Y Control Points ======
print_test_line "Bug fix regression: Negative Y control points handling" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run test with the caution.eps file that had negative Y control point issues
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/caution.eps" \
    "$OUTPUT_DIR/caution_regression.svg" >> "$LOG_FILE" 2>&1

# Verify the caution SVG was generated and doesn't have errors
if [ -f "$OUTPUT_DIR/caution_regression.svg" ] && [ -s "$OUTPUT_DIR/caution_regression.svg" ]; then
    # Check for error markers or malformed SVG
    if grep -q "error" "$OUTPUT_DIR/caution_regression.svg"; then
        print_fail | tee -a "$LOG_FILE"
        echo "Error found in caution SVG output" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    else
        # Create a reference file if it doesn't exist
        if [ ! -f "$TEST_DIR/references/caution_reference.svg" ]; then
            print_indented "Creating reference file for caution.eps" | tee -a "$LOG_FILE"
            cp "$OUTPUT_DIR/caution_regression.svg" "$TEST_DIR/references/caution_reference.svg"
        else
            # Compare with reference
            diff_output=$(diff "$OUTPUT_DIR/caution_regression.svg" "$TEST_DIR/references/caution_reference.svg")
            if [ -z "$diff_output" ]; then
                print_pass | tee -a "$LOG_FILE"
                echo "Caution.eps matches reference, regression test passed" >> "$LOG_FILE"
            else
                print_fail | tee -a "$LOG_FILE"
                echo "Caution.eps output differs from reference" >> "$LOG_FILE"
                echo "$diff_output" >> "$LOG_FILE"
                FAILED_TESTS=$((FAILED_TESTS+1))
            fi
        fi
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Caution.eps output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "All regression tests passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "$FAILED_TESTS regression tests failed" | tee -a "$LOG_FILE"
    exit 1
fi 