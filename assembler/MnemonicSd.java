public class MnemonicSd extends Mnemonic {
    public MnemonicSd(String mnemonic, int opcode, String hint, String description) {
        super(mnemonic, opcode, hint, description);
    }

    public Node parse(Parser parser) throws SyntaxError {
        return new Storage(this, parser.parseData());
    }

    public String operandToString(Node instruction) {
        Storage i = (Storage) instruction;
        StringBuilder data = new StringBuilder();

        for (int j = 0; j < i.data.length; j++) {
            data.append(String.format("%02X", i.data[j]));
        }

        return data.toString();
    }
}
