package rs.ac.bg.etf.pp1;

import java.util.ArrayDeque;
import java.util.Deque;

import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

    private int mainPc;
    private Deque<Integer> ternFalseStack = new ArrayDeque<>();
    private Deque<Integer> ternJumpStack  = new ArrayDeque<>();
    
    private int faArr, faTarget, faLen, faIdx, faFound;

    public int getMainPc() {
        return mainPc;
    }

    // ================================================================
    // METODE – enter i exit instrukcije
    // ================================================================

    // Posecuje se kad nadjemo ime metode (pre tela).
    // Ovde generisemo enter instrukciju koja rezervise prostor na steku
    // za formalne parametre i lokalne promenljive.
    public void visit(MethodTypeName methodTypeName) {
        if ("main".equalsIgnoreCase(methodTypeName.getMethName())) {
            mainPc = Code.pc;
        }
        methodTypeName.obj.setAdr(Code.pc);

        SyntaxNode methodNode = methodTypeName.getParent();

        VarCounter varCnt = new VarCounter();
        methodNode.traverseTopDown(varCnt);

        FormParamCounter fpCounter = new FormParamCounter();
        methodNode.traverseTopDown(fpCounter);
        int fpCnt = fpCounter.getCount();

        // NOVO: broji koliko puta se findAny koristi u ovoj metodi
        int faCount = countFindAny(methodNode);
        int baseSlot = fpCnt + varCnt.getCount();
        if (faCount > 0) {
            faArr = baseSlot; faTarget = baseSlot+1; faLen = baseSlot+2;
            faIdx = baseSlot+3; faFound = baseSlot+4;
        }

        Code.put(Code.enter);
        Code.put(fpCnt);
        Code.put(baseSlot + (faCount > 0 ? 5 : 0)); // NOVO: +5 slotova ako ima findAny
    }
    
    // NOVO: prost brojac cvorova tipa LengthFindAny u podstablu
    private int countFindAny(SyntaxNode node) {
        final int[] cnt = {0};
        node.traverseBottomUp(new VisitorAdaptor() {
            public void visit(LengthFindAny n) { cnt[0]++; }
        });
        return cnt[0];
    }
    
    public void visit(LengthFindAny node) {
        // 1. Expr (tražena vrednost) je već na steku -> upisujemo je u lokalni slot faTarget
        Code.put(Code.store);
        Code.put(faTarget);

        // 2. Nalazimo niz i čuvamo ga u lokalni slot faArr
        DesignatorList dl = (DesignatorList) node.getParent();
        Code.load(dl.obj); // Ako izbacuje grešku na dl.obj, probaj dl.getDesignator().obj
        Code.put(Code.store);
        Code.put(faArr);

        // 3. Uzimamo dužinu niza (arraylength) i čuvamo u faLen
        Code.put(Code.load);
        Code.put(faArr);
        Code.put(Code.arraylength);
        Code.put(Code.store);
        Code.put(faLen);

        // 4. Inicijalizujemo idx = 0 i found = 0
        Code.loadConst(0);
        Code.put(Code.store);
        Code.put(faIdx);

        Code.loadConst(0);
        Code.put(Code.store);
        Code.put(faFound);

        // ====== POCETAK PETLJE ======
        int loopStart = Code.pc;

        // Provera: faIdx < faLen
        Code.put(Code.load);
        Code.put(faIdx);
        Code.put(Code.load);
        Code.put(faLen);
        Code.putFalseJump(Code.lt, 0);      // ako NIJE idx < len -> izađi iz petlje
        int exitPatch = Code.pc - 2;

        // Učitavanje elementa niza: arr[idx]
        Code.put(Code.load);
        Code.put(faArr);
        Code.put(Code.load);
        Code.put(faIdx);
        Code.put(Code.aload);               

        // Da li je arr[idx] == target?
        Code.put(Code.load);
        Code.put(faTarget);
        Code.putFalseJump(Code.eq, 0);      // ako NISU jednaki -> preskoči setovanje na 1
        int notEqPatch = Code.pc - 2;

        // PRONADJENO: found = 1 i break iz petlje
        Code.loadConst(1);
        Code.put(Code.store);
        Code.put(faFound);
        Code.putJump(0);                    // Izlazimo odmah iz petlje (break)
        int breakPatch = Code.pc - 2;

        // NIJE PRONADJENO: idx++ i nastavi petlju
        Code.fixup(notEqPatch);
        Code.put(Code.load);
        Code.put(faIdx);
        Code.loadConst(1);
        Code.put(Code.add);
        Code.put(Code.store);
        Code.put(faIdx);
        Code.putJump(loopStart);            // Skok na početak petlje

        // ====== KRAJ PETLJE ======
        Code.fixup(exitPatch);
        Code.fixup(breakPatch);

        // Stavljamo rezultat na stek (1 za true, 0 za false)
        Code.put(Code.load);
        Code.put(faFound);
    }
    
    

    // Kraj metode bez formalnih parametara – generisemo exit + return
    public void visit(MethodDeclTypeNoFormPars methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    // Kraj metode sa formalnim parametrima – isti kraj
    public void visit(MethodDeclTypeFormPars methodDecl) {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    // ================================================================
    // KONSTANTE I LITERALI – guraju vrednost na stek
    // ================================================================

    // Numericka konstanta: 5, 42 - const na stek
    public void visit(FactorNumConst numcnst) {
        Obj con = Tab.insert(Obj.Con, "$", numcnst.struct);
        con.setLevel(0);
        con.setAdr(numcnst.getN1());
        Code.load(con);
    }

    // Znakovni literal: 'a' - ASCII vrednost na stek
    public void visit(FactorCharConst cnst) {
        Obj con = Tab.insert(Obj.Con, "$", cnst.struct);
        con.setLevel(0);
        con.setAdr(cnst.getC1().charAt(0)); // format je 'x', karakter na poziciji 1
        Code.load(con);
    }

    // Bool literal: true=1, false=0 - na stek
    public void visit(FactorBoolConst cnst) {
        Obj con = Tab.insert(Obj.Con, "$", cnst.struct);
        con.setLevel(0);
        con.setAdr(cnst.getB1().equals("true") ? 1 : 0);
        Code.load(con);
    }

    // ================================================================
    // DESIGNATOR – ucitavanje vrednosti promenljive/elementa niza
    // ================================================================

    // Prost designator: x, pi, lokalna
    // Code.load automatski generise getstatic/load/const u zavisnosti od vrste objekta
    // NAPOMENA: za DesignatorList (a[i], a.length) load se ne poziva ovde
    // vec u visit(LengthExpr) i visit(LengthLength) – videti dole
    /*ZAMENA POSLEDNJA	
     * public void visit(FactorDes factor) {
        Obj obj = factor.getDesignator().obj;

        if (factor.getDesignator() instanceof DesignatorList) {
            DesignatorList dl = (DesignatorList) factor.getDesignator();
            if (dl.getListIdentLength() instanceof LengthExpr
                || dl.getListIdentLength() instanceof ListIdentLengthExpr) {
                // a[i] kao rvalue: DesignatorList ostavio [ref, idx] na steku
                // aload uzima ref i idx i stavlja element na stek
                Code.put(Code.aload);
            }
            // a.length: DesignatorList vec generisao arraylength
            return;
        }

        Code.load(obj);
    }*/
    public void visit(FactorDes factor) {
        Obj obj = factor.getDesignator().obj;

        if (obj != null && obj.getKind() == Obj.Meth) {
            String name = obj.getName();
            if (name.equals("ord") || name.equals("chr")) {
                // char i int dele isti zapis u MJVM - ord/chr su no-op,
                // argument je vec na steku i to JESTE rezultat
                return;
            }
            if (name.equals("len")) {
                // len(arr) = arraylength nad nizom koji je vec na steku
                Code.put(Code.arraylength);
                return;
            }
            // korisnicki definisana funkcija - ovo ostaje kao pre
            int offset = obj.getAdr() - Code.pc;
            Code.put(Code.call);
            Code.put2(offset);
            return;
        }

        if (factor.getDesignator() instanceof DesignatorList) {
            DesignatorList dl = (DesignatorList) factor.getDesignator();
            if (dl.getListIdentLength() instanceof LengthExpr
                || dl.getListIdentLength() instanceof ListIdentLengthExpr) {
                Code.put(Code.aload);
            }
            return;
        }

        Code.load(obj);
    }


    // ================================================================
    // PRISTUP NIZU – a[i] i a.length
    // ================================================================

    public void visit(LengthExpr node) {
        // Indeks je na steku – ne radimo nista, DesignatorList ce srediti redosled
    }

    public void visit(DesignatorList designator) {
        Obj obj = designator.obj;
        if (obj == null || obj == Tab.noObj) return;
        
        if (designator.getListIdentLength() instanceof LengthFindAny) {
            return; // rezultat je vec na steku (setovan u visit(LengthFindAny))
        }

        if (designator.getListIdentLength() instanceof LengthIdent) {
            // Enum.KONSTANTA – obj je Obj.Con, samo ucitavamo vrednost na stek
            Code.load(obj);
        } else if (designator.getListIdentLength() instanceof LengthLength
            || designator.getListIdentLength() instanceof ListIdentLengthLength) {
            // a.length: ucitaj ref niza, pa arraylength
            Code.load(obj);
            Code.put(Code.arraylength);
        } else {
            // a[i]: stek ima [..., idx]
            // ucitamo ref, pa dup_x1, pa pop da dobijemo [ref, idx]
            Code.load(obj);        // [..., idx, ref]
            Code.put(Code.dup_x1); // [..., ref, idx, ref]
            Code.put(Code.pop);    // [..., ref, idx]
        }
    }

    public void visit(LengthLength node) { }
    public void visit(ListIdentLengthExpr node) { }
    public void visit(ListIdentLengthLength node) { }

    // ================================================================
    // ARITMETIKA
    // ================================================================

    // Term op Term – Addop se posecuje POSLE oba operanda (bottom-up)
    // pa su oba vec na steku kada generisemo add/sub
    public void visit(AddopTermListParam node) {
        if (node.getAddop() instanceof AddopPlus)
            Code.put(Code.add);
        else
            Code.put(Code.sub);
    }

    // Factor op Factor – isto, oba operanda su vec na steku
    public void visit(MulFactListParam node) {
        if (node.getMulop() instanceof MulopMul)
            Code.put(Code.mul);
        else if (node.getMulop() instanceof MulopDiv)
            Code.put(Code.div);
        else
            Code.put(Code.rem);
    }

    // NegHelper se posecuje odmah posle MINUS a pre Term-a
    // -> neg se emituje pre Term-a pa negira samo prvi term
    public void visit(NegHelper node) {
        Code.put(Code.neg);
    }

    // Unarni minus: NegHelper je vec stavio 0 na stek, Term je stavila vrednost
    // 0 - Term = -Term, a zatim AddopTermList nastavlja normalno
    public void visit(ExprMinusTerm expr) {
    }

    // ================================================================
    // ISKAZI
    // ================================================================

    // x = Expr; – Expr je vec na steku, store uzima vrednost sa steka i pise
    // Za obican Var: putstatic ili store
    // Za element niza a[i]: astore (ref i indeks su ispod vrednosti na steku)
    // Code.store automatski bira pravu instrukciju na osnovu vrste objekta
    public void visit(DesignatorStatementAssExpr assignment) {
        Obj obj = assignment.getDesignator().obj;

        if (assignment.getDesignator() instanceof DesignatorList) {
            // Stek: [... , ref_niza, indeks, vrednost]
            // astore uzima sva tri
            Code.put(Code.astore);
        } else {
            Code.store(obj);
        }
    }

    // x++ - load x, const 1, add, store x
    public void visit(DesignatorStatementInc stmt) {
        Code.load(stmt.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.add);
        Code.store(stmt.getDesignator().obj);
    }

    // x-- - load x, const 1, sub, store x
    public void visit(DesignatorStatementDec stmt) {
        Code.load(stmt.getDesignator().obj);
        Code.loadConst(1);
        Code.put(Code.sub);
        Code.store(stmt.getDesignator().obj);
    }

    // print(Expr); – Expr je vec na steku
    // print  = za int (sirina 5)
    // bprint = za char (sirina 1)
    public void visit(StatementPrintNoNum printStmt) {
        if (printStmt.getExpr().struct == Tab.charType) {
            Code.loadConst(1);
            Code.put(Code.bprint);
        } else {
            Code.loadConst(5);
            Code.put(Code.print);
        }
    }

    // print(Expr, width); – isto ali sa eksplicitnom sirinom
    public void visit(StatementPrintNum printStmt) {
        Code.loadConst(printStmt.getNumConst());
        if (printStmt.getExpr().struct == Tab.charType)
            Code.put(Code.bprint);
        else
            Code.put(Code.print);
    }

    // read(Designator); – cita sa ulaza i smesta u promenljivu
    // read  = za int
    // bread = za char i bool
    public void visit(StatementDesigRead stmt) {
        Obj obj = stmt.getDesignator().obj;
        Struct type = obj.getType();

        if (type == Tab.charType) {
            Code.put(Code.bread);
        } else {
            // int i bool
            Code.put(Code.read);
        }

        Code.store(obj);
    }

    // ================================================================
    // POZIV FUNKCIJA
    // ================================================================

    // foo(); – poziv bez argumenata
    public void visit(DesignatorStatementNoActPars stmt) {
        Obj functionObj = stmt.getDesignator().obj;
        int offset = functionObj.getAdr() - Code.pc;
        Code.put(Code.call);
        Code.put2(offset);
        // Ako funkcija vraca vrednost a je ne koristimo, ukloni sa steka
        if (functionObj.getType() != Tab.noType) {
            Code.put(Code.pop);
        }
    }

    // fun(arg1, arg2); – poziv sa argumentima
    // generisalo argumente DRUGI PUT (prvi put ih je vec generisao bottom-up obilazak)
    // Sada samo generisemo call instrukciju – argumenti su vec na steku
    public void visit(DesignatorStatementActPars stmt) {
        Obj methodObj = stmt.getDesignator().obj;
        int offset = methodObj.getAdr() - Code.pc;
        Code.put(Code.call);
        Code.put2(offset);
        if (methodObj.getType() != Tab.noType) {
            Code.put(Code.pop);
        }
    }

    // ================================================================
    // NEW – kreiranje niza
    // ================================================================

    // new int[n] ili new char[n] – n je vec na steku (Expr ga je generisao)
    // newarray 0 = niz char-ova (1 bajt po elementu)
    // newarray 1 = niz int-ova  (4 bajta po elementu)
    public void visit(FactorNewExpr node) {
        if (node.getType().struct == Tab.charType) {
            Code.put(Code.newarray);
            Code.put(0);
        } else {
            Code.put(Code.newarray);
            Code.put(1);
        }
    }

    // ================================================================
    // TERNARNI OPERATOR – cond ? thenExpr : elseExpr
    // ================================================================

    private int ternFalsePc = 0;  // adresa false-jump-a koji preskace then
    private int ternJumpPc  = 0;  // adresa jmp-a koji preskace else

    // Posecuje se posle CondFact a pre then grane (QUESTION token)
    // Uslov (bool) je na steku – generisemo false-jump ako je false (0)
    public void visit(TernHelper node) {
        Code.loadConst(0);
        Code.putFalseJump(Code.ne, 0);
        ternFalseStack.push(Code.pc - 2);
    }

    // Posecuje se posle then grane a pre else grane (COLON token)
    // Then vrednost je na steku – generisemo jmp preskoci else,
    // i patchujemo false-jump da pokazuje ovde (pocetak else)
    public void visit(TernHelper2 node) {
        Code.putJump(0);
        ternJumpStack.push(Code.pc - 2);
        Code.fixup(ternFalseStack.pop());
    }

    // Posecuje se posle else grane
    // Patchujemo jmp da pokazuje ovde (iza else) – na steku je tacna vrednost
    public void visit(ExprCondFact node) {
        Code.fixup(ternJumpStack.pop());
        node.struct = node.getExpr().struct;
    }

    public void visit(CondFactRel node) {
        int op = Code.eq;
        Relop relop = node.getRelop();

        if (relop instanceof RelopEq) op = Code.eq;
        else if (relop instanceof RelopNeq) op = Code.ne;
        else if (relop instanceof RelopGt) op = Code.gt;
        else if (relop instanceof RelopGe) op = Code.ge;
        else if (relop instanceof RelopLt) op = Code.lt;
        else if (relop instanceof RelopLe) op = Code.le;

        // Stek ima: [val1, val2]
        // Generisemo false-jump ako relacija NIJE ispunjena
        Code.putFalseJump(op, 0);
        int falseJumpAdr = Code.pc - 2;

        // Ako JESTE ispunjena relacija: stavljamo 1 na stek
        Code.loadConst(1);
        Code.putJump(0);
        int endJumpAdr = Code.pc - 2;

        // Ako NIJE ispunjena relacija: stavljamo 0 na stek
        Code.fixup(falseJumpAdr);
        Code.loadConst(0);

        // Kraj evaluacije relacije (na steku ostaje tacno JEDNA vrednost: 1 ili 0)
        Code.fixup(endJumpAdr);
    }

}