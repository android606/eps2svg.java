package com.convert2web.ps;

public enum PostScriptTokenType {
    INTEGER,
    REAL,
    BOOLEAN,
    NAME,
    LITERAL_NAME,
    STRING,
    HEX_STRING,
    ARRAY_START,
    ARRAY_END,
    PROCEDURE_START,
    PROCEDURE_END,
    DICTIONARY_START,
    DICTIONARY_END,
    EOF
}
