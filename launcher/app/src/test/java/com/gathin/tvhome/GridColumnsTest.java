package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The grid at 4, 5 and 6 columns (#280, from upstream).
 *
 * The dock is the first row and always has one slot per column; the apps follow. What is checked here
 * is the arithmetic, on a mocked 1080p Android TV screen (1920x1080 at density 2.0, the values the
 * BeyondTV2 reports): how wide a tile is at each column count, that the focus zoom still fits its
 * cell, that the dock keeps its slots, and that the rows the apps need fit the height that is left
 * below the top bar. No View, no PackageManager, no TV: this is what makes it a unit test.
 */
public class GridColumnsTest {

    // A 1080p Android TV: 1920x1080 pixels, 960x540dp, so density 2.0.
    static final float DENSITY = 2.0f;
    static final int SCREEN_W = 1920, SCREEN_H = 1080;
    /** res/layout/activity_main.xml: 56dp either side of the whole column, so 112dp of the 960dp. */
    static final int SIDE_PADDING_DP = 56;
    /** The grid is the second row of a vertical LinearLayout: this is what the top bar leaves it. */
    static final int GRID_HEIGHT = 800;

    static int innerWidth() {
        return SCREEN_W - 2 * Math.round(SIDE_PADDING_DP * DENSITY);
    }

    static int itemHeight(int columns) {
        return AppAdapter.itemHeightPx(
                AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(innerWidth(), columns, DENSITY)), DENSITY);
    }

    // ---- the dock ------------------------------------------------------------------------------

    @Test public void the_dock_has_one_slot_per_column_at_every_column_count() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            assertEquals("columns=" + columns, columns, Grid.dockSize(columns));
            // Row 0 is the dock and holds exactly those positions, filled or not.
            for (int position = 0; position < columns; position++) {
                assertTrue("columns=" + columns + ", position=" + position, Grid.inDock(position, columns));
                assertEquals("columns=" + columns + ", position=" + position, 0, Grid.rowOf(position, columns));
            }
            assertFalse("columns=" + columns, Grid.inDock(columns, columns));
            assertEquals("columns=" + columns, 1, Grid.rowOf(columns, columns));
        }
    }

    /** A full dock says so rather than dropping the tile; the first hidden app frees exactly one slot. */
    @Test public void a_full_dock_takes_no_more_until_an_app_is_hidden() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            // Pinning one app per slot fills the dock.
            for (int pinned = 0; pinned < columns; pinned++) {
                assertTrue("columns=" + columns + ", pinned=" + pinned, Grid.dockHasRoom(pinned, columns));
            }
            assertFalse("columns=" + columns, Grid.dockHasRoom(columns, columns));
            assertFalse("columns=" + columns, Grid.dockHasRoom(columns + 3, columns));
            // Hiding a pinned app drops it from the dock (MainActivity forgets dock entries that are not
            // on screen), which is the one thing that makes room again.
            assertTrue("columns=" + columns, Grid.dockHasRoom(columns - 1, columns));
        }
    }

    // ---- rows ----------------------------------------------------------------------------------

    @Test public void rows_start_and_end_where_the_columns_do() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            for (int count = columns; count <= columns * 3 + 1; count++) {
                for (int position = 0; position < count; position++) {
                    String where = "columns=" + columns + ", count=" + count + ", position=" + position;
                    assertEquals(where, position % columns, Grid.columnOf(position, columns));
                    assertEquals(where, position / columns, Grid.rowOf(position, columns));
                    assertEquals(where, position % columns == 0, Grid.atRowStart(position, columns));
                    boolean end = position % columns == columns - 1 || position == count - 1;
                    assertEquals(where, end, Grid.atRowEnd(position, count, columns));
                }
            }
        }
    }

    @Test public void a_short_last_row_ends_on_its_last_tile_too() {
        // 7 tiles in 4 columns: the last row holds positions 4, 5 and 6. Only the last of them is its
        // right edge, because `position == count - 1` stands in for a row that is not full.
        assertFalse(Grid.atRowEnd(4, 7, 4));
        assertFalse(Grid.atRowEnd(5, 7, 4));
        assertTrue(Grid.atRowEnd(6, 7, 4));
        // A full row needs no such rule: position 3 is both the dock's last slot and its right edge.
        assertTrue(Grid.atRowEnd(3, 8, 4));
        // Up and Down stop at the first position of the last row.
        assertEquals(0, Grid.lastRowStart(4, 4));
        assertEquals(4, Grid.lastRowStart(5, 4));
        assertEquals(4, Grid.lastRowStart(7, 4));
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            for (int count = 1; count <= columns * 2; count++) {
                int start = Grid.lastRowStart(count, columns);
                assertTrue("columns=" + columns + ", count=" + count, start >= 0 && start < count);
                assertEquals("columns=" + columns + ", count=" + count, count - 1,
                        Math.max(start, Math.min(count - 1, start + columns - 1)));
            }
        }
    }

    @Test public void the_number_of_rows_shrinks_as_columns_grow() {
        assertEquals(2, Grid.rows(7, 4));
        assertEquals(2, Grid.rows(7, 5));
        assertEquals(2, Grid.rows(7, 6));
        assertEquals(1, Grid.rows(4, 4));
        assertEquals(6, Grid.rows(24, 4));
        assertEquals(4, Grid.rows(24, 6));
        assertEquals(0, Grid.rows(0, 4));
    }

    // ---- tile geometry on the mocked screen -----------------------------------------------------

    @Test public void four_columns_keep_the_design_tile_and_the_rest_shrink_to_fit() {
        // res/values/config.xml: 180dp at 4 columns, about 144dp at 5 and 117dp at 6 on a 1080p TV.
        assertEquals(360, AppAdapter.tileWidthPx(innerWidth(), 4, DENSITY));   // 180dp
        assertEquals(286, AppAdapter.tileWidthPx(innerWidth(), 5, DENSITY));   // 143dp
        assertEquals(234, AppAdapter.tileWidthPx(innerWidth(), 6, DENSITY));   // 117dp
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            int width = AppAdapter.tileWidthPx(innerWidth(), columns, DENSITY);
            assertTrue("columns=" + columns, width <= Math.round(180 * DENSITY));
            assertTrue("columns=" + columns, width >= Math.round(80 * DENSITY));
        }
    }

    /** The focused tile is scaled by FOCUS_SCALE and still has to stay inside its own cell. */
    @Test public void the_focused_tile_never_overflows_its_cell_at_any_column_count() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            int cell = innerWidth() / columns;
            int width = AppAdapter.tileWidthPx(innerWidth(), columns, DENSITY);
            int focused = (int) (width * AppAdapter.FOCUS_SCALE);
            assertTrue("columns=" + columns + ": " + focused + " in a cell of " + cell, focused <= cell);
            // The tiles plus the focus zoom still fit the width the layout gives them.
            assertTrue("columns=" + columns, columns * focused <= innerWidth());
        }
    }

    /** The tile keeps its 16:9 shape and the item adds the label under it. */
    @Test public void the_item_is_the_tile_plus_its_label() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            int tile = AppAdapter.tileWidthPx(innerWidth(), columns, DENSITY);
            int height = AppAdapter.tileHeightPx(tile);
            assertEquals("columns=" + columns, Math.round(tile * 101f / 180f), height);
            assertEquals("columns=" + columns, height + Math.round(45 * DENSITY),
                    AppAdapter.itemHeightPx(height, DENSITY));
            assertTrue("columns=" + columns, AppAdapter.itemHeightPx(height, DENSITY) > height);
        }
    }

    // ---- the grid does not exceed the mocked screen ----------------------------------------------

    @Test public void the_dock_row_is_parked_at_the_bottom_and_the_rest_has_the_screen_above_it() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            int item = itemHeight(columns);
            int dockRow = Grid.dockRowHeight(item, DENSITY);
            int top = Grid.topPadding(GRID_HEIGHT, item, DENSITY);
            assertTrue("columns=" + columns + ", top=" + top, top >= 0);
            // The padding is exactly what is left of the grid once the dock band and the margin below it.
            assertEquals("columns=" + columns, GRID_HEIGHT - dockRow - Math.round(30 * DENSITY), top);
            assertTrue("columns=" + columns + ", top=" + top + ", dockRow=" + dockRow, top + dockRow <= GRID_HEIGHT);
        }
    }

    @Test public void the_apps_need_rows_that_fit_the_mocked_screen_at_every_column_count() {
        // A TV with a full home screen: the dock plus enough apps to want more than one screen.
        int apps = 24;
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            int item = itemHeight(columns);
            int appRows = Grid.rows(apps, columns);
            int gridRows = Grid.rows(Grid.dockSize(columns) + apps, columns);
            assertEquals("columns=" + columns, appRows + 1, gridRows);
            // What shows on the first screen: the dock pinned at the bottom, the space above it.
            int visible = GRID_HEIGHT - Grid.topPadding(GRID_HEIGHT, item, DENSITY);
            // One row of apps fits above the dock at every column count. That is the whole point of the
            // tile shrinking from 5 columns up: the dock and one row never overflow the screen.
            assertTrue("columns=" + columns + ": " + visible + " for a row of " + item, visible >= item);
            // And not two, so the rest of the apps are reached by scrolling, not by a taller screen.
            assertTrue("columns=" + columns + ": " + visible + " for two rows of " + item, visible < 2 * item);
            // 24 apps never fit on one screen at any column count, so the grid always scrolls.
            assertTrue("columns=" + columns, gridRows * item > GRID_HEIGHT);
        }
    }

    @Test public void a_grid_shorter_than_the_dock_keeps_no_padding() {
        for (int columns = Grid.MIN_COLUMNS; columns <= Grid.MAX_COLUMNS; columns++) {
            int item = itemHeight(columns);
            assertEquals("columns=" + columns, 0, Grid.topPadding(0, item, DENSITY));
            assertEquals("columns=" + columns, 0, Grid.topPadding(Grid.dockRowHeight(item, DENSITY), item, DENSITY));
        }
    }

    // ---- the column count itself -----------------------------------------------------------------

    @Test public void the_column_count_is_clamped_to_what_the_launcher_offers() {
        assertEquals(4, Grid.columns(0));
        assertEquals(4, Grid.columns(3));
        assertEquals(4, Grid.columns(4));
        assertEquals(5, Grid.columns(5));
        assertEquals(6, Grid.columns(6));
        assertEquals(6, Grid.columns(7));
        assertEquals(4, Grid.columns(Integer.MIN_VALUE));
        assertEquals(6, Grid.columns(Integer.MAX_VALUE));
    }
}