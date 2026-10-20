package com.gathin.tvhome;

/**
 * The grid's shape at a given column count (#280, from upstream).
 *
 * The dock is the first row and always has one slot per column, filled or not; the apps follow it in
 * reading order, so position {@code i} is in column {@code i % columns} of row {@code i / columns}.
 * Everything here is plain arithmetic on a position: the launcher asks this class where a row starts
 * and ends instead of repeating the arithmetic, and the tests can check it without a View, a
 * PackageManager or a TV.
 */
final class Grid {
    /** The column counts the launcher offers (HomePrefs, res/values/config.xml). */
    static final int MIN_COLUMNS = 4;
    static final int MAX_COLUMNS = 6;

    private Grid() { }

    /** Clamps a saved or requested column count to what the launcher supports. */
    static int columns(int wanted) {
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, wanted));
    }

    /** The dock always has one slot per column, whether or not an app is pinned to it. */
    static int dockSize(int columns) {
        return Math.max(0, columns);
    }

    static boolean inDock(int position, int columns) {
        return position < dockSize(columns);
    }

    /** Whether another app still fits in the dock; a full dock says so instead of taking the tile. */
    static boolean dockHasRoom(int pinned, int columns) {
        return pinned < dockSize(columns);
    }

    /** Position within its row. */
    static int columnOf(int position, int columns) {
        return columns <= 0 ? 0 : position % columns;
    }

    /** Zero-based row of a position; row 0 is the dock. */
    static int rowOf(int position, int columns) {
        return columns <= 0 ? 0 : position / columns;
    }

    /**
     * True on the left edge of a row. Left there has nowhere to go, and letting it fall through would
     * wrap focus to the end of the row (#289).
     */
    static boolean atRowStart(int position, int columns) {
        return columnOf(position, columns) == 0;
    }

    /** True on the right edge of a row, or on the last tile when the last row is short. */
    static boolean atRowEnd(int position, int count, int columns) {
        return columnOf(position, columns) == columns - 1 || position == count - 1;
    }

    /** First position of the last row, which is where Up and Down stop. */
    static int lastRowStart(int count, int columns) {
        return count <= 0 || columns <= 0 ? 0 : ((count - 1) / columns) * columns;
    }

    /** Rows the list needs at this column count, the dock row included. */
    static int rows(int count, int columns) {
        return columns <= 0 || count <= 0 ? 0 : (count + columns - 1) / columns;
    }

    /** Height of the dock's band: one item plus the offsets DockDecoration puts above and below it. */
    static int dockRowHeight(int itemHeightPx, float density) {
        return itemHeightPx + (int) ((6 + 12) * density);
    }

    /**
     * Padding above the grid, so the dock is parked on the bottom edge of the screen with the margin
     * the viewer expects below it. Never negative: a grid too short for the dock keeps no padding.
     */
    static int topPadding(int gridHeightPx, int itemHeightPx, float density) {
        return Math.max(0, gridHeightPx - dockRowHeight(itemHeightPx, density) - (int) (30 * density));
    }
}