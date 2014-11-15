public class InstructionF2 extends Node {
    int register1;
    int register2;
    int number;

    public InstructionF2(Mnemonic mnemonic, int register1, int register2, int number) {
        super(mnemonic);
        this.register1 = register1;
        this.register2 = register2;
        this.number = number;
    }

    public int length() {
        return 2;
    }

    public void emitCode(int[] code, int codeAddr) {
        code[codeAddr] = mnemonic.opcode;

        if (register1 == -1) {
            code[codeAddr + 1] = (number << 4);
        } else if (register2 == -1) {
            code[codeAddr + 1] = (register1 << 4) | number;
        } else {
            code[codeAddr + 1] = (register1 << 4) | register2;
        }
    }
}
