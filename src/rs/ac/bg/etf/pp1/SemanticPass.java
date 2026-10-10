package rs.ac.bg.etf.pp1;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticPass extends VisitorAdaptor{
		
		int nVars;
		int printCallCount = 0;
		int varDeclCount = 0;
		int mainCount = 0;
		Obj currentMethod = null;
		Struct currType = null;
		boolean errorDetected = false;
		boolean mainNoArg = true;
		
		
		Struct boolType = new Struct(Struct.Bool);
		
		//Map<Obj, List<Struct>> methodParams = new HashMap<>();
		// novo polje
		Map<Obj, List<Obj>> methodParamObjs = new HashMap<>();
		
		
		
		
		Logger log = Logger.getLogger(getClass());

		public void report_error(String message, SyntaxNode info) {
			errorDetected = true;
			StringBuilder msg = new StringBuilder(message);
			int line = (info == null) ? 0: info.getLine();
			if (line != 0)
				msg.append (" na liniji ").append(line);
			log.error(msg.toString());
		}
		
		public void report_info(String message, SyntaxNode info) {
			StringBuilder msg = new StringBuilder(message); 
			int line = (info == null) ? 0: info.getLine();
			if (line != 0)
				msg.append (" na liniji ").append(line);
			log.info(msg.toString());
		}
		
		public boolean passed(){
	    	return !errorDetected;
	    }

		// IZBACENO: visit(VarDeclListParam) i visit(VarDeclListNoParam) su bili prazne metode – nisu potrebne

		public void visit(VarDecl varDecl) {
		    Struct type = varDecl.getType().struct;
		    boolean isLocal = currentMethod != null;

		    insertVar(varDecl.getVarName(), type,
		              varDecl.getLRBracket() instanceof LRBracketOnce, varDecl, isLocal);

		    VarIdentList list = varDecl.getVarIdentList();
		    while (list instanceof VarIdentListParams) {
		        VarIdentListParams p = (VarIdentListParams) list;
		        insertVar(p.getI2(), type,                       // PROVERI da li je getI1() tacno ime!
		                   p.getLRBracket() instanceof LRBracketOnce, p, isLocal);
		        list = p.getVarIdentList();
		    }
		}
		
		private void insertVar(String name, Struct type, boolean isArray, SyntaxNode node, boolean isLocal) {
		    if (Tab.currentScope().findSymbol(name) != null) {
		        report_error("Promenljiva " + name + " vec deklarisana u ovom scope-u", node);
		        return;
		    }
		    Struct finalType = isArray ? new Struct(Struct.Array, type) : type;
		    Tab.insert(Obj.Var, name, finalType);
		    varDeclCount++;
		    report_info("Deklarisana " + (isLocal ? "lokalna" : "globalna") + " promenljiva " + name, node);
		}
		
	    public void visit(ProgName progName) {
	    	Tab.insert(Obj.Type, "bool", boolType);
	    	progName.obj = Tab.insert(Obj.Prog, progName.getProgName(), Tab.noType);
	    	Tab.openScope();
	    }
	    
	    public void visit(StatementPrintNoNum print) {
	        printCallCount++;
	        if (print.getExpr().struct != Tab.intType && print.getExpr().struct != Tab.charType && print.getExpr().struct != boolType) {
	            report_error("Operand instrukcije PRINT mora biti bool, char ili int tipa", print);
	        }
	    }
	    
	    public void visit(Program program) {
	        
	        // Trazi main DOK je scope jos otvoren
	        Obj mainObj = Tab.find("main");
	        if (mainObj == Tab.noObj) {
	            report_error("Program nema main funkciju!", program);
	        } else if (mainObj.getKind() != Obj.Meth) {
	            report_error("main nije metoda!", program);
	        } else if (mainObj.getType() != Tab.noType) {
	            report_error("main mora biti void!", program);
	        } else if (mainNoArg == false) {
	            report_error("main ne sme imati parametre", program);
	        } else if (mainCount != 1) {
	            report_error("ne sme imati vise od 1 main", program);
	        } else {
	            report_info("Pronadjena main funkcija (void, bez argumenata)", program);
	        }
	        
	        nVars = Tab.currentScope().getnVars(); // NOVO - mora PRE closeScope, dok je global scope jos otvoren
	        
	        // zatvori scope
	        Tab.chainLocalSymbols(program.getProgName().obj);
	        Tab.closeScope();
	    }
	    
	    public void visit(Type type) {
	        Obj typeNode = Tab.find(type.getTypeName());
	        if (typeNode == Tab.noObj) {
	            report_error("Nije pronadjen tip " + type.getTypeName() + " u tabeli simbola", type);
	            type.struct = Tab.noType;
	        } else if (typeNode.getKind() != Obj.Type) {
	            report_error("Ime " + type.getTypeName() + " ne predstavlja tip", type);
	            type.struct = Tab.noType;
	        } else {
	            type.struct = typeNode.getType();
	        }
	        currType = type.struct;
	    }
	    
	    public void visit(MethodTypeDeclVoid type) {
	        type.struct = Tab.noType;
	        currType = Tab.noType;
	        log.info("MethodTypeDeclVoid – currType postavljen na noType (void)");
	    }

	    public void visit(MethodTypeDeclType type) {
	        type.struct = type.getType().struct;
	        currType = type.getType().struct;
	        log.info("MethodTypeDeclType – currType = " + currType);
	    }

	    public void visit(MethodTypeName methodTypeName) {
	        if (currType == null) currType = Tab.noType;

	        String name = methodTypeName.getMethName();
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Funkcija " + name + " vec deklarisana", methodTypeName);
	        }

	        currentMethod = Tab.insert(Obj.Meth, name, currType);
	        methodTypeName.obj = currentMethod;
	        Tab.openScope();
	        if (name.equals("main")) {
	            mainCount++;
	        }
	        report_info("Obradjuje se funkcija " + name + ", povratni tip: " +
	                    (currType == Tab.noType ? "void" : currType.toString()), methodTypeName);
	    }

	    // POPRAVLJENO: tvoj originalni visit(MethodDecl) nije radio –
	    // MethodDecl nije AST cvor koji visitor posecuje, postoje samo
	    // MethodDeclTypeNoFormPars i MethodDeclTypeFormPars
	    
	    public void visit(MethodDeclTypeNoFormPars methodDecl) {
	    	Tab.chainLocalSymbols(currentMethod);
	    	List<Obj> ps = methodParamObjs.get(currentMethod);
	    	currentMethod.setLevel(ps == null ? 0 : ps.size());
	    	Tab.closeScope();
	    	currentMethod = null;
	    }
	    
	    public void visit(MethodDeclTypeFormPars methodDecl) {
	    	if(currentMethod != null && currentMethod.getName().equals("main")) {
	    		mainNoArg = false;
	    	}
	    	Tab.chainLocalSymbols(currentMethod);
	    	List<Obj> ps = methodParamObjs.get(currentMethod);
	    	currentMethod.setLevel(ps == null ? 0 : ps.size());
	    	Tab.closeScope();
	    	currentMethod = null;
	    }
	    
	    // DODATO: formalni parametri
	    // FormPars u gramatici koristi Type IDENT direktno (ne VarDecl)
	    // pa ih moramo rucno upisati u tabelu simbola
	    
	    /*Pre code gena
	    public void visit(FormParsNoBracket formPars) {
	    	String name = formPars.getI2();
	    	Struct type = formPars.getType().struct;
	    	if (Tab.currentScope().findSymbol(name) != null) {
	    		report_error("Parametar " + name + " vec deklarisan", formPars);
	    		return;
	    	}
	    	Tab.insert(Obj.Var, name, type);
	    	methodParams.computeIfAbsent(currentMethod, k -> new ArrayList<>()).add(0, type);
	    	report_info("Deklarisan formalni parametar " + name, formPars);
	    }*/
	    public void visit(FormParsNoBracket formPars) {
	        String name = formPars.getI2();
	        Struct type = formPars.getType().struct;
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Parametar " + name + " vec deklarisan", formPars);
	            return;
	        }
	        Obj o = Tab.insert(Obj.Var, name, type);
	        List<Obj> params = methodParamObjs.computeIfAbsent(currentMethod, k -> new ArrayList<>());
	        params.add(0, o); // NOVO - "a" ide na POCETAK, jer je prvi parametar
	        for (int i = 0; i < params.size(); i++) params.get(i).setAdr(i); // NOVO - ispravlja adr svih
	        report_info("Deklarisan formalni parametar " + name, formPars);
	    }
	    
	    /* Pre code gena
	    public void visit(FormParsBracket formPars) {
	    	String name = formPars.getI2();
	    	Struct type = new Struct(Struct.Array, formPars.getType().struct);
	    	if (Tab.currentScope().findSymbol(name) != null) {
	    		report_error("Parametar " + name + " vec deklarisan", formPars);
	    		return;
	    	}
	    	Tab.insert(Obj.Var, name, type);
	    	methodParams.computeIfAbsent(currentMethod, k -> new ArrayList<>()).add(0, type);
	    	report_info("Deklarisan formalni parametar niza " + name, formPars);
	    }*/
	    public void visit(FormParsBracket formPars) {
	        String name = formPars.getI2();
	        Struct type = new Struct(Struct.Array, formPars.getType().struct);
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Parametar " + name + " vec deklarisan", formPars);
	            return;
	        }
	        Obj o = Tab.insert(Obj.Var, name, type);
	        List<Obj> params = methodParamObjs.computeIfAbsent(currentMethod, k -> new ArrayList<>());
	        params.add(0, o); // NOVO
	        for (int i = 0; i < params.size(); i++) params.get(i).setAdr(i); // NOVO
	        report_info("Deklarisan formalni parametar niza " + name, formPars);
	    }
	    
	    /* Pre code gena
	    public void visit(TypeIdentListParamNoBracket param) {
	    	String name = param.getI3();
	    	Struct type = param.getType().struct;
	    	if (Tab.currentScope().findSymbol(name) != null) {
	    		report_error("Parametar " + name + " vec deklarisan", param);
	    		return;
	    	}
	    	Tab.insert(Obj.Var, name, type);
	    	methodParams.computeIfAbsent(currentMethod, k -> new ArrayList<>()).add(type);
	    	report_info("Deklarisan formalni parametar " + name, param);
	    }*/
	    public void visit(TypeIdentListParamNoBracket param) {
	        String name = param.getI3();
	        Struct type = param.getType().struct;
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Parametar " + name + " vec deklarisan", param);
	            return;
	        }
	        Obj o = Tab.insert(Obj.Var, name, type);
	        methodParamObjs.computeIfAbsent(currentMethod, k -> new ArrayList<>()).add(o); // NOVO - dodaje na KRAJ
	        report_info("Deklarisan formalni parametar " + name, param);
	    }
	    
	    /*Pre code gena
	    public void visit(TypeIdentListParamBracket param) {
	    	String name = param.getI3();
	    	Struct type = new Struct(Struct.Array, param.getType().struct);
	    	if (Tab.currentScope().findSymbol(name) != null) {
	    		report_error("Parametar " + name + " vec deklarisan", param);
	    		return;
	    	}
	    	Tab.insert(Obj.Var, name, type);
	    	methodParams.computeIfAbsent(currentMethod, k -> new ArrayList<>()).add(type);
	    	report_info("Deklarisan formalni parametar niza " + name, param);
	    }*/
	    public void visit(TypeIdentListParamBracket param) {
	        String name = param.getI3();
	        Struct type = new Struct(Struct.Array, param.getType().struct);
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Parametar " + name + " vec deklarisan", param);
	            return;
	        }
	        Obj o = Tab.insert(Obj.Var, name, type);
	        methodParamObjs.computeIfAbsent(currentMethod, k -> new ArrayList<>()).add(o); // NOVO
	        report_info("Deklarisan formalni parametar niza " + name, param);
	    }
	    
	    // DODATO: simbolicke konstante
	    // ConstOptNum/Char/Bool setuju .struct na ConstOpt cvorovima
	    // da ConstDecl zna koji tip vrednosti je napisana
	    
	    public void visit(ConstOptNum constOpt) {
	    	constOpt.struct = Tab.intType;
	    }
	    
	    public void visit(ConstOptChar constOpt) {
	    	constOpt.struct = Tab.charType;
	    }
	    
	    public void visit(ConstOptBool constOpt) {
	    	constOpt.struct = boolType;
	    }
	    
	    public void visit(ConstDecl constDecl) {
	        Struct declared = constDecl.getType().struct;

	        List<ConstIdentListParam> items = new ArrayList<>();
	        ConstIdentList l = constDecl.getConstIdentList();
	        while (l instanceof ConstIdentListParam) {
	            ConstIdentListParam p = (ConstIdentListParam) l;
	            items.add(0, p);
	            l = p.getConstIdentList();
	        }

	        insertCon(constDecl.getI2(), declared, constDecl.getConstOpt(), constDecl);
	        for (ConstIdentListParam p : items)
	            insertCon(p.getI2(), declared, p.getConstOpt(), p);
	    }

	    private void insertCon(String name, Struct declared, ConstOpt opt, SyntaxNode node) {
	        if (opt.struct == null || !opt.struct.assignableTo(declared)) {
	            report_error("Tip vrednosti ne odgovara tipu konstante " + name, node);
	            return;
	        }
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Konstanta " + name + " vec deklarisana", node);
	            return;
	        }
	        Tab.insert(Obj.Con, name, declared).setAdr(extractConstValue(opt));
	        report_info("Deklarisana konstanta " + name, node);
	    }

	    
	    
	    
	    private int extractConstValue(ConstOpt constOpt) {
	    	if (constOpt instanceof ConstOptNum)
	    		return ((ConstOptNum) constOpt).getN1();
	    	if (constOpt instanceof ConstOptChar)
	    		return ((ConstOptChar) constOpt).getC1().charAt(0); // format 'x'
	    	if (constOpt instanceof ConstOptBool)
	    		return ((ConstOptBool) constOpt).getB1().equals("true") ? 1 : 0;
	    	return 0;
	    }
	    
	    // DODATO: enum nabrajanja
	    // Enum konstante su tipa int, upisujemo ih kao Con u tabelu simbola
	    
	    public void visit(EnumDecl enumDecl) {
	        if (Tab.currentScope().findSymbol(enumDecl.getI1()) != null) {
	            report_error("Enum " + enumDecl.getI1() + " vec deklarisan", enumDecl);
	            return;
	        }
	        Tab.insert(Obj.Type, enumDecl.getI1(), Tab.intType);

	        List<EnumIdentListParam> items = new ArrayList<>();
	        EnumIdentList l = enumDecl.getEnumIdentList();
	        while (l instanceof EnumIdentListParam) {
	            EnumIdentListParam p = (EnumIdentListParam) l;
	            items.add(0, p);                       // ubacuj na pocetak = okreni
	            l = p.getEnumIdentList();
	        }

	        int val = (enumDecl.getEqNumConst() instanceof EqNumConstOne)
	                ? ((EqNumConstOne) enumDecl.getEqNumConst()).getN1() : 0;
	        insertEnumCon(enumDecl.getI2(), val, enumDecl);

	        for (EnumIdentListParam p : items) {
	            val = (p.getEqNumConst() instanceof EqNumConstOne)
	                ? ((EqNumConstOne) p.getEqNumConst()).getN1() : val + 1;
	            insertEnumCon(p.getI2(), val, p);
	        }
	    }

	    private void insertEnumCon(String name, int val, SyntaxNode node) {
	        if (Tab.currentScope().findSymbol(name) != null) {
	            report_error("Enum konstanta " + name + " vec deklarisana", node);
	            return;
	        }
	        Tab.insert(Obj.Con, name, Tab.intType).setAdr(val);
	        report_info("Enum konstanta " + name + " = " + val, node);
	    }


	    public void visit(DesignatorIdent designator) {
	        Obj obj = Tab.find(designator.getName());
	        if (obj == Tab.noObj)
	            report_error("Ime " + designator.getName() + " nije deklarisano", designator);
	        else
	            report_info("Pretraga (" + designator.getName() + "), nadjeno " + obj.getName()
	                        + ": " + obj.getType().getKind(), designator);
	        designator.obj = obj;
	    }
	    
		 // a[i] ili a.length – designator sa nastavkom
		 // Trazimo bazni niz u tabeli simbola i setujemo .obj
		 public void visit(DesignatorList designator) {
		     // Oblik Enum.KONSTANTA (npr. Broj.NULA) – LengthIdent je DOT IDENT
		     // Enum konstante su upisane u globalni scope pri EnumDecl kao Obj.Con
		     // pa ih trazimo direktno po imenu clana, ignorisemo ime enum tipa
		     if (designator.getListIdentLength() instanceof LengthIdent) {
		         String memberName = ((LengthIdent) designator.getListIdentLength()).getI1();
		         Obj memberObj = Tab.find(memberName);
		         if (memberObj == Tab.noObj) {
		             report_error("Enum konstanta " + memberName + " nije deklarisana", designator);
		             designator.obj = Tab.noObj;
		         } else {
		             designator.obj = memberObj;
		             report_info("Pronadjena enum konstanta " + memberName, designator);
		         }
		         return;
		     }
		     // Obican slucaj: niz sa indeksom, a.length itd.
		     Obj obj = Tab.find(designator.getName());
		     if (obj == Tab.noObj) {
		         report_error("Ime " + designator.getName() + " nije deklarisano", designator);
		     }
		     designator.obj = obj;
		 }
	
		 // a[i] – tip elementa niza (za rvalue kontekst)
		 // struct je tip elementa, ne tip niza
		  public void visit(LengthExpr node) {
		     if (node.getExpr().struct != Tab.intType) {
		         report_error("Indeks niza mora biti tipa int", node);
		     }
		     node.struct = Tab.noType;
		 }
		 
		public void visit(ListIdentLengthExpr node) {
		    if (node.getExpr().struct != Tab.intType) {
		        report_error("Indeks niza mora biti tipa int", node);
		    }
		    node.struct = Tab.noType;
		}
	
		 // a.length – uvek int
		 public void visit(LengthLength node) {
		     node.struct = Tab.intType;
		 }
	
		 public void visit(ListIdentLengthLength node) {
		     node.struct = Tab.intType;
		 }
	    
	    public void visit(FactorDes factorDes) {
	        Designator d = factorDes.getDesignator();
	        Obj obj = d.obj;
	        if (obj == null || obj == Tab.noObj) {
	            factorDes.struct = Tab.noType;
	            return;
	        }

	        if (obj.getKind() == Obj.Meth) {
	            factorDes.struct = obj.getType();
	            if (!(factorDes.getFactDes() instanceof FactDesParent)) {
	                report_error("Poziv funkcije " + obj.getName() + " mora imati zagrade", factorDes);
	                return;
	            }
	            FactDesParent fp = (FactDesParent) factorDes.getFactDes();
	            List<Struct> argTypes = new ArrayList<Struct>();
	            if (fp.getActParentsOpt() instanceof ActParsYes)
	                argTypes = collectArgTypes(((ActParsYes) fp.getActParentsOpt()).getActPars());
	            report_info("Pronadjen poziv funkcije " + obj.getName(), factorDes);
	            checkCallArgs(obj, argTypes, factorDes);
	            return;
	        }

	        if (obj.getKind() != Obj.Var && obj.getKind() != Obj.Con) {
	            report_error(obj.getName() + " nije promenljiva ni funkcija", factorDes);
	            factorDes.struct = Tab.noType;
	            return;
	        }
	        if (factorDes.getFactDes() instanceof FactDesParent) {
	            report_error(obj.getName() + " nije funkcija, ne moze se pozvati", factorDes);
	            factorDes.struct = Tab.noType;
	            return;
	        }

	        if (d instanceof DesignatorList) {
	            ListIdentLength lil = ((DesignatorList) d).getListIdentLength();
	            if (lil instanceof LengthExpr || lil instanceof ListIdentLengthExpr) {
	                if (obj.getType().getKind() != Struct.Array)
	                    report_error(obj.getName() + " nije niz, ne moze se indeksirati", factorDes);
	            } else if (lil instanceof LengthLength || lil instanceof ListIdentLengthLength) {
	                if (obj.getType().getKind() != Struct.Array)
	                    report_error(obj.getName() + " nije niz, nema polje length", factorDes);
	            } else if (lil instanceof LengthFindAny) {
	                if (obj.getType().getKind() != Struct.Array) {
	                    report_error("findAny se moze pozvati samo nad nizom", factorDes);
	                } else {
	                    Struct arg = ((LengthFindAny) lil).getExpr().struct;
	                    if (arg == null || !arg.assignableTo(obj.getType().getElemType()))
	                        report_error("Argument findAny mora biti tipa elementa niza", factorDes);
	                }
	            }
	        }

	        factorDes.struct = designatorType(d);
	        report_info("Pronadjena promenljiva/konstanta " + obj.getName(), factorDes);
	    }

		 
	    public void visit(ExprA expr) {
	        expr.struct = expr.getExprArithmetic().struct;
	    }

	    

	    public void visit(Term term) {
	        if (term.getMulFactList() instanceof MulFactListParam) {
	            if (term.getFactor().struct != Tab.intType)
	                report_error("Operandi za *, / i % moraju biti tipa int", term);
	            term.struct = Tab.intType;
	        } else {
	            term.struct = term.getFactor().struct;
	        }
	    }

	    public void visit(ExprNoMinusTerm expr) {
	        if (expr.getAddopTermList() instanceof AddopTermListParam) {
	            if (expr.getTerm().struct != Tab.intType)
	                report_error("Operandi za + i - moraju biti tipa int", expr);
	            expr.struct = Tab.intType;
	        } else {
	            expr.struct = expr.getTerm().struct;
	        }
	    }


	    public void visit(FactorNumConst factor) {
	        factor.struct = Tab.intType;
	    }

	    public void visit(FactorCharConst factor) {
	        factor.struct = Tab.charType;
	    }

	    public void visit(FactorBoolConst factor) {
	        factor.struct = boolType;
	    }
	    
	    public void visit(DesignatorStatementAssExpr assign) {
	        Designator d = assign.getDesignator();
	        Obj dstObj = d.obj;
	        Struct srcStruct = assign.getExpr().struct;

	        if (dstObj == null || dstObj == Tab.noObj) {
	            report_error("Levi operand dodele nije deklarisan", assign);
	            return;
	        }
	        if (dstObj.getKind() != Obj.Var) {
	            report_error("Leva strana dodele mora biti promenljiva ili element niza", assign);
	            return;
	        }

	        if (d instanceof DesignatorList) {
	            ListIdentLength lil = ((DesignatorList) d).getListIdentLength();
	            if (lil instanceof LengthLength || lil instanceof ListIdentLengthLength) {
	                report_error("Ne moze se dodeljivati vrednost polju length", assign);
	                return;
	            }
	            if (lil instanceof LengthFindAny) {
	                report_error("Rezultat findAny ne moze biti leva strana dodele", assign);
	                return;
	            }
	            if ((lil instanceof LengthExpr || lil instanceof ListIdentLengthExpr)
	                    && dstObj.getType().getKind() != Struct.Array) {
	                report_error(dstObj.getName() + " nije niz, ne moze se indeksirati", assign);
	                return;
	            }
	        }

	        Struct dstType = designatorType(d);
	        if (srcStruct == null || srcStruct == Tab.noType || dstType == Tab.noType) {
	            report_error("Nepoznat tip u iskazu dodele", assign);
	            return;
	        }
	        if (!srcStruct.assignableTo(dstType)) {
	            report_error("Tipovi se ne poklapaju pri dodeli", assign);
	        } else {
	            report_info("Ispravna dodela", assign);
	        }
	    }

	    
	    public void visit(FactorNewExpr newArray) {
	        Struct exprType = newArray.getExpr().struct;

	        if (exprType != Tab.intType) {
	            report_error("Velicina niza mora biti tipa int", newArray.getExpr());
	            newArray.struct = Tab.noType;
	            return;
	        }

	        Struct elemType = newArray.getType().struct;

	        if (elemType == Tab.noType) {
	            report_error("Tip elementa niza nije definisan", newArray.getType());
	            newArray.struct = Tab.noType;
	            return;
	        }

	        newArray.struct = new Struct(Struct.Array, elemType);
	        log.info("Kreiran niz tipa " + elemType + "[]");
	    }

	    public void visit(FactorNewNoExpr newObj) {
	        Struct type = newObj.getType().struct;
	        if (type.getKind() != Struct.Class) {
	            report_error("new moze kreirati samo objekte klase", newObj);
	            newObj.struct = Tab.noType;
	            return;
	        }
	        newObj.struct = type;
	    }
	    
	    public void visit(FactorExpr factor) {
	        factor.struct = factor.getExpr().struct;
	    }
	    
	    public void visit(CondFactNoRel node) {
	        Struct s = node.getExprArithmetic().struct;
	        if (s == null || !s.equals(boolType)) {
	            report_error("Uslov ternarnog operatora mora biti bool tipa", node);
	        }
	        // CondFact nema .struct polje – ne treba setovati
	    }

	    public void visit(CondFactRel node) {
	        Struct left  = node.getExprArithmetic().struct;
	        Struct right = node.getExprArithmetic1().struct;
	        if (left == null || right == null || !left.compatibleWith(right)) {
	            report_error("Tipovi u relacionom izrazu nisu kompatibilni", node);
	        }
	        // CondFact nema .struct polje
	    }

	    public void visit(ExprCondFact node) {
	        Struct thenType = node.getExpr().struct;
	        Struct elseType = node.getExpr1().struct;
	        if (thenType == null || elseType == null || !thenType.compatibleWith(elseType)) {
	            report_error("Tipovi grana ternarnog operatora nisu kompatibilni", node);
	        }
	        node.struct = thenType;
	    }

	    public void visit(ExprT node) {
	        node.struct = node.getExprTern().struct;
	    }
	    
	    public void visit(LengthFindAny node) {
	        node.struct = boolType;
	    }

	    
	    public void visit(StatementDesigRead node) {
	        Designator d = node.getDesignator();
	        Obj obj = d.obj;
	        if (obj == null || obj == Tab.noObj) return;
	        if (obj.getKind() != Obj.Var) {
	            report_error("read() zahteva promenljivu ili element niza", node);
	            return;
	        }
	        Struct s = designatorType(d);
	        if (s != Tab.intType && s != Tab.charType && s != boolType)
	            report_error("read() zahteva int, char ili bool", node);
	    }


	    public void visit(DesignatorStatementInc node) {
	        checkIntDesignator(node.getDesignator(), node);
	    }

	    public void visit(DesignatorStatementDec node) {
	        checkIntDesignator(node.getDesignator(), node);
	    }

	    private void checkIntDesignator(Designator d, SyntaxNode node) {
	        if (d.obj == null || d.obj == Tab.noObj) {
	            report_error("++/-- : ime nije deklarisano", node);
	            return;
	        }
	        if (d.obj.getKind() != Obj.Var) {
	            report_error("++/-- se moze primeniti samo na promenljivu ili element niza", node);
	            return;
	        }
	        if (designatorType(d) != Tab.intType)
	            report_error("++/-- se moze primeniti samo na int", node);
	    }

	    
	    private Struct designatorType(Designator d) {
	        Obj o = d.obj;
	        if (o == null || o == Tab.noObj) return Tab.noType;
	        if (!(d instanceof DesignatorList)) return o.getType();

	        ListIdentLength lil = ((DesignatorList) d).getListIdentLength();
	        if (lil instanceof LengthExpr || lil instanceof ListIdentLengthExpr) {
	            if (o.getType().getKind() != Struct.Array) return Tab.noType;
	            return o.getType().getElemType();
	        }
	        if (lil instanceof LengthFindAny) return boolType;
	        return Tab.intType;                    // .length ili Enum.KONST
	    }


	    public void visit(DesignatorStatementActPars node) {
	        Obj obj = node.getDesignator().obj;
	        java.util.List<Struct> argTypes = collectArgTypes(node.getActPars());
	        checkCallArgs(obj, argTypes, node);
	    }

	    public void visit(DesignatorStatementNoActPars node) {
	        Obj obj = node.getDesignator().obj;
	        checkCallArgs(obj, new java.util.ArrayList<Struct>(), node);
	    }

	    private java.util.List<Struct> collectArgTypes(ActPars actPars) {
	        java.util.List<Struct> types = new java.util.ArrayList<Struct>();
	        if (actPars == null) return types;
	        types.add(actPars.getExpr().struct);
	        collectExprListTypes(actPars.getExprList(), types);
	        return types;
	    }

	    private void collectExprListTypes(ExprList list, java.util.List<Struct> out) {
	        if (list instanceof ExprListParam) {
	            ExprListParam p = (ExprListParam) list;
	            collectExprListTypes(p.getExprList(), out);
	            out.add(p.getExpr().struct);
	        }
	    }

	    // PAZI: getFpPos() redosled zavisi od redosleda INSERT-a. Zbog bottom-up
	    // obilaska (TypeIdentList grana se upisuje PRE FormPars), redosled MOZE
	    // biti obrnut. Testiraj obavezno sa f(int a, char b) pre predaje!
	    private void checkCallArgs(Obj methodObj, List<Struct> actualTypes, SyntaxNode node) {
	        if (methodObj == null || methodObj == Tab.noObj) return;
	        if (methodObj.getKind() != Obj.Meth) {
	            report_error(methodObj.getName() + " nije funkcija", node);
	            return;
	        }

	        List<Struct> formalTypes;
	        if (methodParamObjs.containsKey(methodObj)) {
	            formalTypes = new ArrayList<Struct>();
	            for (Obj o : methodParamObjs.get(methodObj)) formalTypes.add(o.getType());
	        } else {
	            // predeklarisane metode (chr, ord, len)
	            formalTypes = new ArrayList<Struct>();
	            for (Obj o : methodObj.getLocalSymbols()) {
	                if (o.getKind() == Obj.Var) formalTypes.add(o.getType());
	            }
	        }

	        if (formalTypes.size() != actualTypes.size()) {
	            report_error("Pogresan broj argumenata u pozivu " + methodObj.getName()
	                + " (ocekivano " + formalTypes.size() + ", dobijeno " + actualTypes.size() + ")", node);
	            return;
	        }
	        for (int i = 0; i < formalTypes.size(); i++) {
	            if (actualTypes.get(i) == null || !actualTypes.get(i).assignableTo(formalTypes.get(i))) {
	                report_error("Argument " + (i+1) + " u pozivu " + methodObj.getName() + " ima pogresan tip", node);
	                return;
	            }
	        }
	        report_info("Ispravan poziv funkcije " + methodObj.getName(), node);
	    }

	    
	    public void visit(StatementPrintNum print) {
	        printCallCount++;
	        Struct s = print.getExpr().struct;
	        if (s != Tab.intType && s != Tab.charType && s != boolType) {
	            report_error("Operand instrukcije PRINT mora biti bool, char ili int tipa", print);
	        }
	    }
	    
	    public void visit(AddopTermListParam n) {
	        if (n.getTerm().struct != Tab.intType)
	            report_error("Operandi za + i - moraju biti tipa int", n);
	    }
	    public void visit(MulFactListParam n) {
	        if (n.getFactor().struct != Tab.intType)
	            report_error("Operandi za *, / i % moraju biti tipa int", n);
	    }
	    public void visit(ExprMinusTerm expr) {
	        if (expr.getTerm().struct != Tab.intType)
	            report_error("Operand unarnog minusa mora biti tipa int", expr);
	        expr.struct = Tab.intType;
	    }

}