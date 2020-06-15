package aterm.terminal;

public interface TerminalClient {
    public void onDamage(int startRow, int endRow, int startCol, int endCol);

    public void onMoveRect(int destStartRow, int destEndRow, int destStartCol, int destEndCol,
                           int srcStartRow, int srcEndRow, int srcStartCol, int srcEndCol);

    public void onMoveCursor(int posRow, int posCol, int oldPosRow, int oldPosCol, int visible);

    public void onBell();
}
