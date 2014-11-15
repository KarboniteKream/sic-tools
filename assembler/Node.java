public abstract class Node {
    protected String label;
    protected Mnemonic mnemonic;
    protected String comment;

    public Node(Mnemonic mnemonic) {
        this.mnemonic = mnemonic;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String toString() {
        return mnemonic.toString() + " " + mnemonic.operandToString(this);
    }

    public abstract int length();

    public void activate(Code code) {
        if (label != null) {
            code.defineSymbol(label, code.currentAddr, false);
        }
    }

    public void resolve(Code code) {

    }

    public void enter(Code code) {
        code.nextAddr += length();
    }

    public void leave(Code code) {
        code.currentAddr += length();
    }

    public void emitCode(int[] code, int codeAddr) {
    }
}
