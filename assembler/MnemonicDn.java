public class MnemonicDn extends Mnemonic {
    public MnemonicDn(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        if (Character.isDigit(parser.lexer.peek())) {
            return new Directive(this, parser.parseNumber(0, Code.MAX_ADDR));
        } else if (Character.isLetter(parser.lexer.peek()) || parser.lexer.peek() == '_') {
            return new Directive(this, parser.parseSymbol());
        } else {
            throw new SyntaxError(String.format("Invalid character '%c'", parser.lexer.peek()),
                                  parser.lexer.row, parser.lexer.col);
        }
    }

    public String operandToString(Node instruction) {
        Directive i = (Directive) instruction;
        return i.symbol != null ? i.symbol : Integer.toString(i.number);
    }
}
