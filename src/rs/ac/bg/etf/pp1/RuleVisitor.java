package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;
import rs.ac.bg.etf.pp1.ast.*;

public class RuleVisitor extends VisitorAdaptor{

	int printCallCount = 0;
	int varDeclCount = 0;
	int programDeclCount = 0;
	
	Logger log = Logger.getLogger(getClass());

    public void visit(Program Program) { 
    	programDeclCount++;
    	log.info("Prepoznata naredba program");
    }

	public void visit(VarDecl vardecl){
		varDeclCount++;
	}
	
	public void visit(StatementPrintNum print) {
		printCallCount++;
	}
	
    public void visit(StatementPrintNoNum print) {
		printCallCount++;
	}

}
