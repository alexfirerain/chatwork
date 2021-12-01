package primitive;

import java.io.Serializable;

public class PMessage implements Serializable  {
    String text;

    public PMessage(String text) {
        this.text = text;
        System.out.printf("[%s] created%n", this);
    }

    public static PMessage hello() {
        return new PMessage("Hello!");
    }

    @Override
    public String toString() {
        return text;
    }
}
