package com.convert2web.ps;

import java.io.IOException;

public final class PostScriptParseException extends IOException {
    public PostScriptParseException(String message) {
        super(message);
    }

    public PostScriptParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
