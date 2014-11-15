public class MnemonicF2n extends Mnemonic {
    public MnemonicF2n(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        parser.lexer.advanceIf('#');
        return new InstructionF2(this, -1, -1, parser.parseNumber(0, Code.MAX_WORD));
    }

    public String operandToString(Node instruction) {
        return Integer.toString(((InstructionF2) instruction).number);
    }
}
