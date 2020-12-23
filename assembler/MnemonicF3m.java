public class MnemonicF3m extends Mnemonic {
    public MnemonicF3m(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        char addressingMode = parser.lexer.advanceIf('#') ? '#' : parser.lexer.advanceIf('@') ? '@' : '\0';

        // TODO: SIC/XE format.
        // TODO: 15/12-bit number.
        if (Character.isDigit(parser.lexer.peek())) {
            int address = parser.parseNumber(0, Code.MAX_WORD);

            return new InstructionF3(this, addressingMode, parser.parseIndexed() ? 1 : 0, address);
        } else if (Character.isLetter(parser.lexer.peek()) || parser.lexer.peek() == '_') {
            String symbol = parser.parseSymbol();

            return new InstructionF3(this, addressingMode, parser.parseIndexed() ? 1 : 0, symbol);
        } else {
            throw new SyntaxError(String.format(
                    "Invalid character '%c'",
                    parser.lexer.peek()), parser.lexer.row, parser.lexer.col);
        }
    }

    public String operandToString(Node instruction) {
        InstructionF3 i = (InstructionF3) instruction;
        return i.symbol != null ? i.symbol : Integer.toString(i.addressOffset);
    }
}
