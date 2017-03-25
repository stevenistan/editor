package editor;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.ScrollBar;

public class Editor extends Application {

    private static int WINDOW_WIDTH = 500;
    private static int WINDOW_HEIGHT = 500;
    private static int LEFT_MARGIN = 5;
    private static int RIGHT_MARGIN;
    private Group textRoot;
    private Cursor cursor = new Cursor();
    private ScrollBar scrollBar = new ScrollBar();

    /**
     * An EventHandler to handle mouse clicks.
     */
    private class MouseClickEventHandler implements EventHandler<MouseEvent> {
        /** A Text object that will be used to print the current mouse position. */
        KeyEventHandler keyEventHandler;

        MouseClickEventHandler(KeyEventHandler keyEventHandler) {
            this.keyEventHandler = keyEventHandler;
        }

        @Override
        public void handle(MouseEvent mouseEvent) {
            // Because we registered this EventHandler using setOnMouseClicked, it will only called
            // with mouse events of type MouseEvent.MOUSE_CLICKED.  A mouse clicked event is
            // generated anytime the mouse is pressed and released on the same JavaFX node.
            int mousePressedX = (int) Math.round(mouseEvent.getX());
            int mousePressedY = (int) Math.round(mouseEvent.getY());
            keyEventHandler.mouseSet(mousePressedX, mousePressedY);
        }

    }

    /**
     * An EventHandler to handle keys that get pressed.
     */
    private class KeyEventHandler implements EventHandler<KeyEvent> {
        //The coordinates where the each text should be displayed
        private int cursorX;
        private int cursorY;

        //The Text to display on the screen.
        private Text displayText;
        private int fontSize = 12;
        private ArrayList<Node> lines;
        private TextBuffer<Text> buffer;
        private boolean readingFile = false;

        private String fontName = "Verdana";

        private int characterWidth;
        private int characterHeight;

        private RecentStack<Actions<Text>> undo;
        private RecentStack<Actions<Text>> redo;

        Application.Parameters params = getParameters();
        List<String> args = params.getRaw();
        String inputFilename;

        public KeyEventHandler(final Group root, int windowWidth, int windowHeight) {
            cursorX = LEFT_MARGIN;
            cursorY = 0;
            lines = new ArrayList<>();
            buffer = new TextBuffer<>();
            textRoot = new Group();
            undo = new RecentStack<>();
            redo = new RecentStack<>();

            // Initialize some empty text and add it to root so that it will be displayed.
            // Prevents slow first character typed
            displayText = new Text(cursorX, cursorY, "");
            displayText.setTextOrigin(VPos.TOP);
            displayText.setFont(Font.font(fontName, fontSize));

            characterWidth = (int) Math.round(displayText.getLayoutBounds().getWidth());
            characterHeight = (int) Math.round(displayText.getLayoutBounds().getHeight());

            // All new Nodes need to be added to the root in order to be displayed.
            textRoot.getChildren().add(displayText);
            root.getChildren().add(textRoot);

            if (args.size() < 1) {
                System.out.println("No file name provided");
                System.exit(1);
            }

            try {
                inputFilename = args.get(0);
                File inputFile = new File(inputFilename);

                FileReader reader = new FileReader(inputFile);

                BufferedReader bufferedReader = new BufferedReader(reader);

                int intRead;
                // Keep reading from the file input read() returns -1, which means the end of the file
                // was reached.
                while ((intRead = bufferedReader.read()) != -1) {
                    // The integer read can be cast to a char, because we're assuming ASCII.
                    char charRead = (char) intRead;
                    readingFile = true;
                    String character = Character.toString(charRead);
                    addCharacter(character);
                }

                readingFile = false;
                reposition(buffer);

                // reset the cursor to the beginning of the file
                buffer.cursorNode = buffer.sentinel_front.next;
                updateCursor();

                // Close the reader.
                bufferedReader.close();

            } catch (FileNotFoundException fileNotFoundException) {
                System.out.println("File not found! Exception was: " + fileNotFoundException);
            } catch (IOException ioException) {
                System.out.println("Error when copying; exception was: " + ioException);
            }
        }

