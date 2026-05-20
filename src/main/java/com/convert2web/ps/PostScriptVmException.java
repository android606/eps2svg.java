package com.convert2web.ps;

/**
 * Runtime error during PostScript execution (stack underflow, typecheck, etc.).
 */
public final class PostScriptVmException extends RuntimeException {
    public PostScriptVmException(String message) {
        super(message);
    }
}
