#!/bin/bash

# Script to update all test scripts to add friendly TEST_SUITE_NAME
echo "Updating test script friendly names..."

# Define friendly names for each test script
declare -A FRIENDLY_NAMES
FRIENDLY_NAMES["TS00_test_setup.sh"]="Test Setup"
FRIENDLY_NAMES["TS01_test_basic_conversion.sh"]="Basic Conversion"
FRIENDLY_NAMES["TS02_test_binary_eps.sh"]="Binary EPS Support"
FRIENDLY_NAMES["TS03_test_viewbox.sh"]="ViewBox Correctness"
FRIENDLY_NAMES["TS04_test_path_bounds.sh"]="Path Boundaries"
FRIENDLY_NAMES["TS05_test_visual.sh"]="Visual Quality"
FRIENDLY_NAMES["TS06_test_curve_operators.sh"]="Curve Operators"
FRIENDLY_NAMES["TS07_test_curve_preservation.sh"]="Curve Preservation"
FRIENDLY_NAMES["TS08_test_subtle_curves.sh"]="Subtle Curves"
FRIENDLY_NAMES["TS09_test_fix_caution_curve.sh"]="Caution Curve Fix"
FRIENDLY_NAMES["TS10_test_svg_content.sh"]="SVG Content"
FRIENDLY_NAMES["TS11_test_basic_orientation.sh"]="Basic Orientation"
FRIENDLY_NAMES["TS12_test_color_preservation.sh"]="Color Preservation"
FRIENDLY_NAMES["TS13_test_complex_shapes.sh"]="Complex Shapes"
FRIENDLY_NAMES["TS14_test_y_operator_regression.sh"]="Y Operator Regression"
FRIENDLY_NAMES["TS15_test_negative_y_curves_regression.sh"]="Negative Y Curves Regression"

# Process each test script
for script in test/scripts/TS*_*.sh; do
  script_name=$(basename "$script")
  friendly_name="${FRIENDLY_NAMES[$script_name]}"
  
  # If no friendly name is defined, generate one from the filename
  if [ -z "$friendly_name" ]; then
    friendly_name=$(echo "$script_name" | sed -E 's/TS[0-9]+_test_//g' | sed -E 's/\.sh$//g' | sed 's/_/ /g' | sed -E 's/\b(\w)/\U\1/g')
    echo "  Generated friendly name for $script_name: $friendly_name"
  else
    echo "  Using predefined friendly name for $script_name: $friendly_name"
  fi
  
  # Extract the test suite ID
  ts_id=$(grep -E "TEST_SUITE_ID\s*=\s*\"TS[0-9]+\"" "$script" | grep -oE "TS[0-9]+" || echo "")
  
  if [ -z "$ts_id" ]; then
    echo "  Warning: Could not extract TEST_SUITE_ID from $script, skipping"
    continue
  fi
  
  # Back up the original file
  cp "$script" "${script}.name.bak"
  
  # Update the TEST_SUITE_NAME line if it exists
  if grep -q "TEST_SUITE_NAME=" "$script"; then
    sed -i "s/TEST_SUITE_NAME=.*/TEST_SUITE_NAME=\"$friendly_name\"/" "$script"
    echo "  Updated TEST_SUITE_NAME in $script"
  else
    # Add TEST_SUITE_NAME after TEST_SUITE_ID if it doesn't exist
    sed -i "/TEST_SUITE_ID=\"$ts_id\"/a TEST_SUITE_NAME=\"$friendly_name\"" "$script"
    echo "  Added TEST_SUITE_NAME to $script"
  fi
done

echo "All test scripts updated with friendly names!" 