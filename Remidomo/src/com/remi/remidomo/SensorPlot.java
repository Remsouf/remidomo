package com.remi.remidomo;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import android.widget.Toast;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;

import com.androidplot.Plot;
import com.androidplot.series.XYSeries;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.RectRegion;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XValueMarker;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYRegionFormatter;
import com.androidplot.xy.XYStepMode;
import com.androidplot.xy.YLayoutStyle;
import com.androidplot.xy.YPositionMetric;

public class SensorPlot extends XYPlot implements OnTouchListener {
	
//	private final static String TAG = RDActivity.class.getSimpleName();
	
	// Definition of the touch states
	private final static int NONE = 0;
	private final static int ONE_FINGER_DRAG = 1;
	private final static int TWO_FINGERS_DRAG = 2;
	private int mode = NONE;

	private final static long HOURS_24 = 24*60*60*1000;
	private final static long HOURS_12 = 12*60*60*1000;

	private final static int[] COLORS_TABLE = {Color.CYAN, Color.GREEN, Color.YELLOW};

	private Number minXSeriesValue;
	private Number maxXSeriesValue;
	private Number minYSeriesValue;
	private Number maxYSeriesValue;

	private float rangeMinY = -100.0f;
	private float rangeMaxY = 100.0f;
	
	private PointF firstFinger;
	private float lastScrolling;
	private float distBetweenFingers;

	private Number newMinX;
	private Number newMaxX;
	
	// Attributes (from XML)
	private int curveColor;
	private int axesColor;
	private int gridColor;
	private int nightsColor;
	private int dotsColor;
	private boolean autoRange;
    private boolean tapEnabled;
    private String units;
	
	private SharedPreferences prefs;
	
	// Possible temp. display on tap
	private Toast tempToast = null;

	public SensorPlot(Context context, String title) {
		super(context, title);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		// No attributes -> set defaults
		curveColor = 0;
		axesColor = Color.DKGRAY;
		gridColor = Color.DKGRAY;
		nightsColor = Color.parseColor("#5A505080");
		dotsColor = Color.CYAN;
		autoRange = true;
		tapEnabled = false;
		units = "?";

		initTouchHandling();
		initPlot();
	}

