# MikroJava Compiler

Compiler for MikroJava, a small Java-like language, written in Java. It translates MikroJava source code into bytecode that runs on the MikroJava virtual machine (MJVM).

Course project for Compiler Construction 1 at the School of Electrical Engineering, University of Belgrade (2025/26).

## Phases

| Phase | Implementation |
| --- | --- |
| Lexical analysis | JFlex specification in `src/spec/mjlexer.flex` |
| Syntax analysis | LALR(1) grammar in `src/spec/mjparser.cup`, processed by AST-CUP, which also generates the abstract syntax tree classes |
| Semantic analysis | `SemanticAnalyzer`, a visitor over the AST that fills the symbol table and checks the context conditions |
| Code generation | `CodeGenerator`, a visitor over the AST that emits MJVM bytecode into an `.obj` file |

All four phases are started from `rs.ac.bg.etf.pp1.Compiler`.

## Supported language

- A program with a `main` function, global constants, and global and local variables of type `int`, `char` and `bool`
- One-dimensional arrays of built-in types, with `new`, indexing and `length`
- Assignment, `++` and `--`
- Arithmetic expressions with `+ - * / %`, unary minus and parentheses
- Ternary operator with a relational condition, for example `a > b ? a : b`
- `read` and `print`
- `findAny`, a built-in array search added on top of the base language

Not supported: `if`, `for` and `switch` statements, `break`, `continue` and `return`, the logical operators `&&` and `||`, user-defined functions other than `main`, classes and enums.

### `findAny`

`b = arr.findAny(expr);` sets the `bool` variable `b` to `true` if any element of `arr` equals the value of `expr`, and to `false` otherwise. The array is scanned from the first element to the last.

Adding it touched every phase: a new token in the lexer, a new production in the grammar, semantic checks that the left side is a `bool` variable and the right side is a one-dimensional array of a built-in type, and a generated loop over the array.

```
program demo
  const int target = 7;
  int arr[];
  bool found;
{
  void main() {
    arr = new int[3];
    arr[0] = 3; arr[1] = 7; arr[2] = 9;
    found = arr.findAny(target);
    print(found ? 1 : 0);
  }
}
```

## Error handling

- **Lexical errors** report the unrecognized text with its line and column, and scanning continues.
- **Syntax errors** are logged with the line number. The parser recovers and continues for errors in global variable declarations and in assignment statements.
- **Semantic errors** are reported with the line number and a description. The analyzer also logs every use of a constant, global variable or local variable, and prints the symbol table when it finishes.

## Building and running

Requirements: JDK 1.8 and Apache Ant.

`build.xml` generates the lexer and the parser from the specifications and compiles the sources.

The compiler takes the source file as the first argument and the output file as the second. The result runs on the VM from `mj-runtime-1.1.jar`:

```
java -cp "bin;lib/*" rs.ac.bg.etf.pp1.Compiler test/program.mj test/program.obj
java -cp lib/mj-runtime-1.1.jar rs.etf.pp1.mj.runtime.Run test/program.obj
```

On Linux and macOS, use `:` instead of `;` in the classpath.

## Tools

JFlex, AST-CUP, the course symbol table library, and `mj-runtime-1.1.jar`, which provides the code buffer, the disassembler and the VM.
