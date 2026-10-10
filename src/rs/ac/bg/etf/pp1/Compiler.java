package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;

public class Compiler {

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    public static void main(String[] args) throws Exception {

        Logger log = Logger.getLogger(Compiler.class);

        // Provera argumenata komandne linije
        if (args.length < 2) {
            log.error("Upotreba: Compiler <ulazni .mj fajl> <izlazni .obj fajl>");
            return;
        }

        File sourceFile = new File(args[0]);
        File objFile    = new File(args[1]);

        if (!sourceFile.exists()) {
            log.error("Ulazni fajl ne postoji: " + sourceFile.getAbsolutePath());
            return;
        }

        log.info("Kompajliranje: " + sourceFile.getAbsolutePath());
        log.info("Izlaz:         " + objFile.getAbsolutePath());

        Reader br = null;
        try {
            br = new BufferedReader(new FileReader(sourceFile));
            Yylex lexer = new Yylex(br);

            // Parsiranje
            MJParser p = new MJParser(lexer);
            Symbol s = p.parse();
            
            if (p.errorDetected) {
                log.error("Sintaksna analiza NIJE uspela, prekidam.");
                return;
            }
            
            Program prog = (Program)(s.value);

            Tab.init();

            // Ispis sintaksnog stabla
            log.info(prog.toString(""));
            log.info("===================================");

            // Semanticka analiza
            SemanticPass v = new SemanticPass();
            prog.traverseBottomUp(v);

            log.info(" Print count calls = " + v.printCallCount);
            log.info(" Deklarisanih promenljivih ima = " + v.varDeclCount);
            log.info("===================================");

            // Ispis tabele simbola
            tsdump();

            // Generisanje koda
            if (!p.errorDetected && v.passed()) {
                if (objFile.exists()) objFile.delete();

                CodeGenerator codeGenerator = new CodeGenerator();
                prog.traverseBottomUp(codeGenerator);

                Code.dataSize = v.nVars;
                Code.mainPc   = codeGenerator.getMainPc();
                Code.write(new FileOutputStream(objFile));

                log.info("Kompajliranje uspesno zavrseno!");
                log.info("Obj fajl: " + objFile.getAbsolutePath());
            } else {
                log.error("Kompajliranje nije uspesno zavrseno!");
            }

        } finally {
            if (br != null) {
                try { br.close(); } catch (IOException e) { log.error(e.getMessage(), e); }
            }
        }
    }

    public static void tsdump() {
        Tab.dump();
    }
}