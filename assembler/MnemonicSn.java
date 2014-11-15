public class MnemonicSn extends Mnemonic {
    public MnemonicSn(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        if (name.equals("RESW")) {
            return new Storage(this, parser.parseNumber(0, Code.MAX_WORD) * 3);
        } else {
            return new Storage(this, parser.parseNumber(0, Code.MAX_WORD));
        }
    }

    public String operandToString(Node instruction) {
        return Integer.toString(((Storage) instruction).number);
    }
}
