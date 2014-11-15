public class MnemonicF2rn extends Mnemonic {
    public MnemonicF2rn(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        int r1 = parser.parseRegister();
        parser.parseComma();
        parser.lexer.advanceIf('#');
        int n = parser.parseNumber(0, Code.MAX_WORD) - 1;

        return new InstructionF2(this, r1, -1, n);
    }

    public String operandToString(Node instruction) {
        InstructionF2 i = (InstructionF2) instruction;
        return i.register1 + ", " + i.number;
    }
}
