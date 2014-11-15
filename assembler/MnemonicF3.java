public class MnemonicF3 extends Mnemonic {
    public MnemonicF3(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        return new InstructionF3(this, '\0', 0, 0);
    }
}
