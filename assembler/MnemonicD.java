public class MnemonicD extends Mnemonic {
    public MnemonicD(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        return new Directive(this, 0);
    }
}
