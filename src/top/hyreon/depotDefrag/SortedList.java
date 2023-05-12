package top.hyreon.depotDefrag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SortedList<T extends Comparable<T>> extends ArrayList<T> {

    @Override
    public boolean add(T e) {
        int index = Collections.binarySearch(this, e);
        if (index < 0) index = ~index;
        super.add(index, e);
        return true;
    }

}
