package rs.ac.bg.etf.pp1;
import java_cup.runtime.Symbol;
%%
%{
	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline+1, yycolumn);
	}

	// ukljucivanje informacije o poziciji tokena
	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline+1, yycolumn, value);
	}
%}
%cup
%line
%column
%xstate COMMENT
%eofval{
	return new_symbol(sym.EOF);
%eofval}
%%
" " 	{ }
"\b" 	{ }
"\t" 	{ }
"\f" 	{ }
"\r\n" 	{ }
"\n" 	{ }
"\r" 	{ }

"program"   { return new_symbol(sym.PROGRAM, yytext()); }
"enum"      { return new_symbol(sym.ENUM, yytext()); }
"const"     { return new_symbol(sym.CONST, yytext()); }
"void"      { return new_symbol(sym.VOID, yytext()); }
"read"      { return new_symbol(sym.READ, yytext()); }
"print"     { return new_symbol(sym.PRINT, yytext()); }
"new"       { return new_symbol(sym.NEW, yytext()); }
"length"    { return new_symbol(sym.LENGTH, yytext()); }
"findAny"   { return new_symbol(sym.FINDANY, yytext()); }

"++"   { return new_symbol(sym.INC, yytext()); }
"--"   { return new_symbol(sym.DEC, yytext()); }
"=="   { return new_symbol(sym.EQ, yytext()); }
"!="   { return new_symbol(sym.NEQ, yytext()); }
">="   { return new_symbol(sym.GE, yytext()); }
"<="   { return new_symbol(sym.LE, yytext()); }
"+"    { return new_symbol(sym.PLUS, yytext()); }
"-"    { return new_symbol(sym.MINUS, yytext()); }
"*"    { return new_symbol(sym.MUL, yytext()); }
"/"    { return new_symbol(sym.DIV, yytext()); }
"%"    { return new_symbol(sym.MOD, yytext()); }
">"    { return new_symbol(sym.GT, yytext()); }
"<"    { return new_symbol(sym.LT, yytext()); }
"="    { return new_symbol(sym.ASSIGN, yytext()); }

";"     { return new_symbol(sym.SEMI, yytext()); }
","     { return new_symbol(sym.COMMA, yytext()); }
"."     { return new_symbol(sym.DOT, yytext()); }
":"     { return new_symbol(sym.COLON, yytext()); }
"?" 	{ return new_symbol(sym.QUESTION, yytext()); }
"("     { return new_symbol(sym.LPAREN, yytext()); }
")"     { return new_symbol(sym.RPAREN, yytext()); }
"{"     { return new_symbol(sym.LBRACE, yytext()); }
"}"     { return new_symbol(sym.RBRACE, yytext()); }
"["     { return new_symbol(sym.LBRACKET, yytext()); }
"]"     { return new_symbol(sym.RBRACKET, yytext()); }

<YYINITIAL> "//" 		     { yybegin(COMMENT); }
<COMMENT> "\r\n" { yybegin(YYINITIAL); }
<COMMENT> "\n"   { yybegin(YYINITIAL); }
<COMMENT> "\r"   { yybegin(YYINITIAL); }
<COMMENT> .      { }

[0-9]+  {
	try {
		return new_symbol(sym.NUM_CONST, Integer.parseInt(yytext()));
	} catch (NumberFormatException e) {
		System.err.println("Leksicka greska (broj '"+yytext()+"' je prevelik) u liniji "+(yyline+1));
	}
}

"true"|"false"  { return new_symbol(sym.BOOL_CONST, yytext()); }

\'([^\'\\]|\\.)\'  { return new_symbol(sym.CHAR_CONST, String.valueOf(yytext().charAt(1))); }

[a-zA-Z][a-zA-Z0-9_]* 	{return new_symbol (sym.IDENT, yytext()); }

. { System.err.println("Leksicka greska ("+yytext()+") u liniji "+(yyline+1)); }
