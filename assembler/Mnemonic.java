public abstract class Mnemonic {
    public String name;
    public int opcode;
    public String hint;
    public String description;

    public Mnemonic(String name, int opcode, String hint, String description) {
        this.name = name;
        this.opcode = opcode;
        this.hint = hint;
        this.description = description;
    }

    public abstract Node parse(Parser parser) throws SyntaxError;

    public String toString() {
        return String.format(" %-6s", name);
    }

    public String operandToString(Node instruction) {
        return "";
    }
}