        @Override
        public void handle(KeyEvent keyEvent) {
            if (keyEvent.getEventType() == KeyEvent.KEY_TYPED && !keyEvent.isShortcutDown()) {
                // Use the KEY_TYPED event rather than KEY_PRESSED for letter keys, because with
                // the KEY_TYPED event, javafx handles the "Shift" key and associated
                // capitalization.
                String characterTyped = keyEvent.getCharacter();
                if (characterTyped.length() > 0 && characterTyped.charAt(0) != 8) {
                    addCharacter(characterTyped);
                    keyEvent.consume();
                }
            } else if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
                // Arrow keys should be processed using the KEY_PRESSED event, because KEY_PRESSED
                // events have a code that we can check (KEY_TYPED events don't have an associated
                // KeyCode).
                KeyCode code = keyEvent.getCode();
                if (keyEvent.isShortcutDown()) {
                    if (code == KeyCode.PLUS || code == KeyCode.EQUALS) {
                        fontSize += 4;
                        reposition(buffer);
                        updateCursor();

                    } else if (code == KeyCode.MINUS) {
                        fontSize = Math.max(4, fontSize - 4);
                        reposition(buffer);
                        updateCursor();

                    } else if (code == KeyCode.P) {
                        // prints out the top left coordinate of the cursor
                        System.out.println(cursorX + ", " + cursorY);

                    } else if (code == KeyCode.S) {
                        try {
                            FileWriter writer = new FileWriter(inputFilename);
                            Node ptr = buffer.sentinel_front.next;
                            while (ptr != buffer.sentinel_back) {
                                Text text = (Text) ptr.item;
                                writer.write(text.getText().charAt(0));
                                ptr = ptr.next;
                            }
                            writer.close();
                        } catch (IOException ioException) {
                            System.out.println("Error when copying; exception was: " + ioException);
                        }
                    } else if (code == KeyCode.Z) {
                        if (!undo.isEmpty()) {
                            Actions action = undo.pop();

                            // test if the action was "add"
                            if (action.getAction()) {
                                buffer.cursorNode = action.getCursor();
                                textRoot.getChildren().remove(action.getItem());
                                buffer.remove();

                                reposition(buffer);

                                updateCursor();

                                Node cursorPosition = buffer.cursorNode;
                                redo.push(new Actions(action.getItem(), cursorPosition, false));
                            } else {
                                buffer.cursorNode = action.getCursor();
                                textRoot.getChildren().add((Text) action.getItem());
                                buffer.add((Text) action.getItem());

                                reposition(buffer);

                                updateCursor();

                                Node cursorPosition = buffer.cursorNode;
                                redo.push(new Actions(action.getItem(), cursorPosition, true));
                            }
                        }

                    } else if (code == KeyCode.Y) {
                        if (!redo.isEmpty()) {
                            Actions action = redo.pop();

                            // test if the action was "add"
                            if (action.getAction()) {
                                buffer.cursorNode = action.getCursor();
                                textRoot.getChildren().remove(action.getItem());
                                buffer.remove();

                                reposition(buffer);

                                updateCursor();

                                Node cursorPosition = buffer.cursorNode;
                                undo.push(new Actions(action.getItem(), cursorPosition, false));
                            } else {
                                buffer.cursorNode = action.getCursor();
                                textRoot.getChildren().add((Text) action.getItem());
                                buffer.add((Text) action.getItem());

                                reposition(buffer);

                                updateCursor();

                                Node cursorPosition = buffer.cursorNode;
                                undo.push(new Actions(action.getItem(), cursorPosition, true));
                            }
                        }
                    }

                } else {
                    if (code == KeyCode.BACK_SPACE) {
                        if (buffer.cursorNode != buffer.sentinel_front.next) {
                            remove();
                        }

                    } else if (code == KeyCode.LEFT) {
                        if (buffer.cursorNode != buffer.sentinel_front.next) {
                            buffer.cursorNode = buffer.cursorNode.prev;
                            updateCursor();
                        }

                    } else if (code == KeyCode.RIGHT) {
                        if (buffer.cursorNode != buffer.sentinel_back) {
                            buffer.cursorNode = buffer.cursorNode.next;
                            updateCursor();
                        }
                    } else if (code == KeyCode.UP) {
                        if (lines.size() != 0) {
                            int current_line;
                            int targetX;
                            int diff1;
                            int diff2;
                            Node targetNode;
                            Text targetText;

                            // checks for current_line if cursor at sentinel_back
                            if (buffer.cursorNode == buffer.sentinel_back) {
                                targetNode = buffer.cursorNode.prev;
                                targetText = (Text) targetNode.item;
                                current_line = (int) Math.round(targetText.getY() / characterHeight);
                                targetX = (int) Math.round(targetText.getX() + targetText.getLayoutBounds().getWidth());
                            } else {
                                targetText = (Text) buffer.cursorNode.item;
                                current_line = (int) Math.round(targetText.getY() / characterHeight);
                                targetX = (int) Math.round(targetText.getX());
                            }

                            if (current_line != 0) {
                                Node cursor_ptr = lines.get(current_line - 1);

                                Node next_line_ptr = lines.get(current_line);

                                // loop until the next line
                                while (cursor_ptr != next_line_ptr) {
                                    Text currentText = (Text) cursor_ptr.item;
                                    int currentX = (int) Math.round(currentText.getX());

                                    if (currentX > targetX) {
                                        Node previousNode = cursor_ptr.prev;
                                        Text previousText = (Text) previousNode.item;
                                        int previousX = (int) Math.round(previousText.getX());
                                        diff1 = Math.abs(currentX - targetX);
                                        diff2 = Math.abs(previousX - targetX);
                                        if (diff1 > diff2) {
                                            cursor_ptr = cursor_ptr.prev;
                                        }
                                        break;
                                    } else {
                                        cursor_ptr = cursor_ptr.next;
                                    }
                                }

                                // checks for uneven lines
                                if (cursor_ptr != next_line_ptr) {
                                    buffer.cursorNode = cursor_ptr;
                                } else {
                                    buffer.cursorNode = cursor_ptr.prev;
                                }
                            }
                            updateCursor();
                        }

                    } else if (code == KeyCode.DOWN) {
                        if (lines.size() != 0) {
                            int current_line;
                            int targetX;

                            // checks for current_line if cursor at sentinel_back
                            if (buffer.cursorNode == buffer.sentinel_back) {
                                Node targetNode = buffer.cursorNode.prev;
                                Text targetText = (Text) targetNode.item;
                                current_line = (int) Math.round(targetText.getY() / characterHeight);
                                targetX = (int) Math.round(targetText.getX() + targetText.getLayoutBounds().getWidth());
                            } else {
                                Text targetText = (Text) buffer.cursorNode.item;
                                current_line = (int) Math.round(targetText.getY() / characterHeight);
                                targetX = (int) Math.round(targetText.getX());
                            }

                            if (current_line != lines.size() - 1) {
                                Node cursor_ptr = lines.get(current_line + 1);
                                Text currentText;
                                int currentX;

                                Node next_ptr = cursor_ptr.next;
                                Text nextText = (Text) next_ptr.item;
                                int nextX = (int) Math.round(nextText.getX());

                                while (nextX != LEFT_MARGIN) {
                                    if (next_ptr == buffer.sentinel_back) {
                                        currentText = (Text) cursor_ptr.item;
                                        currentX = (int) Math.round(currentText.getX());

                                        cursor_ptr = cursor_ptr.next;
                                        break;
                                    } else {
                                        currentText = (Text) cursor_ptr.item;
                                        currentX = (int) Math.round(currentText.getX());

                                        nextText = (Text) next_ptr.item;
                                        nextX = (int) Math.round(nextText.getX());
                                    }
                                    if (currentX >= targetX) {
                                        Node previousNode = cursor_ptr.prev;
                                        Text previousText = (Text) previousNode.item;
                                        int previousX = (int) Math.round(previousText.getX());
                                        int diff1 = Math.abs(currentX - targetX);
                                        int diff2 = Math.abs(previousX - targetX);
                                        if (diff1 > diff2) {
                                            cursor_ptr = cursor_ptr.prev;
                                        }
                                        break;
                                    } else {
                                        cursor_ptr = cursor_ptr.next;
                                        next_ptr = next_ptr.next;
                                    }
                                }
                                if (nextX != LEFT_MARGIN) {
                                    buffer.cursorNode = cursor_ptr;
                                } else {
                                    buffer.cursorNode = cursor_ptr.prev;
                                }
                            }
                            updateCursor();
                        }
                    }
                }
            }
        }

