package com.remi.remidomo.test;

import java.util.Date;

import com.androidplot.series.XYSeries;
import com.remi.remidomo.reloaded.SensorData;
import com.remi.remidomo.reloaded.SensorData.Pair;
import com.remi.remidomo.reloaded.SensorPlot;

import android.test.AndroidTestCase;
import android.util.Log;

public class SensorPlotTest extends AndroidTestCase {
	
	private boolean endedA = false;
	private boolean endedB = false;
	
	private void addValues(SensorData series, int number) {
		final int SAMPLE_PERIOD = 1000*60;  // 1 sample / min
		long tstamp = new Date().getTime() - number*SAMPLE_PERIOD;
		float value = 0.0f;
		
		for (int i=0; i<number; i++) {
			series.addValue(new Date(tstamp), value, SensorData.CompressionType.NONE);
			tstamp = tstamp + SAMPLE_PERIOD;
			value = value + 0.1f;
		}
	}
	
	
	public void testCreate0() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testCreate1");
		assertNotNull(plot);
	}
	
	public void testCreate5000() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testCreate5000");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 5000);
		plot.addSeries(data);
	}
	
	public void testCreate15000() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testCreate15000");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 15000);
		plot.addSeries(data);
	}
	
	public void testFilteringHalf() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testFilteringHalf");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 14400); // 10 days
		plot.addSeries(data, 5);
		
		XYSeries series = plot.getSeriesSet().iterator().next();
		assertEquals(7199, series.size());
	}
	
	public void testFilteringAll() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testFilteringHalf");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 7200); // 5 days
		// Shift test data 10 days back
		for (int i=0; i<7200; i++) {
			Pair pair = new SensorData.Pair(
					data.get(i).time-10*24*60*60*1000,
					data.get(i).value);
			data.set(i, pair);
		}
		
		plot.addSeries(data, 5);
		
		XYSeries series = plot.getSeriesSet().iterator().next();
		assertEquals(0, series.size());
	}
	
	public void testFilteringNone() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testFilteringHalf");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 7200); // 5 days
		plot.addSeries(data, 10);
		
		XYSeries series = plot.getSeriesSet().iterator().next();
		assertEquals(7200, series.size());
	}
	
	public void testRepeatFiltering1Small() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testFilteringSmall");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 5040); // 3.5 days

		for (int i=0; i<100; i++) {
			Log.d("testRepeat1", "Iteration: " + i);
			plot.clear();
			plot.addSeries(data, 2);
		}
	}

	public void TestRepeatFiltering2Concurrent() {

		final SensorData data = new SensorData("series", null, false);
		addValues(data, 5040); // 3.5 days

		endedA = false;
		endedB = false;
		
		new Thread(new Runnable() {
			public void run() {
				SensorPlot plot = new SensorPlot(SensorPlotTest.this.getContext(), "testFilteringConcurrentA");
				for (int i=0; i<100; i++) {
					plot.clear();
					plot.addSeries(data, 5);
				}
				endedA = true;
			};
		}).start();

		new Thread(new Runnable() {
			public void run() {
				SensorPlot plot = new SensorPlot(SensorPlotTest.this.getContext(), "testFilteringConcurrentB");
				for (int i=0; i<100; i++) {
					plot.clear();
					plot.addSeries(data, 2);
				}
				endedB = true;
			};
		}).start();
		
		while (!endedA && !endedB) {
			
		}
	}
	
	public void TestRepeatFiltering3Big() {
		SensorPlot plot = new SensorPlot(this.getContext(), "testFilteringBig");
		SensorData data = new SensorData("series", null, false);
		addValues(data, 36000); // 3 months

		for (int i=0; i<10; i++) {
			Log.d("testRepeat3", "Iteration: " + i);
			plot.clear();
			plot.addSeries(data, 5);
		}
	}
}