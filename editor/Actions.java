package editor;

public class Actions<Item> {
    private Item item;
    private Node cursor;
    private boolean add;

    public Actions(Item item, Node cursor, boolean add) {
        this.item = item;
        this.cursor = cursor;
        this.add = add;
    }

    public boolean getAction() {
        return add;
    }

    public Node getCursor() {
        return cursor;
    }

    public Item getItem() {
        return item;
    }
}
