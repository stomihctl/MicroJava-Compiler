package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;

public class CounterVisitor extends VisitorAdaptor {
	protected int count;
	
	public int getCount() {
		return count;
	}
	
	// Broji lokalne VarDecl cvorove unutar metode
    public static class VarCounter extends CounterVisitor {
        public void visit(VarDecl varDecl) {
            count++;
        }
    }
	
    // Broji formalne parametre metode.
    // FormPars u gramatici koristi FormParsNoBracket/FormParsBracket za prvi parametar
    // i TypeIdentListParamNoBracket/TypeIdentListParamBracket za svaki sledeci.
    // Nijedno od toga nije vardecl zato originalni FormParamCounter nije radio.
    public static class FormParamCounter extends CounterVisitor {

        // Prvi parametar (nije niz): void foo(int a, ...)
        public void visit(FormParsNoBracket formPars) {
            count++;
        }

        // Prvi parametar (niz): void foo(int a[], ...)
        public void visit(FormParsBracket formPars) {
            count++;
        }

        // Svaki sledeci parametar (nije niz): void foo(int a, int b, ...)
        public void visit(TypeIdentListParamNoBracket param) {
            count++;
        }

        // Svaki sledeci parametar (niz): void foo(int a, int b[], ...)
        public void visit(TypeIdentListParamBracket param) {
            count++;
        }
    }


}
