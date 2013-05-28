/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2005
 Adrian Bowyer & The University of Bath
 
 http://reprap.org
 
 Principal author:
 
 Adrian Bowyer
 Department of Mechanical Engineering
 Faculty of Engineering and Design
 University of Bath
 Bath BA2 7AY
 U.K.
 
 e-mail: A.Bowyer@bath.ac.uk
 
 RepRap is free; you can redistribute it and/or
 modify it under the terms of the GNU Library General Public
 Licence as published by the Free Software Foundation; either
 version 2 of the Licence, or (at your option) any later version.
 
 RepRap is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General Public Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General Public Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General Public
 Licence along with RepRap; if not, write to the Free
 Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
 or see
 
 http://www.gnu.org/
 
 =====================================================================
 
 
 SimulationPlotter: Simple 2D graphics
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

/*
 * Changes after 2013-04-13 are Copyright (C) 2013  Holger Oehm
 * also licensed under LGPL.
 */
package org.reprap.geometry;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.geometry.grids.BooleanGrid;
import org.reprap.geometry.grids.BooleanGridList;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;

/**
 * Class to plot images of geometrical structures for debugging.
 * 
 * @author ensab
 */
public class SimulationPlotter extends JComponent {
    private static final Color BOX_COLOR = Color.blue;

    private static SimulationPlotter instance;
    private final Map<String, Color> colorMap = new HashMap<String, Color>();
    private BooleanGridList grids = null;
    private PolygonList polygons = null;

    private double scale;
    private Point2D origin;
    private Point2D pos;
    private Rectangle scaledBox, originalBox;
    private boolean plotBoxes = false;
    private boolean initialised = false;
    private boolean pauseSlicer;

    /**
     * For debugging purposes, to allow display of PolygonLists and
     * BooleanGridLists from anywhere.
     */
    public static SimulationPlotter getInstance() {
        return instance;
    }

    /**
     * Constructor for nothing - add stuff later
     */
    public SimulationPlotter(final CurrentConfiguration configuration) {
        polygons = null;
        initialised = false;
        for (final MaterialSetting material : configuration.getMaterials()) {
            colorMap.put(material.getName(), material.getColor().get());
        }
        instance = this;
    }

    public void cleanPolygons() {
        polygons = null;
        grids = null;
    }

    private void setScales(final Rectangle b) {
        scaledBox = b.scale(1.2);

        final double width = scaledBox.x().length();
        final double height = scaledBox.y().length();
        final double xs = getWidth() / width;
        final double ys = getHeight() / height;

        if (xs < ys) {
            scale = xs;
        } else {
            scale = ys;
        }

        origin = new Point2D((getWidth() - (width + 2 * scaledBox.x().low()) * scale) * 0.5,
                (getHeight() - (height + 2 * scaledBox.y().low()) * scale) * 0.5);

        pos = new Point2D(width * 0.5, height * 0.5);
    }

    private void plotBar(final Graphics2D g2d) {
        g2d.setColor(BOX_COLOR);
        Point2D p = new Point2D(scaledBox.ne().x() - 12, scaledBox.sw().y() + 5);
        move(p);
        p = new Point2D(scaledBox.ne().x() - 2, scaledBox.sw().y() + 5);
        plot(g2d, p);
    }