        // adds characters at the current position of the cursor
        public void addCharacter(String characterTyped) {
            Text text = new Text(cursorX, cursorY, characterTyped);
            text.setTextOrigin(VPos.TOP);
            text.setFont(Font.font(fontName, fontSize));

            textRoot.getChildren().add(text);
            buffer.add(text);

            if (!readingFile) {
                reposition(buffer);
            }

            updateCursor();

            Node cursorPositon = buffer.cursorNode;
            undo.push(new Actions(text, cursorPositon, true));
            redo.clear();
        }

        // deletes characters behind the current position of the cursor
        public void remove() {
            Text previousCharacter = (Text) buffer.cursorNode.prev.item;

            textRoot.getChildren().remove(previousCharacter);
            buffer.remove();

            reposition(buffer);

            updateCursor();

            Node cursorPositon = buffer.cursorNode;
            undo.push(new Actions(previousCharacter, cursorPositon, false));
            redo.clear();
        }

        // repositions all the characters again
        public void reposition(TextBuffer<Text> buffer) {
            int textX = LEFT_MARGIN;
            int textY = 0;
            Node spaceNode = buffer.sentinel_front;

            Node currentNode = buffer.sentinel_front;

            //resets first line pointers every time buffer is repositioned
            lines.clear();

            while (currentNode != buffer.sentinel_back.prev) {
                Node characterNode = currentNode.next;
                Text character = (Text) characterNode.item;
                character.setTextOrigin(VPos.TOP);
                character.setFont(Font.font(fontName, fontSize));
                characterWidth = (int) Math.round(character.getLayoutBounds().getWidth());

                character.setX(textX);
                character.setY(textY);

                // add pointers to each of the first lines
                // STUPID "\r\n" WHEN READING A FILE
                if (character.getX() == LEFT_MARGIN) {
                    //if (!character.getText().equals("\n")) {
                    lines.add(characterNode);
                    //}
                }

                if (character.getText().equals("\r")) {
                    textX = LEFT_MARGIN;
                    textY += characterHeight;
                } else {
                    // store the index of the most recent space character
                    // distinguish last word before word wrap
                    if (character.getText().equals(" ")) {
                        spaceNode = characterNode;
                    }
                    // the newline character has twice the height of normal characters
                    characterHeight = (int) Math.round(character.getLayoutBounds().getHeight());
                }

                // check for word wrapping
                if (textX + characterWidth >= RIGHT_MARGIN) {
                    textX = LEFT_MARGIN;
                    textY += characterHeight;
                    // readjust words after the most recent space (last word)
                    if (spaceNode.item != null) {
                        while (spaceNode != currentNode.next) {
                            Node nextNode = spaceNode.next;
                            Text nextCharacter = (Text) nextNode.item;
                            int nextWidth = (int) Math.round(nextCharacter.getLayoutBounds().getWidth());
                            nextCharacter.setX(textX);
                            nextCharacter.setY(textY);
                            // add pointers to each of the first lines
                            // accounts for word wrapped characters
                            if (nextCharacter.getX() == LEFT_MARGIN) {
                                lines.add(nextNode);
                            }
                            textX += nextWidth;
                            spaceNode = spaceNode.next;
                        }
                        // reset the last spaceNode
                        spaceNode = buffer.sentinel_front;
                    } else {
                        character.setX(textX);
                        character.setY(textY);
                        textX += characterWidth;
                    }
                } else {
                    textX += characterWidth;
                }
                currentNode = currentNode.next;
            }
            scrollBar.setMax(Math.max(0, textY - WINDOW_HEIGHT));
        }

