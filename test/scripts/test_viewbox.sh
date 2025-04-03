#!/bin/bash

# Test viewbox-related functionality
# This script tests that the tool sets the correct viewbox in SVG output

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." &> /dev/null && pwd )"

# Create test output directory
mkdir -p "$SCRIPT_DIR/../output/test_viewbox"

# Define colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# Build the project if needed
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    echo "Building project..."
    mvn clean package -q
fi

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

echo "Running viewbox tests..."

# Test files with known bounding boxes
declare -A test_files_bbox
test_files_bbox["test_basic_fill.eps"]="0 0 100 100"
test_files_bbox["test_basic_stroke.eps"]="0 0 100 100"

# Function to compare viewBox values ignoring precision formatting
compare_viewbox() {
    local actual="$1"
    local expected="$2"
    
    # Extract the values as arrays
    local actual_values=($actual)
    local expected_values=($expected)
    
    # Convert values to integers (truncate decimal places)
    local actual_int0=$(printf "%.0f" "${actual_values[0]}")
    local actual_int1=$(printf "%.0f" "${actual_values[1]}")
    local actual_int2=$(printf "%.0f" "${actual_values[2]}")
    local actual_int3=$(printf "%.0f" "${actual_values[3]}")
    
    local expected_int0=$(printf "%.0f" "${expected_values[0]}")
    local expected_int1=$(printf "%.0f" "${expected_values[1]}")
    local expected_int2=$(printf "%.0f" "${expected_values[2]}")
    local expected_int3=$(printf "%.0f" "${expected_values[3]}")
    
    # Compare values as integers (ignore decimal places)
    if [ "$actual_int0" -eq "$expected_int0" ] && 
       [ "$actual_int1" -eq "$expected_int1" ] && 
       [ "$actual_int2" -eq "$expected_int2" ] && 
       [ "$actual_int3" -eq "$expected_int3" ]; then
        return 0  # Success
    else
        return 1  # Failure
    fi
}

# For each test file, check if viewBox is set correctly
for test_file in "${!test_files_bbox[@]}"; do
    input_file="$SCRIPT_DIR/../test_images/$test_file"
    output_file="$SCRIPT_DIR/../output/test_viewbox/$test_file.svg"
    expected_bbox="${test_files_bbox[$test_file]}"
    
    echo "Testing viewbox for $test_file (expected: $expected_bbox)..."
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    # Convert file
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$input_file" "$output_file"
    
    # Check viewBox attribute
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        viewbox=$(grep -o 'viewBox="[^"]*"' "$output_file" | sed 's/viewBox="\([^"]*\)"/\1/')
        
        if [ -n "$viewbox" ]; then
            # Compare ignoring decimal precision
            if compare_viewbox "$viewbox" "$expected_bbox"; then
                echo -e "${GREEN}PASS${NC}: ViewBox correct - Found: $viewbox, Expected: $expected_bbox"
            else
                echo -e "${RED}FAIL${NC}: ViewBox incorrect - Found: $viewbox, Expected: $expected_bbox"
                FAILED_TESTS=$((FAILED_TESTS+1))
            fi
        else
            echo -e "${RED}FAIL${NC}: ViewBox attribute not found"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
    else
        echo -e "${RED}FAIL${NC}: Output file doesn't exist or is empty"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
done

# Summary
echo "------------------------"
echo "Test Summary:"
echo "Total tests: $TOTAL_TESTS"
echo "Failed tests: $FAILED_TESTS"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}All tests PASSED${NC}"
    exit 0
else
    echo -e "${RED}Some tests FAILED${NC}"
    exit 1
fi 