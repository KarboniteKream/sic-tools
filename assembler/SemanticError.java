public class SemanticError extends Exception {
    public SemanticError(String msg) {
        super(msg + ".");
    }
}