        public void updateCursor() {
            Node currentNode = buffer.cursorNode;
            Text currentCharacter = (Text) currentNode.item;
            int currentX;
            int currentY;

            if (buffer.size() == 0 || currentNode.prev == buffer.sentinel_front) {
                cursorX = LEFT_MARGIN;
                cursorY = 0;

            } else if (currentNode == buffer.sentinel_back || currentCharacter.getText().equals("\r")) {
                Node previousNode = currentNode.prev;
                Text previousCharacter = (Text) previousNode.item;
                currentX = (int) previousCharacter.getX() + (int) Math.round(previousCharacter.getLayoutBounds().getWidth());
                currentY = (int) previousCharacter.getY();
                if (previousCharacter.getText().equals("\r")) {
                    currentX = LEFT_MARGIN;
                    currentY += characterHeight;
                }
                cursorX = currentX;
                cursorY = currentY;

            } else {
                currentX = (int) Math.round(currentCharacter.getX());
                currentY = (int) Math.round(currentCharacter.getY());
                cursorX = currentX;
                cursorY = currentY;
            }

            cursor.returnRectangle().setX(cursorX);
            cursor.returnRectangle().setY(cursorY);
            cursor.returnRectangle().setHeight(characterHeight);

            // snaps the screen back whenever the cursor is off the screen
            // and user begins typing
            if (cursorY < scrollBar.getValue()) {
                scrollBar.setValue(cursorY);
            } else if (cursorY + characterHeight > WINDOW_HEIGHT + scrollBar.getValue()) {
                scrollBar.setValue(cursorY + characterHeight - WINDOW_HEIGHT);
            }
        }

