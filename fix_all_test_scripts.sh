#!/bin/bash

# Script to fix all test scripts and ensure they have proper TEST_SUITE_NAME variables
echo "Fixing all test scripts..."

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

for script in test/scripts/TS*_*.sh; do
  script_name=$(basename "$script")
  echo "Processing $script_name"
  
  # Get the friendly name for this script
  friendly_name="${FRIENDLY_NAMES[$script_name]}"
  if [ -z "$friendly_name" ]; then
    friendly_name=$(echo "$script_name" | sed -E 's/TS[0-9]+_test_//g' | sed -E 's/\.sh$//g' | sed 's/_/ /g' | sed -E 's/\b(\w)/\U\1/g')
    echo "  Generated friendly name: $friendly_name"
  else
    echo "  Using predefined friendly name: $friendly_name"
  fi
  
  # Extract the test suite ID
  ts_id=$(grep -E "TEST_SUITE_ID\s*=\s*\"TS[0-9]+\"" "$script" | grep -oE "TS[0-9]+" || echo "")
  if [ -z "$ts_id" ]; then
    echo "  Warning: Could not extract TEST_SUITE_ID from $script, skipping"
    continue
  fi
  
  # Create a temporary file
  temp_file="${script}.temp"
  
  # Process the script line by line
  inside_header=false
  added_name=false
  while IFS= read -r line; do
    if [[ "$line" =~ TEST_SUITE_ID=.*\"TS[0-9]+\" ]]; then
      echo "$line" >> "$temp_file"
      echo "TEST_SUITE_NAME=\"$friendly_name\"" >> "$temp_file"
      inside_header=true
      added_name=true
      continue
    fi
    
    # Skip existing TEST_SUITE_NAME lines if we've added a new one
    if [[ "$added_name" == "true" && "$line" =~ TEST_SUITE_NAME= ]]; then
      continue
    fi
    
    # Add the line to the temp file
    echo "$line" >> "$temp_file"
  done < "$script"
  
  # Replace the original file with the temp file
  mv "$temp_file" "$script"
  
  echo "  Updated $script with TEST_SUITE_NAME=\"$friendly_name\""
done

echo "All test scripts fixed!" 