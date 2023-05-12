package top.hyreon.depotDefrag;

import org.jetbrains.annotations.NotNull;

public class ValuedObject<T> implements Comparable<ValuedObject<T>> {

    T object;
    int value;

    public ValuedObject(T object, int value) {
        this.object = object;
        this.value = value;
    }

    @Override
    public int compareTo(@NotNull ValuedObject o) {
        return value - o.value;
    }
}
