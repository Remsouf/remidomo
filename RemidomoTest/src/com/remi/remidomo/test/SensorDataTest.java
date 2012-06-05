package com.remi.remidomo.test;

import java.io.DataInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.Scanner;

import com.remi.remidomo.SensorData;
import com.remi.remidomo.SensorData.DirType;

import android.test.AndroidTestCase;
import android.util.Log;

public class SensorDataTest extends AndroidTestCase {

	private final long MINS_1  = 1*60*1000;
	private final long SAMPLING_P_EPSILON = SensorData.SAMPLING_PERIOD + MINS_1;

	private static SensorData data = null;
	public void testAdd01() {
		data = new SensorData("testAdd1", null, false);
		assertEquals(0, data.size());
		assertEquals("testAdd1", data.getTitle());
	}

	public void testAdd02() {
		data.addValue(new Date(0), 1.0f);
		assertEquals(0, data.getX(0).longValue());
		assertEquals(1.0f, data.getY(0).floatValue());
	}
	
	public void testAdd03() {
		data.addValue(new Date(SAMPLING_P_EPSILON), 1.2f);
		assertEquals(0, data.getX(0).longValue());
		assertEquals(1.0f, data.getY(0).floatValue());
		assertEquals(SAMPLING_P_EPSILON, data.getX(1).longValue());
		assertEquals(1.2f, data.getY(1).floatValue());
	}
		
	public void testAdd04() {
		data.addValue(new Date(SAMPLING_P_EPSILON+5*MINS_1), 1.4f);
		assertEquals(0, data.getX(0).longValue());
		assertEquals(1.0f, data.getY(0).floatValue());
		assertEquals(SAMPLING_P_EPSILON, data.getX(1).longValue());
		assertEquals(1.2f, data.getY(1).floatValue());
		assertEquals(SAMPLING_P_EPSILON+5*MINS_1, data.getX(2).longValue());
		assertEquals(1.4f, data.getY(2).floatValue());
	}
		
