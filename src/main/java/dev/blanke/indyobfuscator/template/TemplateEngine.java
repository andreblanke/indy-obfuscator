package dev.blanke.indyobfuscator.template;

import java.io.Writer;
import java.nio.file.Path;

public interface TemplateEngine {

    void process(Path template, DataModel dataModel, Writer output) throws Exception;
}
