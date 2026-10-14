package net.bsnx.apps.compass.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class PathListTest {

    private static final float DELTA = 0.001f;

    private static PathItem item(int id, int heading, int steps, int parentId) {
        return new PathItem(null, id, heading, steps, id, Float.NaN, Float.NaN,
                Double.NaN, Double.NaN, Float.NaN, parentId);
    }

    @Test
    public void chainsToPreviousItemWithoutParent() {
        PathList list = new PathList();
        list.add(item(1, 0, 10, PathItem.NO_PARENT));   // 10 m Nord
        list.add(item(2, 90, 5, PathItem.NO_PARENT));   // 5 m Ost
        list.computeCoordinates(1f);

        assertNull(list.get(0).getPrevious());
        assertSame(list.get(0), list.get(1).getPrevious());
        assertEquals(5f, list.get(1).getX(), DELTA);
        assertEquals(10f, list.get(1).getY(), DELTA);
    }

    @Test
    public void continuesFromParentItem() {
        PathList list = new PathList();
        list.add(item(1, 0, 10, PathItem.NO_PARENT));   // (0, 10)
        list.add(item(2, 90, 5, PathItem.NO_PARENT));   // (5, 10)
        list.add(item(3, 270, 4, 1));                   // ab Item 1: (-4, 10)
        list.add(item(4, 180, 2, PathItem.NO_PARENT));  // ab Item 3: (-4, 8)
        list.computeCoordinates(1f);

        PathItem branch = list.get(2);
        assertSame(list.get(0), branch.getPrevious());
        assertEquals(0f, branch.getStartX(), DELTA);
        assertEquals(10f, branch.getStartY(), DELTA);
        assertEquals(-4f, branch.getX(), DELTA);
        assertEquals(10f, branch.getY(), DELTA);

        assertEquals(-4f, list.get(3).getX(), DELTA);
        assertEquals(8f, list.get(3).getY(), DELTA);
    }

    @Test
    public void unknownParentFallsBackToPreviousItem() {
        PathList list = new PathList();
        list.add(item(1, 0, 10, PathItem.NO_PARENT));
        list.add(item(2, 0, 10, 99));                   // Parent gelöscht/anderer Tag
        list.computeCoordinates(0.5f);

        assertSame(list.get(0), list.get(1).getPrevious());
        assertEquals(10f, list.get(1).getY(), DELTA);
    }
}
