package dev.blanke.indyobfuscator.template;

import java.io.File;
import java.io.Writer;

public interface TemplateEngine {

    void process(File template, DataModel dataModel, Writer output) throws Exception;
}
