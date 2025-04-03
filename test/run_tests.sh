#!/bin/bash

# Master script to run all tests for EPS to SVG conversion

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"

# Create scripts directory if it doesn't exist (although it should)
mkdir -p "$SCRIPT_DIR/scripts"

# Define colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Make all test scripts executable
chmod +x "$SCRIPT_DIR/scripts/test_basic_conversion.sh"
chmod +x "$SCRIPT_DIR/scripts/test_binary_eps.sh"
chmod +x "$SCRIPT_DIR/scripts/test_viewbox.sh"
chmod +x "$SCRIPT_DIR/scripts/test_path_bounds.sh" 
chmod +x "$SCRIPT_DIR/scripts/test_visual.sh"

# Count tests and failures
TOTAL_SUITES=0
FAILED_SUITES=0

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Starting EPS to SVG Conversion Test Suite${NC}"
echo -e "${BLUE}=========================================${NC}"

# Function to run a test script and track results
run_test_suite() {
    local test_script=$1
    local test_name=$2
    
    echo -e "\n${BLUE}Running test suite: ${test_name}${NC}"
    echo -e "${BLUE}-----------------------------------------${NC}"
    
    TOTAL_SUITES=$((TOTAL_SUITES+1))
    
    # Run the test script
    "$test_script"
    local result=$?
    
    if [ $result -eq 0 ]; then
        echo -e "\n${GREEN}✓ Test suite ${test_name} PASSED${NC}"
    else
        echo -e "\n${RED}✗ Test suite ${test_name} FAILED${NC}"
        FAILED_SUITES=$((FAILED_SUITES+1))
    fi
    
    return $result
}

# Run each test suite
run_test_suite "$SCRIPT_DIR/scripts/test_basic_conversion.sh" "Basic Conversion"
run_test_suite "$SCRIPT_DIR/scripts/test_binary_eps.sh" "Binary EPS Support"
run_test_suite "$SCRIPT_DIR/scripts/test_viewbox.sh" "ViewBox Correctness"
run_test_suite "$SCRIPT_DIR/scripts/test_path_bounds.sh" "Path Boundaries"
run_test_suite "$SCRIPT_DIR/scripts/test_visual.sh" "Visual Quality"

# Summary
echo -e "\n${BLUE}=========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}=========================================${NC}"
echo "Total test suites: $TOTAL_SUITES"
echo "Failed test suites: $FAILED_SUITES"

if [ $FAILED_SUITES -eq 0 ]; then
    echo -e "\n${GREEN}✓ ALL TEST SUITES PASSED${NC}"
    exit 0
else
    echo -e "\n${RED}✗ SOME TEST SUITES FAILED${NC}"
    exit 1
fi 