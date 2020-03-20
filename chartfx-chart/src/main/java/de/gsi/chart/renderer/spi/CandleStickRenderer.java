package de.gsi.chart.renderer.spi;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.spi.CategoryAxis;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.renderer.spi.utils.DefaultRenderColorScheme;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.DataSet2D;
import de.gsi.dataset.spi.CandleStickDataSet;
import de.gsi.dataset.utils.ProcessingProfiler;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CandleStickRenderer extends AbstractDataSetManagement<CandleStickRenderer> implements
    Renderer {

    private int maxPoints;

    public CandleStickRenderer() {
        maxPoints = 300;
    }

    public CandleStickRenderer(final int maxPoints) {
        this.maxPoints = maxPoints;
    }

    @Override
    public ObservableList<DataSet> getDatasets() {
        return super.getDatasets();
    }

    @Override
    public Canvas drawLegendSymbol(DataSet dataSet, int dsIndex, int width, int height) {
        // not implemented for this class
        return null;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    @Override
    protected CandleStickRenderer getThis() {
        return this;
    }

    @Override
    public void render(final GraphicsContext gc, final Chart chart, final int dataSetOffset,
            final ObservableList<DataSet> datasets) {
        if (!(chart instanceof XYChart)) {
            throw new InvalidParameterException(
                    "must be derivative of XYChart for renderer - " + this.getClass().getSimpleName());
        }
        final XYChart xyChart = (XYChart) chart;

        // make local copy and add renderer specific data sets
        final List<DataSet> localDataSetList = new ArrayList<>(datasets);
        localDataSetList.addAll(super.getDatasets());

        final long start = ProcessingProfiler.getTimeStamp();
        final Axis xAxis = xyChart.getXAxis();
        final Axis yAxis = xyChart.getYAxis();

        final double xAxisWidth = xAxis.getWidth();
        final double xmin = xAxis.getValueForDisplay(0);
        final double xmax = xAxis.getValueForDisplay(xAxisWidth);
        int index = 0;
        for (final DataSet ds : localDataSetList) {
            if (!(ds instanceof DataSet2D)) {
                continue;
            }
            final DataSet2D dataset = (DataSet2D) ds;
            final int lindex = index;
            dataset.lock().readLockGuardOptimistic(() -> {
                // update categories in case of category axes for the first
                // (index == '0') indexed data set
                if (lindex == 0) {
                    if (xyChart.getXAxis() instanceof CategoryAxis) {
                        final CategoryAxis axis = (CategoryAxis) xyChart.getXAxis();
                        axis.updateCategories(dataset);
                    }

                    if (xyChart.getYAxis() instanceof CategoryAxis) {
                        final CategoryAxis axis = (CategoryAxis) xyChart.getYAxis();
                        axis.updateCategories(dataset);
                    }
                }

                gc.save();
                DefaultRenderColorScheme.setLineScheme(gc, dataset.getStyle(), lindex);
                DefaultRenderColorScheme.setGraphicsContextAttributes(gc, dataset.getStyle());
                if (dataset.getDataCount() > 0) {
                    final int indexMin = Math.max(0, dataset.getXIndex(xmin));
                    final int indexMax = Math.min(dataset.getXIndex(xmax) + 1, dataset.getDataCount());
                    final int n = Math.abs(indexMax - indexMin);
                    final int d = n / maxPoints;

                    if (true) { //d <= 1

                        int i = dataset.getXIndex(xmin);
                        if (i < 0) {
                            i = 0;
                        }

                        final double minRequiredWidth = minDataPointDistanceX(dataset, xAxis);
                        double barWPercentage = 70;
                        double localBarWidth = minRequiredWidth * barWPercentage / 100;;
                        double barWidthHalf = localBarWidth / 2;

                        for (; i < Math.min(dataset.getXIndex(xmax) + 1, dataset.getDataCount()); i++) {
                            double x0 = xAxis.getDisplayPosition(dataset.get(DataSet.DIM_X, i));
                            double yOpen = yAxis.getDisplayPosition(dataset.get(CandleStickDataSet.DIM_Y_OPEN, i));
                            double yClose = yAxis.getDisplayPosition(dataset.get(CandleStickDataSet.DIM_Y_CLOSE, i));
                            double yLow = yAxis.getDisplayPosition(dataset.get(CandleStickDataSet.DIM_Y_LOW, i));
                            double yHigh = yAxis.getDisplayPosition(dataset.get(CandleStickDataSet.DIM_Y_HIGH, i));

                            double yDiff = yOpen - yClose;
                            double yMin;
                            if (yDiff > 0) {
                                yMin = yClose;
                                gc.setFill(Color.GREEN);
                            } else {
                                yMin = yOpen;
                                yDiff = Math.abs(yDiff);
                                gc.setFill(Color.RED);
                            }

                            gc.setStroke(Color.BLACK);
                            gc.strokeLine(x0, yLow, x0, yDiff > 0 ? yOpen : yClose);
                            gc.strokeLine(x0, yHigh, x0, yDiff > 0 ? yClose : yOpen);
                            gc.fillRect(x0 - barWidthHalf, yMin, localBarWidth, yDiff); // open-close
                        }
                    }
                }
                gc.restore();
            });
            index++;
        }
        ProcessingProfiler.getTimeDiff(start);
    }

//    private int minDistanceX = Integer.MAX_VALUE;
    private double minDataPointDistanceX(DataSet2D dataSet, Axis xAxis) {
        double minDistance = Integer.MAX_VALUE;
        for (int i = 1; i < dataSet.getDataCount(DataSet.DIM_X); i++) {
            final double param0 = xAxis.getDisplayPosition(dataSet.get(DataSet.DIM_X, i - 1));
            final double param1 = xAxis.getDisplayPosition(dataSet.get(DataSet.DIM_X, i));

            if (param0 != param1) {
                minDistance = Math.min(minDistance, Math.abs(param1 - param0));
            }
        }
        return minDistance;
    }

    public void setMaxPoints(final int maxPoints) {
        this.maxPoints = maxPoints;
    }
}
