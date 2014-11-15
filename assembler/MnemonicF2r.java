public class MnemonicF2r extends Mnemonic {
    public MnemonicF2r(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        return new InstructionF2(this, parser.parseRegister(), -1, 0);
    }

    public String operandToString(Node instruction) {
        return Integer.toString(((InstructionF2) instruction).register1);
    }
}