	public SensorPlot(Context context, AttributeSet attrs) {
		super(context, attrs);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SensorPlot);
		curveColor = a.getColor(R.styleable.SensorPlot_curve_color, 0);
		axesColor = a.getColor(R.styleable.SensorPlot_axes_color, Color.parseColor("#80000000"));
		gridColor = a.getColor(R.styleable.SensorPlot_grid_color, Color.parseColor("#400000FF"));
		nightsColor = a.getColor(R.styleable.SensorPlot_nights_color, Color.parseColor("#5A505080"));
		dotsColor = a.getColor(R.styleable.SensorPlot_dots_color, Color.CYAN);
		autoRange = a.getBoolean(R.styleable.SensorPlot_auto_range, false);
		tapEnabled = a.getBoolean(R.styleable.SensorPlot_tap_enabled, false);
		units = a.getString(R.styleable.SensorPlot_units);
		initTouchHandling();
		initPlot();
	}

	public SensorPlot(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SensorPlot);
		curveColor = a.getColor(R.styleable.SensorPlot_curve_color, 0);
		axesColor = a.getColor(R.styleable.SensorPlot_axes_color, Color.WHITE);
		gridColor = a.getColor(R.styleable.SensorPlot_grid_color, Color.WHITE);
		nightsColor = a.getColor(R.styleable.SensorPlot_nights_color, Color.WHITE);
		dotsColor = a.getColor(R.styleable.SensorPlot_dots_color, Color.CYAN);
		autoRange = a.getBoolean(R.styleable.SensorPlot_auto_range, false);
		tapEnabled = a.getBoolean(R.styleable.SensorPlot_tap_enabled, false);
		units = a.getString(R.styleable.SensorPlot_units);

		initTouchHandling();
		initPlot();
	}

	private void initTouchHandling() {
		this.setOnTouchListener(this);
	}

	private void initPlot() {
	    // Colors
        getGraphWidget().setBackgroundPaint(null);
        getGraphWidget().setGridBackgroundPaint(null);
        getGraphWidget().getGridLinePaint().setColor(gridColor);
        getGraphWidget().getDomainOriginLinePaint().setColor(gridColor);
        getGraphWidget().getDomainLabelPaint().setColor(axesColor);
        getGraphWidget().getDomainOriginLabelPaint().setColor(axesColor);
        getGraphWidget().getRangeOriginLinePaint().setColor(gridColor);
        getGraphWidget().getRangeLabelPaint().setColor(axesColor);
        getGraphWidget().getRangeOriginLabelPaint().setColor(axesColor);
        getRangeLabelWidget().getLabelPaint().setColor(axesColor);

        // No border
        setBorderStyle(Plot.BorderStyle.NONE, null, null);
 
        // No title
        getTitleWidget().setVisible(false);
        
        // No legend
        getLegendWidget().setVisible(false);
        
        // draw a domain tick every 3 values
        setDomainStep(XYStepMode.INCREMENT_BY_PIXELS, 200);
        
        // domain/range labels
        setDomainLabel("");
        setRangeLabel(units);
 
        setPlotMargins(0, 0, 0, 0);
        setPlotPadding(0, 0, 0, 0);
        
        // Range / domain format
        setRangeValueFormat(new DecimalFormat("0.0"));
        setDomainValueFormat(new PoolDateFormat());
 
        // by default, AndroidPlot displays developer guides to aid in laying out your plot.
        // To get rid of them call disableAllMarkup():
        disableAllMarkup();
	}
	
	public void addSeries(SensorData series) {
		addSeries(series, 0);
	}

	public void addSeries(SensorData series, int daysBack) {

		SimpleXYSeries filteredSeries = null;

		if (series != null) {
			// Remove points before days limit
			int effDaysBack = daysBack;
			if (effDaysBack == 0) {
				effDaysBack = Integer.parseInt(prefs.getString("plot_limit", Preferences.DEFAULT_PLOTLIMIT));
			}
			long limit = new Date().getTime() - effDaysBack * HOURS_24;
			filteredSeries = series.filter(limit);

			int dotsEffectiveColor = 0;
			if (prefs.getBoolean("dots_highlight", Preferences.DEFAULT_DOTS_HIGHLIGHT)) {
				dotsEffectiveColor = dotsColor;
			} else {
				dotsEffectiveColor = Color.argb(0, 0, 0, 0);
			}

			int curveEffectiveColor = 0;
			if (curveColor != 0) {
				curveEffectiveColor = curveColor;
			} else {
				curveEffectiveColor = COLORS_TABLE[getSeriesSet().size()];
			}

			LineAndPointFormatter formatter  = new LineAndPointFormatter(curveEffectiveColor, dotsEffectiveColor, null);
			XYRegionFormatter regionFormatter = new XYRegionFormatter(nightsColor);

			// Get initial timestamp, and force 20:00 as starting point
			if (prefs.getBoolean("night_highlight", Preferences.DEFAULT_NIGHT_HIGHLIGHT) &&
					(filteredSeries.size() > 0)) {
				Date startDate = new Date(filteredSeries.getX(0).longValue());
				startDate.setHours(20);
				startDate.setMinutes(0);
				long currentX = startDate.getTime();
				while (currentX < filteredSeries.getX(filteredSeries.size()-1).longValue()) {
					// Float.[NEGATIVE|POSITIVE]_INFINITY don't work on ICS
					final float NEGATIVE_INFINITY = 0.0f;
					final float POSITIVE_INFINITY = 50.0f;
					formatter.addRegion(new RectRegion(currentX, currentX + HOURS_12, NEGATIVE_INFINITY, POSITIVE_INFINITY), regionFormatter);
					currentX += HOURS_24;
				}       		
			}

			if (prefs.getBoolean("day_labels", Preferences.DEFAULT_DAY_LABELS) &&
					(filteredSeries.size() > 0)) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("E");
				Date startDate = new Date(filteredSeries.getX(0).longValue());
				startDate.setHours(12);
				startDate.setMinutes(0);
				long currentX = startDate.getTime();
				while (currentX < filteredSeries.getX(filteredSeries.size()-1).longValue()) {
					String dayLabel = dateFormat.format(new Date(currentX));
					dayLabel = dayLabel.substring(0, 1).toUpperCase();
					XValueMarker marker = new XValueMarker(currentX, dayLabel);
					marker.getLinePaint().setAlpha(0);
					marker.getTextPaint().setColor(gridColor);
					marker.setTextPosition(new YPositionMetric(0.0f,
							YLayoutStyle.ABSOLUTE_FROM_BOTTOM));
					addMarker(marker);

					currentX += HOURS_24;
				}       		
			}

			addSeries(filteredSeries, formatter);
		}

		if ((filteredSeries != null) && (filteredSeries.size() > 0)) {
			// On X-axis, min is 1st and max the last
			// (time always going forward)
			minXSeriesValue = filteredSeries.getX(0);
			maxXSeriesValue = filteredSeries.getX(filteredSeries.size()-1);

			newMinX = minXSeriesValue;
			newMaxX = maxXSeriesValue;

			calculateMinMaxVals();
			updateRangeBoundaries();
		}
	}
	
	public boolean onTouch(View view, MotionEvent motionEvent) {

		// Empty set
		if (isEmpty() || getSeriesSet().isEmpty()) {
			return false;
		}

		// Empty series
		XYSeries series = getSeriesSet().iterator().next();
		if (series.size() == 0) {
			return false;
		}

		switch(motionEvent.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN: //start gesture
			firstFinger = new PointF(motionEvent.getX(), motionEvent.getY());
			mode = ONE_FINGER_DRAG;
			break;

		case MotionEvent.ACTION_POINTER_DOWN: //second finger
			distBetweenFingers = distance(motionEvent);
			// the distance check is done to avoid false alarms
			if (distBetweenFingers > 5f || distBetweenFingers < -5f) {
				mode = TWO_FINGERS_DRAG;
			}
			break;

		case MotionEvent.ACTION_POINTER_UP: //end zoom
			//should I count pointers and change mode after only one is left?
			mode = ONE_FINGER_DRAG;
			break;

		case MotionEvent.ACTION_UP: //end tap
		    if (tapEnabled && (motionEvent.getEventTime() - motionEvent.getDownTime() <= 100)) {
				displayPointValue(firstFinger);
			}
			break;

		case MotionEvent.ACTION_MOVE:
			if (mode == ONE_FINGER_DRAG) {
				final PointF oldFirstFinger = firstFinger;
				firstFinger = new PointF(motionEvent.getX(), motionEvent.getY());
				lastScrolling = oldFirstFinger.x - firstFinger.x;
				scroll(lastScrolling);
				fixBoundariesForScroll();
				
				setDomainBoundaries(newMinX, newMaxX, BoundaryMode.FIXED);
				redraw();
			}
			else if (mode == TWO_FINGERS_DRAG) {
				final float oldDist = distBetweenFingers;
				final float newDist = distance(motionEvent);
				if (oldDist > 0 && newDist < 0 || oldDist < 0 && newDist > 0) //sign change! Fingers have crossed ;-)
					break;

				distBetweenFingers = newDist;

				zoom(oldDist / distBetweenFingers);

				fixBoundariesForZoom();
				setDomainBoundaries(newMinX, newMaxX, BoundaryMode.FIXED);
				redraw();
			}
			break;

		default:
			// Do nothing
			break;
		}
		return true;
	}

	private void updateRangeBoundaries() {
		if (autoRange) {
			setRangeBoundaries(0.0, 0.0, BoundaryMode.AUTO);
		} else if (!getSeriesSet().isEmpty()) {
			// Temporary switch to autorange, to get real min/max
			// (or we get the fixed range we set just before !)
			setRangeBoundaries(0.0, 0.0, BoundaryMode.AUTO);
			calculateMinMaxVals();
        	minYSeriesValue = getCalculatedMinY();
        	maxYSeriesValue = getCalculatedMaxY();
        	if ((minYSeriesValue != null) &&
        		(maxYSeriesValue != null)) {
        		if (minYSeriesValue.floatValue() >= 25.0) {
        			rangeMinY = 25.0f;
        		} else if (minYSeriesValue.floatValue() >= 20.0) {
        			rangeMinY = 20.0f;
        		} else if (minYSeriesValue.floatValue() >= 10.0) {
        			rangeMinY = 10.0f;
        		} else if (minYSeriesValue.floatValue() >= 0.0) {
        			rangeMinY = 0.0f;
        		} else {
        			rangeMinY = -10.0f;
        		}
        		if (maxYSeriesValue.floatValue() < 20.0) {
        			rangeMaxY = 20.0f;
        		} else if (maxYSeriesValue.floatValue() < 30.0) {
        			rangeMaxY = 30.0f;
        		} else if (maxYSeriesValue.floatValue() < 32.5) {
        			rangeMaxY = 32.5f;
        		} else if (maxYSeriesValue.floatValue() < 35.0) {
        			rangeMaxY = 35.0f;
        		} else if (maxYSeriesValue.floatValue() < 40.0) {
        			rangeMaxY = 40.0f;
        		} else {
        			rangeMaxY = 48.0f;
        		}
        	} else {
        		rangeMinY = 10.0f;
        		rangeMaxY = 30.0f;
        	}
        	
        	setRangeBoundaries(rangeMinY, rangeMaxY, BoundaryMode.FIXED);
        }
		
	}
	
	private void scroll(float pan) {
		float calculatedMinX = getCalculatedMinX().floatValue();
		float calculatedMaxX = getCalculatedMaxX().floatValue();
		final float domainSpan =  calculatedMaxX - calculatedMinX;
		final float step = domainSpan / getWidth();
		final float offset = pan * step;

		newMinX = calculatedMinX + offset;
		newMaxX = calculatedMaxX + offset;
		
		updateRangeBoundaries();
	}

	private void fixBoundariesForScroll() {
		float diff = newMaxX.floatValue() - newMinX.floatValue();
		if(newMinX.floatValue() < minXSeriesValue.floatValue())	{
			newMinX = minXSeriesValue;
			newMaxX = newMinX.floatValue() + diff;
		}
		if(newMaxX.floatValue() > maxXSeriesValue.floatValue())	{
			newMaxX = maxXSeriesValue;
			newMinX = newMaxX.floatValue() - diff;
		}
	}

	private float distance(MotionEvent event) {
		final float x = event.getX(0) - event.getX(1);
		return x;
	}

	private void zoom(float scale) {
		if (Float.isInfinite(scale) || Float.isNaN(scale) || (scale > -0.001 && scale < 0.001)) //sanity check
			return;

		float calculatedMinX = getCalculatedMinX().floatValue();
		float calculatedMaxX = getCalculatedMaxX().floatValue();
		final float domainSpan =  calculatedMaxX - calculatedMinX;
		final float domainMidPoint = calculatedMaxX - domainSpan / 2.0f;
		final float offset = domainSpan * scale / 2.0f;
		newMinX = domainMidPoint - offset;
		newMaxX = domainMidPoint + offset;
		
		updateRangeBoundaries();
	}

	private void fixBoundariesForZoom()	{
		if (newMinX.floatValue() < minXSeriesValue.floatValue()) {
			newMinX = minXSeriesValue;
		}
		if (newMaxX.floatValue() > maxXSeriesValue.floatValue()) {
			newMaxX = maxXSeriesValue;
		}
	}

	private static class PoolDateFormat extends Format {
		private static final long serialVersionUID = 0;
		
        // create a simple date format that draws on the year portion of our timestamp.
        // see http://download.oracle.com/javase/1.4.2/docs/api/java/text/SimpleDateFormat.html
        // for a full description of SimpleDateFormat.
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM-kk:mm");

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            long timestamp = ((Number) obj).longValue();
            Date date = new Date(timestamp);
            return dateFormat.format(date, toAppendTo, pos);
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            return null;
        }
	}
	
	private void displayPointValue(PointF point) {
		// Allowed only for ONE curve !
		if (getSeriesSet().size() == 1) {
        	XYSeries series = getSeriesSet().iterator().next();
        	long fingerX = this.getGraphWidget().getXVal(point).longValue();

        	// Find the nearest point to timestamp
        	int i = 0;
        	while (i<series.size() && series.getX(i).longValue() < fingerX) {
        		i = i+1;
        	}

        	if (i < series.size()) {
        		float fingerY = series.getY(i).floatValue();
        		DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
                decimalFormat.applyPattern("#0.0#");

        		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        		View layout = inflater.inflate(R.layout.temp_value,
        					(ViewGroup) findViewById(R.id.temp_value));
        		TextView text = (TextView) layout.findViewById(R.id.temp_degrees);

        		text.setText(decimalFormat.format(fingerY)+getContext().getString(R.string.degC));
        		
        		if (tempToast == null) {
        			tempToast = new Toast(getContext());
        		}
        		tempToast.setDuration(Toast.LENGTH_SHORT);
        		tempToast.setView(layout);
        		
        		int toastX = Math.round(point.x);
        		// Compute Y pos of the curve under finger
        		int toastY = Math.round((rangeMaxY-fingerY) * getHeight() / (rangeMaxY - rangeMinY));
        		tempToast.setGravity(Gravity.TOP|Gravity.LEFT, toastX, toastY);
        		tempToast.show();
        	}
		}	
	}
	
	public Bitmap getBitmap(int width, int height) {
		this.layout(0, 0, width, height);
		Bitmap viewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(viewBitmap);
		draw(canvas);
		return viewBitmap;
	}
}
