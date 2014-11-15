public class SyntaxError extends Exception {
    int row;
    int col;

    public SyntaxError(String msg, int row, int col) {
        super(msg);
        this.row = row;
        this.col = col;
    }

    public String toString() {
        String head = "Syntax error at " + this.row + ", " + this.col;
        String message = this.getLocalizedMessage();
        return ((message != null) ? (head + ": " + message) : head) + ".";
    }
}
