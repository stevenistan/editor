package editor;

public class Node<Item> {
    public Node prev;
    public Item item;
    public Node next;

    public Node(Node p, Item i, Node n) {
        prev = p;
        item = i;
        next = n;
    }
}