	public void testAdd05() {
		data.addValue(new Date(2*SAMPLING_P_EPSILON), 1.6f);
		assertEquals(0, data.getX(0).longValue());
		assertEquals(1.0f, data.getY(0).floatValue());
		assertEquals(SAMPLING_P_EPSILON, data.getX(1).longValue());
		assertEquals(1.2f, data.getY(1).floatValue());
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());
	}
		
	public void testAdd06() {
		data.addValue(new Date(2*SAMPLING_P_EPSILON+MINS_1), 1.8f);
		assertEquals(0, data.getX(0).longValue());
		assertEquals(1.0f, data.getY(0).floatValue());
		assertEquals(SAMPLING_P_EPSILON, data.getX(1).longValue());
		assertEquals(1.2f, data.getY(1).floatValue());
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());		
		assertEquals(2*SAMPLING_P_EPSILON+MINS_1, data.getX(3).longValue());
		assertEquals(1.8f, data.getY(3).floatValue());
	}
	
	public void testAdd07() {
		data.addValue(new Date(2*SAMPLING_P_EPSILON+2*MINS_1), 1.81f);
		assertEquals(0, data.getX(0).longValue());
		assertEquals(1.0f, data.getY(0).floatValue());
		assertEquals(SAMPLING_P_EPSILON, data.getX(1).longValue());
		assertEquals(1.2f, data.getY(1).floatValue());
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());		
		assertEquals(2*SAMPLING_P_EPSILON+2*MINS_1, data.getX(3).longValue());
		assertEquals(1.81f, data.getY(3).floatValue());
	}
		
	public void testAdd08() {
		data.addValue(new Date(2*SAMPLING_P_EPSILON+3*MINS_1), 1.82f);
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());		
		assertEquals(2*SAMPLING_P_EPSILON+3*MINS_1, data.getX(3).longValue());
		assertEquals(1.82f, data.getY(3).floatValue());
	}
	
	public void testAdd09() {
		data.addValue(new Date(3*SAMPLING_P_EPSILON), 1.8f);
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());		
		assertEquals(3*SAMPLING_P_EPSILON, data.getX(3).longValue());
		assertEquals(1.8f, data.getY(3).floatValue());
	}
	
	public void testAdd10() {
		data.addValue(new Date(3*SAMPLING_P_EPSILON+MINS_1), 5.0f);
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());		
		assertEquals(3*SAMPLING_P_EPSILON, data.getX(3).longValue());
		assertEquals(1.8f, data.getY(3).floatValue());
		assertEquals(3*SAMPLING_P_EPSILON+MINS_1, data.getX(4).longValue());
		assertEquals(5.0f, data.getY(4).floatValue());
	}
	
	public void testAdd11() {
		data.addValue(new Date(3*SAMPLING_P_EPSILON+2*MINS_1), 8.0f);
		assertEquals(2*SAMPLING_P_EPSILON, data.getX(2).longValue());
		assertEquals(1.6f, data.getY(2).floatValue());		
		assertEquals(3*SAMPLING_P_EPSILON, data.getX(3).longValue());
		assertEquals(1.8f, data.getY(3).floatValue());
		assertEquals(3*SAMPLING_P_EPSILON+MINS_1, data.getX(4).longValue());
		assertEquals(5.0f, data.getY(4).floatValue());
		assertEquals(3*SAMPLING_P_EPSILON+2*MINS_1, data.getX(5).longValue());
		assertEquals(8.0f, data.getY(5).floatValue());
	}
	
	public void testWriteJSON() {
		data = new SensorData("testJSON", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.2f);
		data.addValue(new Date(20000), 3.0f);
		data.addValue(new Date(30000), 4.0f);
		
		String array = data.getJSONArray(0).toString();
		assertEquals("[[0,1],[10000,2.2],[20000,3],[30000,4]]", array);
	}
	
	public void testWriteCSV() {
		StringWriter sw = new StringWriter();
		data = new SensorData("testCSV", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.2f);
		data.addValue(new Date(20000), 3.0f);
		data.writeCSV(sw);
		assertEquals("testCSV<br/>1 Jan 1970 00:00:00 GMT;1.0<br/>1 Jan 1970 00:00:10 GMT;2.2<br/>1 Jan 1970 00:00:20 GMT;3.0<br/><br/>", sw.toString());
	}
	
	public void testPartialRead() {
		data = new SensorData("testJSON", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.2f);
		data.addValue(new Date(20000), 3.0f);
		data.addValue(new Date(30000), 4.0f);
		
		String array = data.getJSONArray(15000).toString();
		assertEquals("[[20000,3],[30000,4]]", array);
	}
	
	public void testWriteAscii() throws java.io.FileNotFoundException, java.io.IOException {
		data = new SensorData("testWriteAscii", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.0f);
		data.addValue(new Date(20000), 3.0f);
		data.addValue(new Date(30000), 4.0f);
		data.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.ASCII);

		FileInputStream fis = new FileInputStream("/sdcard/testWriteAscii.dat");
		Scanner scanner = new Scanner(fis);
		assertEquals(SensorData.FileFormat.ASCII.toString(), scanner.next());
		assertEquals(0, Long.parseLong(scanner.next()));
		assertEquals(1.0f, Float.parseFloat(scanner.next()));
		assertEquals(10000, Long.parseLong(scanner.next()));
		assertEquals(2.0f, Float.parseFloat(scanner.next()));
		assertEquals(20000, Long.parseLong(scanner.next()));
		assertEquals(3.0f, Float.parseFloat(scanner.next()));
		assertEquals(30000, Long.parseLong(scanner.next()));
		assertEquals(4.0f, Float.parseFloat(scanner.next()));
		assertFalse(scanner.hasNext());
		
		fis.close();
	}

	public void testWriteBinary() throws java.io.FileNotFoundException, java.io.IOException {
		data = new SensorData("testWriteBinary", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.0f);
		data.addValue(new Date(20000), 3.0f);
		data.addValue(new Date(30000), 4.0f);
		data.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.BINARY);

		FileInputStream fis = new FileInputStream("/sdcard/testWriteBinary.dat");

		DataInputStream dis = new DataInputStream(fis);
		assertEquals(SensorData.FileFormat.BINARY.toString(), dis.readLine());
		assertEquals(0, dis.readLong());
		assertEquals(1.0f, dis.readFloat());
		assertEquals(10000, dis.readLong());
		assertEquals(2.0f, dis.readFloat());
		assertEquals(20000, dis.readLong());
		assertEquals(3.0f, dis.readFloat());
		assertEquals(30000, dis.readLong());
		assertEquals(4.0f, dis.readFloat());
		assertEquals(0, dis.available());
		fis.close();
	}
	
	public void testReadAscii() throws java.io.FileNotFoundException, java.io.IOException {
		data = new SensorData("testReadAscii", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.0f);
		data.addValue(new Date(20000), 3.0f);
		data.addValue(new Date(30000), 4.0f);
		data.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.ASCII);

		data = new SensorData("testReadAscii", null, false);
		data.readFile(SensorData.DirType.SDCARD);

		String array = data.getJSONArray(0).toString();
		assertEquals("[[0,1],[10000,2],[20000,3],[30000,4]]", array);
	}
	
	public void testReadBinary() throws java.io.FileNotFoundException, java.io.IOException {
		data = new SensorData("testReadBinary", null, false);
		data.addValue(new Date(0), 1.0f);
		data.addValue(new Date(10000), 2.0f);
		data.addValue(new Date(20000), 3.0f);
		data.addValue(new Date(30000), 4.0f);
		data.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.BINARY);

		data = new SensorData("testReadBinary", null, false);
		data.readFile(SensorData.DirType.SDCARD);

		String array = data.getJSONArray(0).toString();
		assertEquals("[[0,1],[10000,2],[20000,3],[30000,4]]", array);
	}
}