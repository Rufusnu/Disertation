package MASInterface.Environment;

import java.util.Objects;

public class Coords {
    public int x = -1;
    public int y = -1;

    public Coords(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public String toString() {
        return "(x=" + x + "; y=" + y + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Coords)) {
            return false;
        }
        Coords c = (Coords) o;

        return c.x == this.x && c.y == this.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
