#!/bin/bash

# Test basic conversion functionality
# This script tests that the tool can convert EPS files to SVG

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." &> /dev/null && pwd )"

# Create test output directory
mkdir -p "$SCRIPT_DIR/../output/test_basic"

# Define colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Build the project
cd "$PROJECT_ROOT"
echo "Building project..."
mvn clean package -q

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

echo "Running basic conversion tests..."

# Test basic fill
test_file="$SCRIPT_DIR/../test_images/test_basic_fill.eps"
output_file="$SCRIPT_DIR/../output/test_basic/test_basic_fill.svg"
echo "Testing $test_file..."
TOTAL_TESTS=$((TOTAL_TESTS+1))

java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$test_file" "$output_file"
if [ -f "$output_file" ] && [ -s "$output_file" ]; then
    echo -e "${GREEN}PASS${NC}: Basic fill test - Output file exists and is not empty"
else
    echo -e "${RED}FAIL${NC}: Basic fill test - Output file doesn't exist or is empty"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test basic stroke
test_file="$SCRIPT_DIR/../test_images/test_basic_stroke.eps"
output_file="$SCRIPT_DIR/../output/test_basic/test_basic_stroke.svg"
echo "Testing $test_file..."
TOTAL_TESTS=$((TOTAL_TESTS+1))

java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$test_file" "$output_file"
if [ -f "$output_file" ] && [ -s "$output_file" ]; then
    echo -e "${GREEN}PASS${NC}: Basic stroke test - Output file exists and is not empty"
else
    echo -e "${RED}FAIL${NC}: Basic stroke test - Output file doesn't exist or is empty"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Basic SVG validation - check if output SVG contains expected elements
echo "Checking if SVG contains required elements..."
TOTAL_TESTS=$((TOTAL_TESTS+1))

if grep -q "<.*svg" "$SCRIPT_DIR/../output/test_basic/test_basic_fill.svg" && 
   grep -q "<.*path" "$SCRIPT_DIR/../output/test_basic/test_basic_fill.svg"; then
    echo -e "${GREEN}PASS${NC}: SVG validation - File contains expected SVG elements"
else
    echo -e "${RED}FAIL${NC}: SVG validation - File missing expected SVG elements"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

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