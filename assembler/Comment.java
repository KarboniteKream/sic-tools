public class Comment extends Node {
    public Comment(String comment) {
        super(null);
        this.comment = comment;
    }

    public String toString() {
        return comment;
    }

    public int length() {
        return 0;
    }
}
