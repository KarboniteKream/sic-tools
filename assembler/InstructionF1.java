public class InstructionF1 extends Node {
    public InstructionF1(Mnemonic mnemonic) {
        super(mnemonic);
    }

    public String toString() {
        return mnemonic.toString();
    }

    public int length() {
        return 1;
    }

    public void emitCode(int[] code, int codeAddr) {
        code[codeAddr] = mnemonic.opcode;
    }
}
