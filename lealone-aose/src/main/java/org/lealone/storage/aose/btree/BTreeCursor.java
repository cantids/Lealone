/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0, and the
 * EPL 1.0 (http://h2database.com/html/license.html). Initial Developer: H2
 * Group
 */
package org.lealone.storage.aose.btree;

import org.lealone.storage.IterationParameters;
import org.lealone.storage.StorageMapCursor;

/**
 * A cursor to iterate over elements in ascending order.
 * 
 * @param <K> the key type
 * @param <V> the value type
 * 
 * @author H2 Group
 * @author zhh
 */
class BTreeCursor<K, V> implements StorageMapCursor<K, V> {

    private final BTreeMap<K, ?> map;
    private final IterationParameters<K> parameters;

    private CursorPos pos;
    private K currentKey, lastKey;
    private V currentValue, lastValue;

    BTreeCursor(BTreeMap<K, ?> map, BTreePage root, IterationParameters<K> parameters) {
        this.map = map;
        this.parameters = parameters;

        // 定位到>=from的第一个leaf page
        min(root, parameters.from);
        fetchNext();
    }

    @Override
    public K getKey() {
        return lastKey;
    }

    @Override
    public V getValue() {
        return lastValue;
    }

    @Override
    public boolean hasNext() {
        return currentKey != null;
    }

    @Override
    public K next() {
        lastKey = currentKey;
        lastValue = currentValue;
        fetchNext();
        return lastKey;
    }

    /**
     * Fetch the next entry that is equal or larger than the given key, starting
     * from the given page. This method retains the stack.
     * 
     * @param p the page to start
     * @param from the key to search
     */
    private void min(BTreePage p, K from) {
        while (true) {
            if (p.isLeaf()) {
                int x = from == null ? 0 : p.binarySearch(from);
                if (x < 0) {
                    x = -x - 1;
                }
                pos = new CursorPos(p, x, pos);
                break;
            }
            int x = from == null ? -1 : p.binarySearch(from);
            if (x < 0) {
                x = -x - 1;
            } else {
                x++;
            }
            pos = new CursorPos(p, x + 1, pos);
            p = p.getChildPage(x);
        }
    }

    /**
     * Fetch the next entry if there is one.
     */
    @SuppressWarnings("unchecked")
    private void fetchNext() {
        while (pos != null) {
            if (pos.index < pos.page.getKeyCount()) {
                int index = pos.index++;
                currentKey = (K) pos.page.getKey(index);
                if (parameters.allColumns)
                    currentValue = (V) pos.page.getValue(index, true);
                else
                    currentValue = (V) pos.page.getValue(index, parameters.columnIndexes);
                return;
            }
            pos = pos.parent;
            if (pos == null) {
                break;
            }
            if (pos.index < map.getChildPageCount(pos.page)) {
                min(pos.page.getChildPage(pos.index++), null);
            }
        }
        currentKey = null;
    }

    @Override
    public boolean hasNextBatch() {
        return pos != null;
    }

    @Override
    public V[] nextBatch() {
        @SuppressWarnings("unchecked")
        V[] values = (V[]) pos.page.getValues();
        pos = pos.parent;
        if (pos == null) {
            return values;
        }
        if (pos.index < map.getChildPageCount(pos.page)) {
            min(pos.page.getChildPage(pos.index++), null);
        }
        return values;
    }
}
