public class MnemonicF2rr extends Mnemonic {
    public MnemonicF2rr(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        int r1 = parser.parseRegister();
        parser.parseComma();
        int r2 = parser.parseRegister();

        return new InstructionF2(this, r1, r2, 0);
    }

    public String operandToString(Node instruction) {
        InstructionF2 i = (InstructionF2) instruction;
        return i.register1 + ", " + i.register2;
    }
}
