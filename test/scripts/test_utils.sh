#!/bin/bash

# Shared utility functions for all test scripts
# This file provides consistent formatting and utilities for test output

# Get the test directory
TEST_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$TEST_DIR/.." &> /dev/null && pwd )"

# Get the various directories
SCRIPT_DIR="$TEST_DIR/scripts"
LOGS_DIR="$TEST_DIR/logs"
OUTPUT_DIR="$TEST_DIR/output"
TEST_IMAGES_DIR="$TEST_DIR/test_images"

# Semaphore to track if test setup has been completed
TEST_SETUP_COMPLETE=${TEST_SETUP_COMPLETE:-false}

# Check if we're being called from run_tests.sh
is_called_from_run_tests() {
    local parent_script
    parent_script=$(ps -o command= $PPID | grep -o "[^/]*$" || true)
    [[ "$parent_script" == "run_tests.sh" ]]
}

# Run test setup if needed
run_test_setup_if_needed() {
    if [ "$TEST_SETUP_COMPLETE" != "true" ]; then
        echo "Running test setup..."
        "$SCRIPT_DIR/TS00_test_setup.sh"
        local setup_status=$?
        if [ $setup_status -ne 0 ]; then
            echo "Test setup failed. Please check the logs."
            exit $setup_status
        fi
        export TEST_SETUP_COMPLETE=true
    fi
}

# Create all required test directories
create_test_dirs() {
    mkdir -p "$LOGS_DIR"
    mkdir -p "$OUTPUT_DIR"
    mkdir -p "$TEST_IMAGES_DIR"
}

# Define colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get script name from the caller script
get_script_name() {
    basename "$0"
}

# Get test suite ID for the current script
# First check if TEST_SUITE_ID is defined in the calling script,
# if not, try to extract it from the filename
get_test_suite_id() {
    # If TEST_SUITE_ID is defined in the script, use it
    if [ -n "${TEST_SUITE_ID}" ]; then
        echo "${TEST_SUITE_ID}"
        return
    fi
    
    # Otherwise, try to extract it from the script name if it follows the TSxx_ pattern
    local script_name=$(get_script_name)
    if [[ $script_name =~ ^TS([0-9]+)_ ]]; then
        echo "TS${BASH_REMATCH[1]}"
        return
    fi
    
    # Default fallback
    echo "TSXX"
}

# Format log file name with test suite ID
get_log_filename() {
    local test_name=$1
    local ts_id=$(get_test_suite_id)
    echo "$ts_id-$test_name.log"
}

# Print a test suite header
print_test_suite_header() {
    local test_name=$1
    local ts_id=$(get_test_suite_id)
    echo ""
    echo "-----------------------------------------"
    echo "$ts_id: Running test suite: $test_name"
    echo "-----------------------------------------"
}

# Print indented output
print_indented() {
    local message=$1
    echo "  $message"
}

# Print test output with status
print_test_line() {
    local test_name=$1
    echo -ne "  $test_name... \c"
}

# Print a PASS result
print_pass() {
    echo -e "${GREEN}PASS${NC}"
}

# Print a FAIL result
print_fail() {
    echo -e "${RED}FAIL${NC}"
}

# Print a WARNING result
print_warning() {
    echo -e "${YELLOW}WARNING${NC}"
}

# Print the test summary footer
print_test_summary() {
    local total=$1
    local failed=$2
    local log_file=$3
    local ts_id=$(get_test_suite_id)
    
    echo "------------------------------------------"
    echo "Test Summary:"
    echo "Total tests: $total"
    echo "Failed tests: $failed"
    echo "Log file: $log_file"
    
    if [ $failed -eq 0 ]; then
        echo -e "${GREEN}All tests in suite $ts_id PASSED${NC}"
        echo ""
        echo -e "$ts_id Results: ${GREEN}✓ Test suite $ts_id PASSED${NC}"
    else
        echo -e "${RED}Some tests in suite $ts_id FAILED${NC}"
        echo ""
        echo -e "$ts_id Results: ${RED}✗ Test suite $ts_id FAILED${NC}"
    fi
}

echo "Script name: $(get_script_name); Test Suite ID: $(get_test_suite_id)"
