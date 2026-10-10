# MikroJava Compiler

Compiler for MikroJava, a small Java-like language, written in Java. It translates MikroJava source code into bytecode that runs on the MikroJava virtual machine (MJVM).

Course project for Compiler Construction 1 at the School of Electrical Engineering, University of Belgrade (2025/26).

## Phases

| Phase | Implementation |
| --- | --- |
| Lexical analysis | JFlex specification in `spec/mjlexer.flex` |
| Syntax analysis | LALR(1) grammar in `spec/mjparser.cup`, processed by AST-CUP, which also generates the abstract syntax tree classes |
| Semantic analysis | `SemanticPass`, a visitor over the AST that fills the symbol table and checks the context conditions |
| Code generation | `CodeGenerator`, a visitor over the AST that emits MJVM bytecode into an `.obj` file |

All four phases are started from `rs.ac.bg.etf.pp1.Compiler`.

## Supported language

- Global constants, enums, and global and local variables of type `int`, `char` and `bool`
- One-dimensional arrays of built-in types, with `new`, indexing and `length`
- Assignment, `++` and `--`
- Arithmetic expressions with `+ - * / %`, unary minus and parentheses
- Ternary operator with a relational condition, for example `a > b ? a : b`
- `read` and `print`, with an optional print width
- Functions with parameters, including array parameters, and the predeclared `ord`, `chr` and `len`
- `findAny`, a built-in array search added on top of the base language

Not supported: `if`, `for` and `switch` statements, `break`, `continue` and `return`, the logical operators `&&` and `||`, and classes.

### `findAny`

`b = arr.findAny(expr);` sets the `bool` variable `b` to `true` if any element of `arr` equals the value of `expr`, and to `false` otherwise. The array is scanned from the first element to the last.

Adding it touched every phase: a new token in the lexer, a new production in the grammar, semantic checks on the array and the argument type, and a generated loop over the array. In the grammar it is a suffix after the dot, like `.length`, which avoids a shift/reduce conflict with the existing designator rules.

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

- **Lexical errors** report the unrecognized text with its line, and scanning continues.
- **Syntax errors** are logged with the line number. The parser recovers and continues for errors in declarations and in statements.
- **Semantic errors** are reported with the line number and a description. The pass also logs the symbols it finds and prints the symbol table when it finishes.

Code is generated only if the syntax and semantic phases report no errors.

## Building and running

Requirements: JDK 1.8 and Apache Ant.

`ant compile` generates the parser and the syntax tree classes from the grammar and compiles the sources. `ant lexerGen` regenerates the lexer.

The compiler takes the source file as the first argument and the output file as the second. `ant runObj` disassembles and runs `test/program.obj` on the VM from `lib/mj-runtime.jar`.

Test programs are in the `test` folder.

## Tools

JFlex, AST-CUP, the course symbol table library, and `mj-runtime.jar`, which provides the code buffer, the disassembler and the VM.
