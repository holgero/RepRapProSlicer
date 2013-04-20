package org.reprap.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.swing.JLabel;
import javax.swing.JProgressBar;

import org.reprap.geometry.ProductionProgressListener;

final class ProgressBarUpdater implements ProductionProgressListener {
    private final JLabel currentLayerOutOfN;
    private final JProgressBar progressBar;
    private final JLabel expectedBuildTime;
    private final JLabel expectedFinishTime;
    private final long startTime;
    private int oldLayer = -1;

    public ProgressBarUpdater(final JLabel currentLayerOutOfN, final JProgressBar progressBar,
            final JLabel expectedBuildTime, final JLabel expectedFinishTime, final long startTime) {
        super();
        this.currentLayerOutOfN = currentLayerOutOfN;
        this.progressBar = progressBar;
        this.expectedBuildTime = expectedBuildTime;
        this.expectedFinishTime = expectedFinishTime;
        this.startTime = startTime;
    }

    @Override
    public void productionProgress(final int layer, final int totalLayers) {
        if (layer >= 0) {
            currentLayerOutOfN.setText("" + layer + "/" + totalLayers);
        }

        final double fractionDone;
        if (layer < oldLayer) {
            fractionDone = (double) (totalLayers - layer) / (double) totalLayers;
        } else {
            fractionDone = (double) layer / (double) totalLayers;
        }
        oldLayer = layer;

        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue((int) (100 * fractionDone));

        final GregorianCalendar cal = new GregorianCalendar();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("EE HH:mm:ss Z");
        final Date d = cal.getTime();
        final long e = d.getTime();
        final long f = (long) ((e - startTime) / fractionDone);
        final int h = (int) (f / 60000) / 60;
        final int m = (int) (f / 60000) % 60;

        if (m > 9) {
            expectedBuildTime.setText("" + h + ":" + m);
        } else {
            expectedBuildTime.setText("" + h + ":0" + m);
        }
        expectedFinishTime.setText(dateFormat.format(new Date(startTime + f)));
    }
}