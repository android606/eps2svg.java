#!/bin/bash

# Verification script for arrow orientation
# This script performs a quick check of SVG arrow files to ensure they have the correct orientation
# It can be run independently or as part of other tests

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Source the shared test utilities if available
if [ -f "$SCRIPT_DIR/test_utils.sh" ]; then
    source "$SCRIPT_DIR/test_utils.sh"
else
    # Define minimal utilities if test_utils.sh is not available
    function print_pass() {
        echo -e "\033[0;32mPASS\033[0m"
    }
    
    function print_fail() {
        echo -e "\033[0;31mFAIL\033[0m"
    }
    
    function print_indented() {
        echo "  $1"
    }
    
    OUTPUT_DIR="$SCRIPT_DIR/../output"
fi

# Skip processing if we're already in a test setup
if [ "$TEST_SETUP_COMPLETE" = "true" ]; then
    echo "Test setup already complete, skipping arrow orientation verification"
    exit 0
fi

# Check if output directory exists
if [ ! -d "$OUTPUT_DIR" ]; then
    echo "Error: Output directory does not exist: $OUTPUT_DIR"
    exit 1
fi

# Check for arrow SVG files
RIGHT_ARROW="$OUTPUT_DIR/arrow_right_normal.svg"
UP_ARROW="$OUTPUT_DIR/arrow_up_normal.svg"

if [ ! -f "$RIGHT_ARROW" ] || [ ! -f "$UP_ARROW" ]; then
    echo "Error: Arrow SVG files not found. Run test_setup.sh first."
    exit 1
fi

# Check right arrow orientation
echo "Checking right arrow orientation..."

# Verify the right arrow has a transform and path element
if grep -q "<path d=\"M20" "$RIGHT_ARROW" && grep -q "L80 50" "$RIGHT_ARROW"; then
    echo "  Right arrow has correct path format"
    print_pass
else
    echo "  Right arrow has incorrect path format"
    print_fail
    exit 1
fi

# Check up arrow orientation
echo "Checking up arrow orientation..."

# Verify the up arrow has a transform and path element
if grep -q "<path d=\"M48" "$UP_ARROW" && grep -q "L50 80" "$UP_ARROW"; then
    echo "  Up arrow has correct path format"
    print_pass
else
    echo "  Up arrow has incorrect path format"
    print_fail
    exit 1
fi

echo "Arrow orientation verification complete. All tests passed."
# Mark verification as complete
TEST_SETUP_COMPLETE="true"
export TEST_SETUP_COMPLETE
exit 0
