package com.convert2web.ps;

@FunctionalInterface
public interface PostScriptOperator {
    void execute(PostScriptVm vm);
}
