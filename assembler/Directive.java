public class Directive extends Node {
    public static final int START = 0x00;
    public static final int END = 0x01;
    public static final int BASE = 0x02;
    public static final int NOBASE = 0x03;
    public static final int EQU = 0x04;
    public static final int ORG = 0x05;
    public static final int LTORG = 0x06;

    int number;
    String symbol;

    public Directive(Mnemonic mnemonic, int number) {
        super(mnemonic);
        this.number = number;
    }

    public Directive(Mnemonic mnemonic, String symbol) {
        super(mnemonic);
        this.symbol = symbol;
    }

    public int length() {
        return 0;
    }

    public void activate(Code code) {
        if (mnemonic.name.equals("EQU") && label != null) {
            code.defineSymbol(label, number, true);
        }
    }

    public void resolve(Code code) {
        if (symbol != null) {
            number = code.symbols.get(symbol).value;
            symbol = null;
        }

        switch (mnemonic.name) {
            case "START":
                code.name = label;
                code.startAddr = number;
                break;

            case "END":
                code.endAddr = number;
                break;

            case "ORG":
                // TODO: Test.
                code.nextAddr = number;
                break;
        }
    }
}
