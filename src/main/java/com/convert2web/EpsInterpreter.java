package com.convert2web;

import java.util.Stack;
import java.util.logging.Logger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.logging.Level;

/**
 * Interprets PostScript commands from an EPS file and calls a GraphicsHandler 
 * to perform graphical operations.
 */
public class EpsInterpreter {
    private static final Logger logger = Logger.getLogger(EpsInterpreter.class.getName());

    private Stack<Object> operandStack = new Stack<>(); // Handles numbers, names, arrays etc.
    // TODO: Add dictionary stack if needed for full PS support
    private GraphicsHandler graphicsHandler;
    private double[] parsedBbox = null;

    public EpsInterpreter(GraphicsHandler handler) {
        this.graphicsHandler = handler;
    }

    public void processEps(String inputEpsPath) throws IOException {
        logger.info("Starting EPS interpretation for: " + inputEpsPath);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputEpsPath), StandardCharsets.ISO_8859_1))) {
            boolean headerProcessed = false;
            String line = null; // Initialize line to null

            // --- Header Processing Phase ---
            while (!headerProcessed && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("%%BoundingBox:")) {
                    parseBoundingBox(line);
                    headerProcessed = true; // Found BBox, header done
                    continue; // Continue to next phase
                } else if (line.startsWith("%%EndComments")) {
                     logger.warning("%%BoundingBox not found before %%EndComments.");
                    headerProcessed = true; // Header done, even without BBox
                    continue; // Continue to next phase
                } else if (line.startsWith("%")) {
                    logger.fine("(Header) Ignoring comment: " + line);
                    continue;
                } else {
                     logger.warning("Non-comment line encountered before BBox/EndComments: " + line + ". Treating header as processed.");
                    headerProcessed = true; 
                    // Process this first non-comment line as part of the body below
                }
                 if (headerProcessed) break; // Exit header loop if processed (by finding BBox or EndComments)
            }
            
             // Initialize the graphics handler *after* parsing BBox
             graphicsHandler.initialize(parsedBbox);

            // --- Body Processing Phase ---
            do { // Use do-while to process the line read before exiting header loop
                if (line == null) break; // If loop exited due to EOF
                line = line.trim();
                 if (line.isEmpty()) continue;
                 
                if (line.startsWith("%")) { // Skip comments in body
                    if (line.startsWith("%%EOF")) {
                         logger.fine("%%EOF encountered. Finalizing.");
                         break; // Stop processing
                    }
                    logger.fine("(Body) Ignoring comment: " + line);
                    continue;
                }
                
                // Tokenize and process the line
                StringTokenizer tokenizer = new StringTokenizer(line);
                while (tokenizer.hasMoreTokens()) {
                    processToken(tokenizer.nextToken());
                }
            } while ((line = reader.readLine()) != null);

            logger.info("Finished interpreting EPS file.");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading EPS file: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // Catch potential exceptions from processToken or handler calls
            logger.log(Level.SEVERE, "Error during EPS interpretation: " + e.getMessage(), e);
            throw new RuntimeException("Interpretation failed: " + e.getMessage(), e);
        }
    }

    private void parseBoundingBox(String line) {
         try {
             String[] parts = line.substring("%%BoundingBox:".length()).trim().split("\\s+");
             if (parts.length >= 4) {
                 parsedBbox = new double[4];
                 parsedBbox[0] = Double.parseDouble(parts[0]);
                 parsedBbox[1] = Double.parseDouble(parts[1]);
                 parsedBbox[2] = Double.parseDouble(parts[2]);
                 parsedBbox[3] = Double.parseDouble(parts[3]);
                 logger.fine("Parsed %%BoundingBox: " + Arrays.toString(parsedBbox));
             } else {
                 logger.warning("Invalid %%BoundingBox format: " + line);
             }
         } catch (NumberFormatException e) {
             logger.warning("Could not parse number in %%BoundingBox: " + line + " -> " + e.getMessage());
         }
     }

    private void processToken(String token) {
        // Simplified: Try parsing as number first
        try {
            double num = Double.parseDouble(token);
            operandStack.push(num);
            logger.fine("Pushed number: " + num);
            return;
        } catch (NumberFormatException e) {
            // Not a number, treat as operator/name
            logger.fine("Processing operator/name: " + token);
        }

        // Handle operators (simplified example)
        switch (token) {
            // --- Stack Operators (Internal) ---
            case "pop":
                if (!operandStack.isEmpty()) operandStack.pop();
                else logger.warning("pop: stack underflow");
                break;
            case "dup":
                 if (!operandStack.isEmpty()) operandStack.push(operandStack.peek());
                 else logger.warning("dup: stack underflow");
                break;
            case "exch":
                 if (operandStack.size() >= 2) {
                     Object o1 = operandStack.pop();
                     Object o2 = operandStack.pop();
                     operandStack.push(o1);
                     operandStack.push(o2);
                 } else logger.warning("exch: stack underflow");
                break;
            // TODO: Add more stack ops: clear, count, index, roll, etc.

            // --- Arithmetic Operators (Internal) ---
            case "add":
                if (operandStack.size() >= 2) {
                    Object o1 = operandStack.pop(); Object o2 = operandStack.pop();
                    if (o1 instanceof Number && o2 instanceof Number) {
                        operandStack.push(((Number)o2).doubleValue() + ((Number)o1).doubleValue());
                    } else { logger.warning("add: non-numeric operands"); operandStack.push(o2); operandStack.push(o1); }
                } else logger.warning("add: stack underflow");
                break;
            // TODO: Add sub, mul, div, mod, neg, etc.

            // --- Graphics Operators (Delegate to Handler) ---
            case "m": case "moveto":
                 if (operandStack.size() >= 2) {
                    Object y = operandStack.pop(); Object x = operandStack.pop();
                     if (x instanceof Number && y instanceof Number) graphicsHandler.moveTo(((Number)x).doubleValue(), ((Number)y).doubleValue());
                     else { logger.warning("moveto: non-numeric coords"); operandStack.push(x); operandStack.push(y); }
                 } else logger.warning("moveto: stack underflow");
                break;
            case "l": case "lineto":
                if (operandStack.size() >= 2) {
                   Object y = operandStack.pop(); Object x = operandStack.pop();
                    if (x instanceof Number && y instanceof Number) graphicsHandler.lineTo(((Number)x).doubleValue(), ((Number)y).doubleValue());
                    else { logger.warning("lineto: non-numeric coords"); operandStack.push(x); operandStack.push(y); }
                } else logger.warning("lineto: stack underflow");
               break;
            case "c": case "curveto":
                if (operandStack.size() >= 6) {
                    Object y3=operandStack.pop(); Object x3=operandStack.pop();
                    Object y2=operandStack.pop(); Object x2=operandStack.pop();
                    Object y1=operandStack.pop(); Object x1=operandStack.pop();
                    if (x1 instanceof Number && y1 instanceof Number && x2 instanceof Number && y2 instanceof Number && x3 instanceof Number && y3 instanceof Number) {
                        graphicsHandler.curveTo(((Number)x1).doubleValue(), ((Number)y1).doubleValue(), ((Number)x2).doubleValue(), ((Number)y2).doubleValue(), ((Number)x3).doubleValue(), ((Number)y3).doubleValue());
                    } else { /* push back args */ logger.warning("curveto: non-numeric coords"); /* ... */ }
                } else logger.warning("curveto: stack underflow");
               break;
            case "h": case "closepath": graphicsHandler.closePath(); break;
            case "n": case "newpath": graphicsHandler.newPath(); break;
            case "f": case "fill": graphicsHandler.fill(); break;
            case "F": case "eofill": graphicsHandler.eoFill(); break;
            case "S": case "stroke": graphicsHandler.stroke(); break;
            case "q": case "gsave": graphicsHandler.gsave(); break;
            case "Q": case "grestore": graphicsHandler.grestore(); break;
            case "cm": case "concat":
                 if (operandStack.size() >= 6) {
                     double[] matrix = new double[6];
                     boolean ok = true;
                     // Pop carefully, check types
                     for (int i = 5; i >= 0; i--) { 
                         if (operandStack.peek() instanceof Number) matrix[i] = ((Number)operandStack.pop()).doubleValue();
                         else { ok = false; logger.warning("cm: non-numeric matrix element"); break; } 
                     }
                     if(ok) graphicsHandler.concatMatrix(matrix);
                     // TODO: Push back args if not ok? Requires more stack manipulation.
                 } else logger.warning("cm: stack underflow");
                 break;
            case "w": case "setlinewidth":
                if (!operandStack.isEmpty() && operandStack.peek() instanceof Number) graphicsHandler.setLineWidth(((Number)operandStack.pop()).doubleValue());
                else logger.warning("setlinewidth: stack underflow or non-numeric operand");
                break;
            case "g": case "setgray":
                 if (!operandStack.isEmpty() && operandStack.peek() instanceof Number) graphicsHandler.setGrayFill(((Number)operandStack.pop()).doubleValue());
                 else logger.warning("setgray: stack underflow or non-numeric operand");
                break;
             case "k": case "setcmykcolor":
                 if (operandStack.size() >= 4 /* && all are Numbers */ ) { // Simplified check
                     double k = ((Number)operandStack.pop()).doubleValue();
                     double y = ((Number)operandStack.pop()).doubleValue();
                     double m = ((Number)operandStack.pop()).doubleValue();
                     double c = ((Number)operandStack.pop()).doubleValue();
                     graphicsHandler.setCMYKColorFill(c, m, y, k);
                 } else logger.warning("setcmykcolor: stack underflow or non-numeric");
                break;
            // TODO: Add cases for setrgbcolor, setlinecap, setlinejoin, setmiterlimit, setdash etc.

            // --- Dictionary/Control Flow (Basic Placeholders) ---
            case "dict":
                 if (!operandStack.isEmpty() && operandStack.peek() instanceof Number) {
                     int size = ((Number)operandStack.pop()).intValue();
                      logger.warning("'dict' operator size " + size + " - Ignoring (no dict stack)");
                 } else logger.warning("dict: stack underflow or non-numeric size");
                 break;
            case "begin": logger.warning("'begin' operator - Ignoring (no dict stack)"); break;
            case "end": logger.warning("'end' operator - Ignoring (no dict stack)"); break;
            case "def": 
                 if (operandStack.size() >= 2) { operandStack.pop(); operandStack.pop(); /* Ignore key/value */ }
                 logger.warning("'def' operator - Ignoring (no dict stack)"); 
                break;
            case "{": logger.warning("'{' (proc start) - Ignoring"); break; // Need exec handling
            case "}": logger.warning("'}' (proc end) - Ignoring"); break; // Need exec handling
            case "if": case "ifelse": logger.warning("'if/ifelse' - Ignoring"); break; // Need proc exec
            case "exec": logger.warning("'exec' - Ignoring"); break; // Need proc exec

             // --- Known Ignored Tokens ---
             case "Adobe_packedarray": case "initialize": case "terminate": case "packedarray": 
             case "setpacking": case "currentpacking": case "Adobe_cshow": case "cshow":
             case "Adobe_customcolor": case "findcmykcustomcolor": case "setcustomcolor": 
             case "setoverprint": case "Adobe_IllustratorA_AI3": case "annotatepage":
             case "showpage": case "A": case "u": case "O": case "*u": case "*U": case "D": case "U":
                 logger.fine("Ignoring known token: " + token);
                 break;

            default:
                // Is it a name (starts with /)?
                if (token.startsWith("/")) {
                    operandStack.push(token); // Push name literal onto stack for now
                    logger.fine("Pushed name: " + token);
                } else {
                    logger.warning("Unhandled token/potential error: " + token);
                    // Maybe try looking up in dictionary if implemented?
                }
        }
    }
} 