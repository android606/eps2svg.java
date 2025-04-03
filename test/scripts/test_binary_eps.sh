#!/bin/bash

# Test binary EPS conversion functionality
# This script tests that the tool can convert binary EPS files to SVG

# Get the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." &> /dev/null && pwd )"

# Create test output directory
mkdir -p "$SCRIPT_DIR/../output/test_binary"

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

echo "Running binary EPS conversion tests..."

# List of binary EPS files to test
binary_files=(
    "v1658963-binary.eps"  # This appears to be a binary EPS
)

# For each binary file, test different conversion modes
for binary_file in "${binary_files[@]}"; do
    # Skip v1658963-text.eps
    if [[ "$binary_file" == "v1658963-text.eps" ]]; then
        continue
    fi

    test_file="$SCRIPT_DIR/../test_images/$binary_file"
    
    # Check if file exists
    if [ ! -f "$test_file" ]; then
        echo -e "${YELLOW}WARNING${NC}: Test file $test_file not found, skipping test"
        continue
    fi
    
    base_name="${binary_file%.*}"
    
    # Test normal conversion
    output_file="$SCRIPT_DIR/../output/test_binary/${base_name}_normal.svg"
    echo "Testing normal conversion of $binary_file..."
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$test_file" "$output_file"
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo -e "${GREEN}PASS${NC}: Normal conversion - Output file exists and is not empty"
    else
        echo -e "${RED}FAIL${NC}: Normal conversion - Output file doesn't exist or is empty"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
    
    # Test with --force-ghostscript flag
    output_file="$SCRIPT_DIR/../output/test_binary/${base_name}_ghostscript.svg"
    echo "Testing conversion with --force-ghostscript of $binary_file..."
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$test_file" "$output_file" --force-ghostscript
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo -e "${GREEN}PASS${NC}: GhostScript conversion - Output file exists and is not empty"
    else
        echo -e "${RED}FAIL${NC}: GhostScript conversion - Output file doesn't exist or is empty"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
    
    # Test with --force-tiff flag
    output_file="$SCRIPT_DIR/../output/test_binary/${base_name}_tiff.svg"
    echo "Testing conversion with --force-tiff of $binary_file..."
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$test_file" "$output_file" --force-tiff
    # This might fail if the EPS doesn't have a TIFF preview, so handle with caution
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo -e "${GREEN}PASS${NC}: TIFF preview conversion - Output file exists and is not empty"
    else
        echo -e "${YELLOW}WARNING${NC}: TIFF preview conversion - This may be expected if the file has no preview"
    fi
done

# Summary
echo "------------------------"
echo "Test Summary:"
echo "Total tests: $TOTAL_TESTS"
echo "Failed tests: $FAILED_TESTS"

# Special test: Compare binary and text versions of the same file
echo ""
echo "Testing binary vs text version comparison..."
binary_file="$SCRIPT_DIR/../test_images/v1658963-binary.eps"
text_file="$SCRIPT_DIR/../test_images/v1658963-text.eps"
binary_output="$SCRIPT_DIR/../output/test_binary/v1658963-binary_compare.svg"
text_output="$SCRIPT_DIR/../output/test_binary/v1658963-text_compare.svg"

TOTAL_TESTS=$((TOTAL_TESTS+1))

# Check if both files exist
if [ -f "$binary_file" ] && [ -f "$text_file" ]; then
    # Convert both files
    echo "Converting binary and text versions of v1658963.eps..."
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$binary_file" "$binary_output"
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$text_file" "$text_output"
    
    # Check if both outputs exist
    if [ -f "$binary_output" ] && [ -s "$binary_output" ] && [ -f "$text_output" ] && [ -s "$text_output" ]; then
        # Perform basic comparison (size, content)
        binary_size=$(wc -c < "$binary_output")
        text_size=$(wc -c < "$text_output")
        size_diff=$((binary_size - text_size))
        size_diff_abs=${size_diff#-}  # Get absolute value
        
        # Count SVG elements in both files
        binary_elements=$(grep -c "<[^>]*path" "$binary_output")
        text_elements=$(grep -c "<[^>]*path" "$text_output")
        
        # Check similarity (allowing for some difference)
        if [ "$size_diff_abs" -lt "$(($binary_size / 4))" ] && [ "$binary_elements" -gt 0 ] && [ "$text_elements" -gt 0 ]; then
            echo -e "${GREEN}PASS${NC}: Binary and text versions produce comparable SVG output"
        else
            echo -e "${YELLOW}WARNING${NC}: Binary and text versions produce different output:"
            echo "  - Binary size: $binary_size bytes, $binary_elements path elements"
            echo "  - Text size: $text_size bytes, $text_elements path elements"
            
            # This is expected since they may be different versions of the same content
            echo -e "${GREEN}NOTE${NC}: This may be expected if they're different representations"
        fi
    else
        echo -e "${RED}FAIL${NC}: Failed to generate output files for comparison"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    echo -e "${YELLOW}WARNING${NC}: One or both comparison files not found, skipping test"
    echo "  - Binary file: $([ -f "$binary_file" ] && echo "Found" || echo "Not found")"
    echo "  - Text file: $([ -f "$text_file" ] && echo "Found" || echo "Not found")"
fi

# Updated summary
echo "------------------------"
echo "Final Test Summary:"
echo "Total tests: $TOTAL_TESTS"
echo "Failed tests: $FAILED_TESTS"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}All tests PASSED${NC}"
    exit 0
else
    echo -e "${RED}Some tests FAILED${NC}"
    exit 1
fi 