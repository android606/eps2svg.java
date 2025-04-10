#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TC01"  # Curve Tests 01

# Set the project root directory
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test"
CURVE_TESTS_DIR="$TEST_DIR/curve_tests"
OUTPUT_DIR="$TEST_DIR/output"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Compile if needed
JAR_FILE="$PROJECT_ROOT/target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Compiling project..."
    cd "$PROJECT_ROOT" && mvn clean package
fi

echo "Testing curve preservation..."

# Test with our clear curve demonstration file
echo "Converting curve_demo.eps with curve preservation (default)..."
java -jar "$JAR_FILE" "$CURVE_TESTS_DIR/curve_demo.eps" "$OUTPUT_DIR/curve_demo_preserved.svg"

echo "Converting curve_demo.eps with curve flattening enabled..."
java -Dconvert2web.flattenCurves=true -jar "$JAR_FILE" "$CURVE_TESTS_DIR/curve_demo.eps" "$OUTPUT_DIR/curve_demo_flattened.svg"

# Test with curve preservation (default behavior)
echo "Converting with curve preservation (default)..."
java -jar "$JAR_FILE" "$CURVE_TESTS_DIR/arc_test.eps" "$OUTPUT_DIR/arc_test_preserved.svg"

# Test with curve flattening
echo "Converting with curve flattening enabled..."
java -Dconvert2web.flattenCurves=true -jar "$JAR_FILE" "$CURVE_TESTS_DIR/arc_test.eps" "$OUTPUT_DIR/arc_test_flattened.svg"

# For caution.eps test
echo "Converting caution.eps with curve preservation (default)..."
java -jar "$JAR_FILE" "$TEST_DIR/test_images/caution.eps" "$OUTPUT_DIR/caution_preserved.svg"

echo "Converting caution.eps with curve flattening enabled..."
java -Dconvert2web.flattenCurves=true -jar "$JAR_FILE" "$TEST_DIR/test_images/caution.eps" "$OUTPUT_DIR/caution_flattened.svg"

# Add diagnostic outputs
echo "Checking for curve commands in preserved files:"
echo "arc_test_preserved.svg curves: $(grep -o "C [0-9].*" "$OUTPUT_DIR/arc_test_preserved.svg" | wc -l)"
echo "caution_preserved.svg curves: $(grep -o "C [0-9].*" "$OUTPUT_DIR/caution_preserved.svg" | wc -l)"
echo "curve_demo_preserved.svg curves: $(grep -o "C [0-9].*" "$OUTPUT_DIR/curve_demo_preserved.svg" | wc -l)"

echo "Checking for curve commands in flattened files:"
echo "arc_test_flattened.svg curves: $(grep -o "C [0-9].*" "$OUTPUT_DIR/arc_test_flattened.svg" | wc -l)"
echo "caution_flattened.svg curves: $(grep -o "C [0-9].*" "$OUTPUT_DIR/caution_flattened.svg" | wc -l)"
echo "curve_demo_flattened.svg curves: $(grep -o "C [0-9].*" "$OUTPUT_DIR/curve_demo_flattened.svg" | wc -l)"

# Check operator recognition in logs
echo "Checking debug logs for curveto operator handling:"
echo "Caution preserved log - c operator counts:"
grep -c "Found user-defined operator: c" caution_preserved_debug.log

echo "Tests completed. Check output files in $OUTPUT_DIR"
echo "To compare results, you can view the SVG files in a browser or SVG viewer." 