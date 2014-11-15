public class InstructionF4 extends Node {
    int n;
    int i;

    int x;
    int b;
    int p;

    int address;
    String symbol;

    public InstructionF4(Mnemonic mnemonic, char addressingMode, int x, int address) {
        super(mnemonic);
        setAddressingMode(addressingMode);
        this.x = x;
        this.address = address;
    }

    public InstructionF4(Mnemonic mnemonic, char addressingMode, int x, String symbol) {
        super(mnemonic);
        setAddressingMode(addressingMode);
        this.x = x;
        this.symbol = symbol;
    }

    private void setAddressingMode(char addressingMode) {
        switch (addressingMode) {
            case '#':
                this.n = 0;
                this.i = 1;
                break;

            case '@':
                this.n = 1;
                this.i = 0;
                break;

            default:
                this.n = 1;
                this.i = 1;
                break;
        }
    }

    public int length() {
        return 4;
    }

    public void resolve(Code code) {
        if (symbol != null) {
            address = code.symbols.get(symbol).value;
            symbol = null;
        }

        // TODO: Check if operand is valid.

        // FIXME
        if (this.n == 1 && this.i == 1 || this.n == 1 && this.i == 0 || this.n == 0 && this.i == 0) {
            if (address >= 0 && address <= 1048575) {
                this.b = 0;
                this.p = 0;
            }
        }
    }

    public void emitCode(int[] code, int codeAddr) {
        code[codeAddr] = mnemonic.opcode | (n << 1) | i;
        code[codeAddr + 1] = (x << 7) | (b << 6) | (p << 5) | (1 << 4) | ((address & 0x0F00) >> 8);
        code[codeAddr + 2] = (address & 0xFF);
    }
}
