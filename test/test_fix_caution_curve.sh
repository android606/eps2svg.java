#!/bin/bash
# Test the fix for caution.eps curve issue

WORKSPACE_DIR=$(pwd)
TEST_OUTPUT_DIR="$WORKSPACE_DIR/test/output"
TEST_IMAGES_DIR="$WORKSPACE_DIR/test/test_images"

# Make sure output directory exists
mkdir -p "$TEST_OUTPUT_DIR"

# Compile the project
echo "Compiling project..."
mvn clean package -DskipTests

# Clear previous output
rm -f "$TEST_OUTPUT_DIR/caution_fixed_curve.svg"

# Run the conversion with our fix
echo "Converting caution.eps with fixed curve handling..."
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "$TEST_IMAGES_DIR/caution.eps" \
  "$TEST_OUTPUT_DIR/caution_fixed_curve.svg"

if [ -f "$TEST_OUTPUT_DIR/caution_fixed_curve.svg" ]; then
  echo "Conversion successful!"
  
  # Check for the problematic curve segment in the output SVG
  if grep -q "L2.336 2.098" "$TEST_OUTPUT_DIR/caution_fixed_curve.svg"; then
    echo "ERROR: The problematic segment 'L2.336 2.098' is still present in the output."
    exit 1
  else
    echo "SUCCESS: The problematic segment 'L2.336 2.098' has been fixed!"
    echo "The SVG file is at: $TEST_OUTPUT_DIR/caution_fixed_curve.svg"
    exit 0
  fi
else
  echo "ERROR: Conversion failed. No output file was created."
  exit 1
fi 