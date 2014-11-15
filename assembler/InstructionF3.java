public class InstructionF3 extends Node {
    int n;
    int i;

    int x;
    int b;
    int p;

    int addressOffset;
    String symbol;

    public InstructionF3(Mnemonic mnemonic, char addressingMode, int x, int addressOffset) {
        super(mnemonic);
        setAddressingMode(addressingMode);
        this.x = x;
        this.addressOffset = addressOffset;
    }

    public InstructionF3(Mnemonic mnemonic, char addressingMode, int x, String symbol) {
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
        return 3;
    }

    public void resolve(Code code) {
        Symbol s = null;

        if (symbol != null) {
            s = code.symbols.get(symbol);
            addressOffset = s.value;
        }

        // TODO: Check if operand is valid.

        // FIXME
        if (addressOffset == 0) {
            this.b = 0;
            this.p = 0;
        } else if (this.n == 1 && this.i == 1 || this.n == 1 && this.i == 0 || this.n == 0 && this.i == 0) {
            int finalAddress = addressOffset - code.nextAddr;
            int baseOffset = addressOffset - code.baseAddr;

            if (finalAddress >= -2048 && finalAddress <= 2047) {
                this.b = 0;
                this.p = 1;
                addressOffset = finalAddress;
            } else if (baseOffset >= 0 && baseOffset <= 4095) {
                this.b = 1;
                this.p = 0;
            } else if (addressOffset >= 0 && addressOffset <= 4095) {
                this.b = 0;
                this.p = 0;
            } else {
                // SIC FORMAT, 15 bits
            }
        } else if (this.n == 0 && this.i == 1 && symbol != null) {
            if (!s.isEQU) {
                this.b = 0;
                this.p = 1;

                addressOffset -= code.nextAddr;
            } else {
                addressOffset = s.value;
            }
        }

        symbol = null;
    }

    public void emitCode(int[] code, int codeAddr) {
        code[codeAddr] = mnemonic.opcode | (n << 1) | i;
        code[codeAddr + 1] = (x << 7);

        if (n == 0 && i == 0) {
            code[codeAddr + 1] |= (addressOffset & 0x7F00) >> 8;
        } else {
            code[codeAddr + 1] |= (b << 6) | (p << 5) | ((addressOffset & 0x0F00) >> 8);
        }

        code[codeAddr + 2] = (addressOffset & 0xFF);
    }
}