        public void mouseSet(int targetX, int targetY) {
            int current_line = Math.round(targetY / characterHeight);

            // clicking on white space past the text
            if (current_line > lines.size() - 1) {
                buffer.cursorNode = buffer.sentinel_back;
                updateCursor();
            } else {
                Node cursor_ptr = lines.get(current_line);
                Text currentText;
                int currentX;

                Node next_ptr = cursor_ptr.next;
                Text nextText = (Text) next_ptr.item;
                int nextX = (int) Math.round(nextText.getX());

                while (nextX != LEFT_MARGIN) {
                    if (next_ptr == buffer.sentinel_back) {
                        currentText = (Text) cursor_ptr.item;
                        currentX = (int) Math.round(currentText.getX());

                        cursor_ptr = cursor_ptr.next;
                        break;
                    } else {
                        currentText = (Text) cursor_ptr.item;
                        currentX = (int) Math.round(currentText.getX());

                        nextText = (Text) next_ptr.item;
                        nextX = (int) Math.round(nextText.getX());
                    }
                    if (currentX >= targetX) {
                        Node previousNode = cursor_ptr.prev;
                        Text previousText = (Text) previousNode.item;
                        int previousX = (int) Math.round(previousText.getX());
                        int diff1 = Math.abs(currentX - targetX);
                        int diff2 = Math.abs(previousX - targetX);
                        if (diff1 > diff2) {
                            cursor_ptr = cursor_ptr.prev;
                        }
                        break;
                    } else {
                        cursor_ptr = cursor_ptr.next;
                        next_ptr = next_ptr.next;
                    }
                }
                if (nextX != LEFT_MARGIN) {
                    buffer.cursorNode = cursor_ptr;
                } else {
                    buffer.cursorNode = cursor_ptr.prev;
                }
            }
            updateCursor();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Create a Node that will be the parent of all things displayed on the screen.
        Group root = new Group();

        // The Scene represents the window: its height and width will be the height and width
        // of the window displayed.
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT, Color.WHITE);

