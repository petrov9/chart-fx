package de.gsi.chart.renderer.spi;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.spi.CategoryAxis;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.renderer.spi.utils.DefaultRenderColorScheme;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.DataSet2D;
import de.gsi.dataset.spi.DoubleHistogramDataSet;
import de.gsi.dataset.utils.ProcessingProfiler;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class DoubleHorizontalHistogramRenderer extends
    AbstractDataSetManagement<DoubleHorizontalHistogramRenderer> implements Renderer {

    private int maxPoints;

    public DoubleHorizontalHistogramRenderer() {
        maxPoints = 300;
    }

    public DoubleHorizontalHistogramRenderer(final int maxPoints) {
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
    protected DoubleHorizontalHistogramRenderer getThis() {
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

                    if (true) { // d <= 1

                        int i = dataset.getXIndex(xmin);
                        if (i < 0) {
                            i = 0;
                        }

                        double xLeftMax = Arrays.stream(dataset.getValues(DoubleHistogramDataSet.DIM_X_LEFT)).max().getAsDouble();
                        double xRightMax = Arrays.stream(dataset.getValues(DoubleHistogramDataSet.DIM_X_RIGHT)).max().getAsDouble();

                        final double minRequiredWidth = minDataPointDistance(dataset, xAxis, DoubleHistogramDataSet.DIM_X);
                        double barWPercentage = 45;
                        double localBarWidth = minRequiredWidth * barWPercentage / 100;

                        final double minRequiredHeight = minDataPointDistance(dataset, yAxis, DoubleHistogramDataSet.DIM_Y);
                        double barHPercentage = 70;
                        double localBarHeight = Math.abs(minRequiredHeight * barHPercentage / 100);
                        double spaceBetweenColumns = minRequiredHeight * 0.1;

                        for (; i < Math.min(dataset.getXIndex(xmax) + 1, dataset.getDataCount()); i++) {
                            double x = dataset.get(DoubleHistogramDataSet.DIM_X, i);
                            double y = dataset.get(DoubleHistogramDataSet.DIM_Y, i);
                            double left = dataset.get(DoubleHistogramDataSet.DIM_X_LEFT, i);
                            double right = dataset.get(DoubleHistogramDataSet.DIM_X_RIGHT, i);

                            double xPos = xAxis.getDisplayPosition(x);
                            double yPos = yAxis.getDisplayPosition(y);

                            double leftBarWidth = Math.abs(left / xLeftMax * localBarWidth);
                            double rightBarWidth = Math.abs(right / xRightMax * localBarWidth);


                            if (y >= 0) {
                                gc.setFill(Color.GREEN);
                            } else {
                                gc.setFill(Color.RED);
                            }

                            gc.fillRect(xPos - leftBarWidth - spaceBetweenColumns, yPos - localBarHeight, leftBarWidth, localBarHeight);
                            gc.fillRect(xPos + spaceBetweenColumns, yPos - localBarHeight, rightBarWidth, localBarHeight);
                        }
                    }
                }
                gc.restore();
            });
            index++;
        }
        ProcessingProfiler.getTimeDiff(start);
    }

    private double minDataPointDistance(DataSet2D dataSet, Axis axis, int dim) {
        double minDistance = Integer.MAX_VALUE;
        for (int i = 1; i < dataSet.getDataCount(dim); i++) {
            final double param0 = axis.getDisplayPosition(dataSet.get(dim, i - 1));
            final double param1 = axis.getDisplayPosition(dataSet.get(dim, i));

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