public class MnemonicF1 extends Mnemonic {
    public MnemonicF1(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        return new InstructionF1(this);
    }
}
