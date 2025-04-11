#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS06"
TEST_SUITE_NAME="Curve Operators"
TEST_SUITE_FILENAME=$(basename "${BASH_SOURCE[0]}" .sh)  # Script name without extension, also used for log file name

# Test script for curve operators
# This script tests the implementation of PostScript curve operators in the EPS to SVG converter

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
LOG_FILE="$LOGS_DIR/$TEST_SUITE_ID-$TEST_SUITE_FILENAME.log"  # Log file name

# Start a new log file
echo "===== $TEST_SUITE_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Testing PostScript curve operators implementation..." | tee -a "$LOG_FILE"

# Copy curve test EPS files to test_images if they don't exist there
if [ ! -f "$TEST_IMAGES_DIR/curveto_test.eps" ]; then
    print_indented "Copying curve test files to test_images directory..." | tee -a "$LOG_FILE"
    cp "$TEST_DIR/curve_tests/"*.eps "$TEST_IMAGES_DIR/" >> "$LOG_FILE" 2>&1
fi

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# Test the curveto operator
print_test_line "Testing curveto operator" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/curveto_test.eps" "$OUTPUT_DIR/curveto_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/curveto_test.svg" ] && [ -s "$OUTPUT_DIR/curveto_test.svg" ]; then
    # Check for the presence of Bézier curve commands in the SVG
    if grep -q "C" "$OUTPUT_DIR/curveto_test.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found Bézier curve commands in curveto_test.svg" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No Bézier curve commands found in curveto_test.svg" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to create curveto_test.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test the v operator
print_test_line "Testing v operator" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/v_test.eps" "$OUTPUT_DIR/v_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/v_test.svg" ] && [ -s "$OUTPUT_DIR/v_test.svg" ]; then
    # Check for the presence of Bézier curve commands in the SVG
    if grep -q "C" "$OUTPUT_DIR/v_test.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found Bézier curve commands in v_test.svg" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No Bézier curve commands found in v_test.svg" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to create v_test.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test the y operator
print_test_line "Testing y operator" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/y_test.eps" "$OUTPUT_DIR/y_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/y_test.svg" ] && [ -s "$OUTPUT_DIR/y_test.svg" ]; then
    # Check for the presence of Bézier curve commands in the SVG
    if grep -q "C" "$OUTPUT_DIR/y_test.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found Bézier curve commands in y_test.svg" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No Bézier curve commands found in y_test.svg" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to create y_test.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test the arc operator
print_test_line "Testing arc operator" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arc_test.eps" "$OUTPUT_DIR/arc_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/arc_test.svg" ] && [ -s "$OUTPUT_DIR/arc_test.svg" ]; then
    # For arc, we should see a path with either curve or line segments
    if grep -q " d=" "$OUTPUT_DIR/arc_test.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found path data in arc_test.svg" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No path data found in arc_test.svg" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to create arc_test.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test the arcn operator
print_test_line "Testing arcn operator" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arcn_test.eps" "$OUTPUT_DIR/arcn_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/arcn_test.svg" ] && [ -s "$OUTPUT_DIR/arcn_test.svg" ]; then
    # For arcn, we should see a path with either curve or line segments
    if grep -q " d=" "$OUTPUT_DIR/arcn_test.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found path data in arcn_test.svg" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No path data found in arcn_test.svg" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to create arcn_test.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test the arct operator
print_test_line "Testing arct operator" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arct_test.eps" "$OUTPUT_DIR/arct_test.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/arct_test.svg" ] && [ -s "$OUTPUT_DIR/arct_test.svg" ]; then
    # For arct, we should see a path with either curve or line segments
    if grep -q " d=" "$OUTPUT_DIR/arct_test.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found path data in arct_test.svg" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No path data found in arct_test.svg" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to create arct_test.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "All curve operator tests passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "$FAILED_TESTS curve operator tests failed" | tee -a "$LOG_FILE"
    exit 1
fi