    public void init(final Rectangle b) {
        originalBox = b;
        setScales(b);
        initialised = true;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public void add(final BooleanGridList gridList) {
        if (grids == null) {
            grids = new BooleanGridList();
        }
        for (final BooleanGrid grid : gridList) {
            grids.add(grid);
        }
        repaint();
    }

    public void add(final BooleanGrid grid) {
        if (grids == null) {
            grids = new BooleanGridList();
        }
        grids.add(grid);
        repaint();
    }

    public void add(final PolygonList pl) {
        if (pl == null) {
            return;
        }
        if (pl.size() <= 0) {
            return;
        }
        if (polygons == null) {
            polygons = new PolygonList(pl);
        } else {
            polygons.add(pl);
        }
        repaint();
    }

    public void add(final Polygon polygon) {
        if (polygons == null) {
            polygons = new PolygonList();
        }
        polygons.add(polygon);
        repaint();
    }

    /**
     * Real-world coordinates to pixels
     */
    private Point2D transform(final Point2D p) {
        return new Point2D(origin.x() + scale * p.x(), getHeight() - (origin.y() + scale * p.y()));
    }

    /**
     * Pixels to real-world coordinates
     */
    private Point2D iTransform(final int x, final int y) {
        return new Point2D((x - origin.x()) / scale, ((getHeight() - y) - origin.y()) / scale);
    }

    /**
     * Move invisibly to a point
     */
    private void move(final Point2D p) {
        pos = transform(p);
    }

    /**
     * Draw a straight line to a point
     */
    private void plot(final Graphics2D g2d, final Point2D p) {
        final Point2D a = transform(p);
        g2d.drawLine((int) Math.round(pos.x()), (int) Math.round(pos.y()), (int) Math.round(a.x()), (int) Math.round(a.y()));
        pos = a;
    }

    /**
     * Plot a box
     */
    private void plot(final Graphics2D g2d, final Rectangle b) {
        if (Rectangle.intersection(b, scaledBox).isEmpty()) {
            return;
        }

        g2d.setColor(BOX_COLOR);
        move(b.sw());
        plot(g2d, b.nw());
        plot(g2d, b.ne());
        plot(g2d, b.se());
        plot(g2d, b.sw());
    }

    /**
     * Plot a polygon
     */
    private void plot(final Graphics2D g2d, final Polygon p) {
        if (p.size() <= 0) {
            return;
        }
        if (Rectangle.intersection(p.getBox(), scaledBox).isEmpty()) {
            return;
        }
        g2d.setColor(colorMap.get(p.getMaterial()));
        move(p.point(0));
        for (int i = 1; i < p.size(); i++) {
            plot(g2d, p.point(i));
        }
        if (p.isClosed()) {
            g2d.setColor(Color.RED);
            plot(g2d, p.point(0));
        }
    }

    private void plot(final Graphics2D g2d, final BooleanGrid grid) {
        if (grid.isEmpty()) {
            return;
        }
        final PolygonList perimeters = grid.allPerimiters();
        for (int i = 0; i < perimeters.size(); i++) {
            final Polygon polygon = perimeters.polygon(i);
            plot(g2d, polygon);
        }
    }

    /**
     * Master plot function - draw everything
     */
    private void plot(final Graphics2D g2d) {
        plotBar(g2d);
        if (polygons != null) {
            for (int i = 0; i < polygons.size(); i++) {
                plot(g2d, polygons.polygon(i));
            }
            if (plotBoxes) {
                for (int i = 0; i < polygons.size(); i++) {
                    plot(g2d, polygons.polygon(i).getBox());
                }
            }
        }
        if (grids != null) {
            for (final BooleanGrid grid : grids) {
                plot(g2d, grid);
            }
        }
    }

    public final class TogglePlotBox extends KeyAdapter {
        @Override
        public void keyTyped(final KeyEvent k) {
            switch (k.getKeyChar()) {
            case 'b':
            case 'B':
                plotBoxes = !plotBoxes;
                repaint();
                break;
            case ' ':
                setPauseSlicer(!isPauseSlicer());
                break;
            }
        }
    }

    public final class MouseClickMagnifier extends MouseAdapter {
        private Rectangle magBox(final Rectangle b, final int ix, final int iy) {
            final Point2D cen = iTransform(ix, iy);
            final Point2D off = new Point2D(b.x().length() * 0.1, b.y().length() * 0.1);
            return new Rectangle(Point2D.sub(cen, off), Point2D.add(cen, off));
        }

        @Override
        public void mouseClicked(final MouseEvent e) {
            switch (e.getButton()) {
            case MouseEvent.BUTTON1:
                setScales(magBox(scaledBox, e.getX(), e.getY()));
                repaint();
                break;
            case MouseEvent.BUTTON2:
                break;
            case MouseEvent.BUTTON3:
            default:
                setScales(originalBox);
                repaint();
            }
        }
    }

    @Override
    public void paint(final Graphics g) {
        if (initialised) {
            plot((Graphics2D) g);
        }
    }

    public synchronized boolean isPauseSlicer() {
        return pauseSlicer;
    }

    public synchronized void setPauseSlicer(final boolean pauseSlicer) {
        this.pauseSlicer = pauseSlicer;
    }
}
