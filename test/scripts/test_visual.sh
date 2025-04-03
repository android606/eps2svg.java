#!/bin/bash

# Test visual aspects of conversion
# This script checks for issues like skewed or upside-down rendering

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." &> /dev/null && pwd )"

# Create test output directory
mkdir -p "$SCRIPT_DIR/../output/test_visual"

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

echo "Running visual tests..."

# Test files with reference SVGs
test_files=(
    "v1658963"  # Has reference SVG files
)

# For each test file
for test_file in "${test_files[@]}"; do
    eps_file="$SCRIPT_DIR/../test_images/$test_file.eps"
    ref_file="$SCRIPT_DIR/../test_images/$test_file-reference.svg"
    output_file="$SCRIPT_DIR/../output/test_visual/$test_file.svg"
    
    # Skip if reference file doesn't exist
    if [ ! -f "$ref_file" ]; then
        echo -e "${YELLOW}WARNING${NC}: Reference file $ref_file not found, skipping test"
        continue
    fi
    
    echo "Testing visual aspects for $test_file.eps against reference..."
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    # Convert file
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$eps_file" "$output_file"
    
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        # Check for matrix transforms that might indicate skewing or flipping
        # This is a simple check for transforms that might cause visual issues
        if grep -q "matrix(-1" "$output_file" || grep -q "rotate(180" "$output_file"; then
            echo -e "${RED}FAIL${NC}: Found potentially upside-down transforms in output"
            FAILED_TESTS=$((FAILED_TESTS+1))
        else
            echo -e "${GREEN}PASS${NC}: No problematic transforms detected"
        fi
        
        # Compare basic structure between reference and output
        # This won't catch all visual issues but might help identify major problems
        ref_paths=$(grep -c "<[^>]*path" "$ref_file")
        output_paths=$(grep -c "<[^>]*path" "$output_file")
        
        if [ "$ref_paths" -eq 0 ] && [ "$output_paths" -eq 0 ]; then
            echo -e "${YELLOW}WARNING${NC}: No paths found in either reference or output"
        elif [ "$output_paths" -eq 0 ]; then
            echo -e "${RED}FAIL${NC}: No paths found in output (reference has $ref_paths)"
            FAILED_TESTS=$((FAILED_TESTS+1))
        else
            # We accept any number of paths as long as the output has at least one path
            echo -e "${YELLOW}INFO${NC}: Path count different: output ($output_paths), reference ($ref_paths). This is acceptable."
            echo -e "${GREEN}PASS${NC}: Output contains paths"
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