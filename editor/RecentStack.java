package editor;

import java.util.LinkedList;

// Stack with capabilities of removing from the bottom
public class RecentStack<Item> {
    private int size;
    private LinkedList<Item> buffer;

    public RecentStack() {
        size = 0;
        buffer = new LinkedList<>();
    }

    public void push(Item item) {
        if (size == 100) {
            buffer.remove(0);
            size -= 1;
        }
        buffer.addLast(item);
        size += 1;
    }

    public Item pop() {
        if (size != 0) {
            size -= 1;
            return buffer.removeLast();
        }
        return null;
    }

    public void clear() {
        buffer.clear();
        size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }
}