        // Make a vertical scroll bar on the right side of the screen.
        scrollBar.setOrientation(Orientation.VERTICAL);
        // Set the height of the scroll bar so that it fills the whole window.
        scrollBar.setPrefHeight(WINDOW_HEIGHT);

        // Set the range of the scroll bar.
        // Set the max to the height of the text
        scrollBar.setMin(0);

        // Add the scroll bar to the scene graph, so that it appears on the screen.
        root.getChildren().add(scrollBar);

        int usableScreenWidth =
                (int) Math.round(WINDOW_WIDTH - scrollBar.getLayoutBounds().getWidth());
        scrollBar.setLayoutX(usableScreenWidth);

        // assign the right margin based of the usable screen width
        RIGHT_MARGIN = usableScreenWidth - LEFT_MARGIN;

        /** When the scroll bar changes position, change the height of the text. */
        scrollBar.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(
                    ObservableValue<? extends Number> observableValue,
                    Number oldValue,
                    Number newValue) {
                // newValue describes the value of the new position of the scroll bar. The numerical
                // value of the position is based on the position of the scroll bar, and on the min
                // and max we set above. For example, if the scroll bar is exactly in the middle of
                // the scroll area, the position will be:
                //      scroll minimum + (scroll maximum - scroll minimum) / 2
                textRoot.setLayoutY((int) Math.round(-newValue.doubleValue()));
            }
        });

        // To get information about what keys the user is pressing, create an EventHandler.
        // EventHandler subclasses must override the "handle" function, which will be called
        // by javafx.
        EventHandler<KeyEvent> keyEventHandler =
                new KeyEventHandler(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        // Register the event handler to be called for all KEY_PRESSED and KEY_TYPED events.
        scene.setOnKeyTyped(keyEventHandler);
        scene.setOnKeyPressed(keyEventHandler);

        EventHandler<MouseEvent> mouseEventEventHandler = new MouseClickEventHandler((KeyEventHandler) keyEventHandler);

        scene.setOnMouseClicked(mouseEventEventHandler);

        scene.widthProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                    ObservableValue<? extends Number> observableValue,
                    Number oldScreenWidth,
                    Number newScreenWidth) {
                WINDOW_WIDTH = newScreenWidth.intValue();
                int usableScreenWidth =
                        (int) Math.round(WINDOW_WIDTH - scrollBar.getLayoutBounds().getWidth());
                scrollBar.setLayoutX(usableScreenWidth);

                // assign the right margin based of the usable screen width
                RIGHT_MARGIN = usableScreenWidth - LEFT_MARGIN;
                ((KeyEventHandler) keyEventHandler).reposition(((KeyEventHandler) keyEventHandler).buffer);
                ((KeyEventHandler) keyEventHandler).updateCursor();
            }
        });
        scene.heightProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                    ObservableValue<? extends Number> observableValue,
                    Number oldScreenHeight,
                    Number newScreenHeight) {
                WINDOW_HEIGHT = newScreenHeight.intValue();
                scrollBar.setPrefHeight(WINDOW_HEIGHT);
                ((KeyEventHandler) keyEventHandler).reposition(((KeyEventHandler) keyEventHandler).buffer);
            }
        });

        // Add the cursor to the scene graph, so that it appears on the screen.
        textRoot.getChildren().add(cursor.returnRectangle());
        cursor.makeRectangleColorChange();

        primaryStage.setTitle("Text Editor");

        // This is boilerplate, necessary to setup the window where things are displayed.
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
            launch(args);
        }
}
