package editor;

// Faster DoubleLinkedListDeque with a cursorNode
public class TextBuffer<Item> {
    public Node sentinel_front;
    public Node sentinel_back;
    public Node cursorNode;
    private int size;

    // Creates an empty TextBuffer
    public TextBuffer() {
        sentinel_front = new Node(null, null, null);
        sentinel_back = new Node(null, null, null);
        sentinel_front.next = sentinel_back;
        sentinel_back.prev = sentinel_front;

        size = 0;
        cursorNode = sentinel_back;
    }

    public int size() {
        return size;
    }

    public void add(Item item) {
        Node newNode = new Node(cursorNode.prev, item, cursorNode);
        cursorNode.prev.next = newNode;
        cursorNode.prev = newNode;
        size += 1;
    }

    public void remove() {
        cursorNode.prev.prev.next = cursorNode;
        cursorNode.prev = cursorNode.prev.prev;
        size -= 1;
    }
}
