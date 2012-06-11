package com.remi.remidomo;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.json.JSONArray;

import com.androidplot.series.XYSeries;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;

public class ServerThread implements Runnable {
	
	private final static String TAG = RDService.class.getSimpleName();
	
	private Thread thread = null;
	private boolean runThread = false;
	
	private RDService service;
	private boolean result;

    private SharedPreferences prefs;
    
    private ServerSocket serverSocket = null;
    private HttpService httpService = null;
    private BasicHttpContext httpContext = null;
    private BasicHttpProcessor httpProc = null;
    private HttpRequestHandlerRegistry httpRegistry = null;
    
    // Size for rendered images
    private final static int BMP_WIDTH = 800;
    private final static int BMP_HEIGHT = 300;
    
    // Homepage configuration
    private int plotDays = 5;

    // URLs:
    // log
    // sensors
    // sensors/csv
    // sensors?last=tstamp
    // switches/query
    // switches/toggle?id=X&cmd=[on|off]
    // doors
    // img/[drawable name|poolplot|thermoplot]
    // pushreg?key=X
	private static final String ALL_PATTERN = "*";
	private static final String LOG_PATTERN = "/log";
	private static final String SENSORS_PATTERN = "/sensors";
	private static final String SENSORS_CSV_PATTERN = "/sensors/csv";
	private static final String SWITCHES_PATTERN = "/switches*";
	private static final String DOORS_PATTERN = "/doors";
	private static final String IMAGES_PATTERN = "/img/*";
	private static final String PUSHREG_PATTERN = "/pushreg*";
	private static final String CONFIG_PATTERN = "/config*";

	public ServerThread(RDService service) {
		this.service = service;
		prefs = PreferenceManager.getDefaultSharedPreferences(service);
		
		plotDays = Integer.parseInt(prefs.getString("plot_limit", Preferences.DEFAULT_PLOTLIMIT));

        // HTTP objects
        httpContext = new BasicHttpContext();

        httpProc = new BasicHttpProcessor();
        httpProc.addInterceptor(new ResponseDate());
        httpProc.addInterceptor(new ResponseServer());
        httpProc.addInterceptor(new ResponseContent());
        httpProc.addInterceptor(new ResponseConnControl());

        httpService = new HttpService(httpProc, 
				  new DefaultConnectionReuseStrategy(),
				  new DefaultHttpResponseFactory());
        
        httpRegistry = new HttpRequestHandlerRegistry();
        httpRegistry.register(IMAGES_PATTERN, new ImagesHandler());
        httpRegistry.register(LOG_PATTERN, new ServiceLogHandler());
        httpRegistry.register(SENSORS_CSV_PATTERN, new SensorsCsvHandler());
        httpRegistry.register(SENSORS_PATTERN, new SensorsHandler());
        httpRegistry.register(SWITCHES_PATTERN, new SwitchesHandler());
        httpRegistry.register(DOORS_PATTERN, new DoorsHandler());
        httpRegistry.register(PUSHREG_PATTERN, new PushRegHandler());
        httpRegistry.register(CONFIG_PATTERN, new ConfigHandler());
        httpRegistry.register(ALL_PATTERN, new HomePageHandler());

        httpService.setHandlerResolver(httpRegistry);
	}
	
	public void start() {
		thread = new Thread(this);
		thread.start();
	}

	public synchronized void destroy() {
		runThread = false;
		Thread.yield();

    	if (serverSocket != null) {
    		try {
    			serverSocket.close();
    		} catch (java.io.IOException ignored) {}
    		serverSocket = null;
    	}
	}

	public void run() {
		int port = Integer.parseInt(prefs.getString("port", Preferences.DEFAULT_PORT));

		try {
			int counter = 10;
			while (counter > 0) {
				// Try until it works !
				try {
					serverSocket = new ServerSocket(port);
					break;
				} catch (java.io.IOException ignored) {
					try {
						Thread.sleep(3000);
					} catch (java.lang.InterruptedException e) {}
				}
				counter = counter - 1;
			}

			if (counter == 0) {
				service.addLog("Erreur serveur: impossible d'ouvrir le socket", RDService.LogLevel.HIGH);
			    Log.e(TAG, "IO Error for server: Failed to create socket");
			    service.errorLeds();
			    return;
			} else {
				Log.d(TAG, "Server Thread starting on port " + port);
				service.addLog("Ecoute clients sur le port " + port);
			}

			runThread = true;
            while (runThread){
            	try {
            		Socket socket = serverSocket.accept();

                    DefaultHttpServerConnection serverConnection = new DefaultHttpServerConnection();
                    serverConnection.bind(socket, new BasicHttpParams());

                    httpService.handleRequest(serverConnection, httpContext);
                            
                    serverConnection.shutdown();
            	} catch (org.apache.http.ConnectionClosedException ignored) {
            	} catch (java.net.SocketException ignored) {
            	} catch (java.io.IOException e) {
            		Log.e(TAG, "IO Error for server: " + e);
            		service.addLog("Erreur IO serveur: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
            		service.errorLeds();
            		break;
            	} catch (org.apache.http.HttpException e) {
            		Log.e(TAG, "HTTP Error for server: " + e);
            		service.addLog("Erreur HTTP serveur: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
            		service.errorLeds();
            		break;
            	}
            }
		} finally {
            if (serverSocket != null) {
            	try {
            		serverSocket.close();
            	} catch (java.io.IOException ignored) {}
            	serverSocket = null;
            }
		}
		
	} // end run

	class HomePageHandler implements HttpRequestHandler {

		public HomePageHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (défaut)");
			Log.d(TAG, "Got a HTTP request for server (default)");

			String contentType = "text/html";
			HttpEntity entity = new EntityTemplate(new ContentProducer() {
				public void writeTo(final OutputStream outstream) throws java.io.IOException {
					OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
					try {
			            InputStream is = service.getResources().openRawResource(R.raw.homepage);
			            int size = is.available();
			            byte[] buffer = new byte[size];
			            is.read(buffer);
			            is.close();
			            
			            // Pool
			            String poolTemp = "?";
			            XYSeries series = service.getSensors().getData(Sensors.ID_POOL_T);
			            if ((series != null) && (series.size() > 0)) {
			            	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
			                decimalFormat.applyPattern("#0.0#");
			                float lastValue = series.getY(series.size()-1).floatValue();
			            	poolTemp = decimalFormat.format(lastValue);
			            }

			            // Ext
			            String extTemp = "?";
			            series = service.getSensors().getData(Sensors.ID_EXT_T);
			            if ((series != null) && (series.size() > 0)) {
			            	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
			                decimalFormat.applyPattern("#0.0#");
			                float lastValue = series.getY(series.size()-1).floatValue();
			                extTemp = decimalFormat.format(lastValue);
			            }
			            String extHumidity = "?";
			            series = service.getSensors().getData(Sensors.ID_EXT_H);
			            if ((series != null) && (series.size() > 0)) {
			            	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
			                decimalFormat.applyPattern("##");
			                float lastValue = series.getY(series.size()-1).floatValue();
			                extHumidity = decimalFormat.format(lastValue);
			            }

			            // Veranda
			            String verandaTemp = "?";
			            series = service.getSensors().getData(Sensors.ID_VERANDA_T);
			            if ((series != null) && (series.size() > 0)) {
			            	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
			                decimalFormat.applyPattern("#0.0#");
			                float lastValue = series.getY(series.size()-1).floatValue();
			                verandaTemp = decimalFormat.format(lastValue);
			            }
			            String verandaHumidity = "?";
			            series = service.getSensors().getData(Sensors.ID_VERANDA_H);
			            if ((series != null) && (series.size() > 0)) {
			            	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
			                decimalFormat.applyPattern("##");
			                float lastValue = series.getY(series.size()-1).floatValue();
			                verandaHumidity = decimalFormat.format(lastValue);
			            }

			            String lastUpdate;
			            if (service.getSensors().getLastUpdate() != null) {
			            	lastUpdate = service.getSensors().getLastUpdate().toLocaleString();
			            } else {
			            	lastUpdate = "?";
			            }
			            String html = String.format(new String(buffer),
			            							poolTemp,
			            							extTemp,
			            							verandaTemp,
			            							extHumidity,
			            							verandaHumidity,
			            							lastUpdate);

			            writer.append(html);
			            writer.flush();
			        } catch (java.io.IOException e) {
			            Log.e(TAG, "Impossible to read homepage asset");
			        }				
				}
			});

			((EntityTemplate)entity).setContentType(contentType);

			response.setEntity(entity);
		}
	}

	class SensorsHandler implements HttpRequestHandler {

		public SensorsHandler(){
			// Do nothing
		}

		private long parseLastTstamp(String Uri) {
			Pattern pattern = Pattern.compile("/sensors\\?last=(\\d+)");
			Matcher matcher = pattern.matcher(Uri);
			if (matcher.matches()) {
				return Long.parseLong(matcher.group(1));
			} else {
				return 0;
			}
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (capteurs)");
			Log.d(TAG, "Got a HTTP request for server (sensors)");

			String Uri = request.getRequestLine().getUri();
			final long lastTstamp = parseLastTstamp(Uri);

			String contentType = "text/html";
			HttpEntity entity = new EntityTemplate(new ContentProducer() {
				public void writeTo(final OutputStream outstream) throws java.io.IOException {
					OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
					service.getSensors().dumpData(writer, lastTstamp);
					writer.flush();
				}
			});

			((EntityTemplate)entity).setContentType(contentType);

			response.setEntity(entity);
		}
	}
	
	class SensorsCsvHandler implements HttpRequestHandler {

		public SensorsCsvHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (capteurs-csv)");
			Log.d(TAG, "Got a HTTP request for server (sensors-csv)");

			String contentType = "text/html";
			HttpEntity entity = new EntityTemplate(new ContentProducer() {
				public void writeTo(final OutputStream outstream) throws java.io.IOException {
					OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
					service.getSensors().dumpCSV(writer);
					writer.flush();
				}
			});

			((EntityTemplate)entity).setContentType(contentType);

			response.setEntity(entity);
		}
	}
	
	class ServiceLogHandler implements HttpRequestHandler {

		public ServiceLogHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (log)");
			Log.d(TAG, "Got a HTTP request for server (log)");

			String contentType = "text/html";
			HttpEntity entity = new EntityTemplate(new ContentProducer() {
				public void writeTo(final OutputStream outstream) throws java.io.IOException {
					OutputStreamWriter writer = new OutputStreamWriter(outstream, "ISO8859_1");
					writer.write("<html><body bgcolor=\"black\">\n<p><font color=\"white\">Log:</font></p>\n<pre>\n");
					writer.write(Html.toHtml(service.getLogMessages()));
					writer.write("\n</pre>\n</body></html>");
					writer.flush();
				}
			});

			((EntityTemplate)entity).setContentType(contentType);

			response.setEntity(entity);
		}
	}
	
	class SwitchesHandler implements HttpRequestHandler {

		public SwitchesHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (switches)");
			Log.d(TAG, "Got a HTTP request for server (switches)");

			String contentType = "text/html";
			String Uri = request.getRequestLine().getUri();
			if ("/switches/query".equals(Uri)) {
				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
						JSONArray json = service.getSwitches().getJSONArray();
						writer.write(json.toString());
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);

				response.setEntity(entity);
			} else if (Uri.startsWith("/switches/toggle")) {

				Pattern pattern = Pattern.compile("/switches/toggle\\?id=(\\d+)&cmd=(on|off)");
				Matcher matcher = pattern.matcher(Uri);
				if (matcher.matches()) {
					String index = matcher.group(1);
					String cmd = matcher.group(2);

					boolean state = "on".equals(cmd);
					result = service.getSwitches().setState(Integer.parseInt(index), state);
				} else {
					Log.e(TAG, "Malformed URL: " + Uri);
					service.addLog("Requete erronée reçue: " + Uri, RDService.LogLevel.HIGH);
				}

				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
						if (result) {
							writer.write("OK");
						} else {
							writer.write("Bad parameter");
						}
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);
				response.setEntity(entity);
			} else {
				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
						writer.write("ERROR: missing parameters");
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);

				response.setEntity(entity);
			}
		}
	}
	
	class ImagesHandler implements HttpRequestHandler {

		public ImagesHandler(){
			// Do nothing
		}

		private Bitmap getBitmap(String name, RDService service) {
			if ("poolplot".equals(name)) {
				return getPoolBitmap(service);
	    	} else if ("thermoplot".equals(name)) {
	    		return getThermoBitmap(service);
	    	} else {
	    		// Try with drawable resources
	    		int id = service.getResources().getIdentifier(name, "drawable", service.getPackageName());
	    		if (id != 0) {
	    			BitmapDrawable bd = (BitmapDrawable) service.getResources().getDrawable(id);
	    			return bd.getBitmap();
	    		} else {
	    			Bitmap bitmap = Bitmap.createBitmap(BMP_WIDTH, BMP_HEIGHT, Bitmap.Config.ARGB_8888);
	    			bitmap.eraseColor(Color.BLACK);
	    			return bitmap;
	    		}
	    	}
		}

		private Bitmap getPoolBitmap(RDService service) {
			SensorPlot plot = new SensorPlot(service, "Fake-pool");
			SensorData data = service.getSensors().getData(Sensors.ID_POOL_T);
			plot.addSeries(data, plotDays);
			return plot.getBitmap(BMP_WIDTH, BMP_HEIGHT);
		}
		
		private Bitmap getThermoBitmap(RDService service) {
			SensorPlot plot = new SensorPlot(service, "Fake-thermo");
			SensorData data = service.getSensors().getData(Sensors.ID_POOL_T);
			plot.addSeries(data, plotDays);
			data = service.getSensors().getData(Sensors.ID_EXT_T);
			plot.addSeries(data, plotDays);
			data = service.getSensors().getData(Sensors.ID_VERANDA_T);
			plot.addSeries(data, plotDays);
			return plot.getBitmap(BMP_WIDTH, BMP_HEIGHT);
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (img)");
			Log.d(TAG, "Got a HTTP request for server (img)");

			HttpEntity entity;
			String contentType;

			String Uri = request.getRequestLine().getUri();
			Pattern pattern = Pattern.compile("/img/(.*)");
			Matcher matcher = pattern.matcher(Uri);
			if (matcher.matches()) {
				String imgName = matcher.group(1);
				contentType = "image/png";
				
				final Bitmap bitmap = getBitmap(imgName, service);
				entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						bitmap.compress(CompressFormat.PNG, 95, outstream);
					}
				});
			} else {
				contentType = "text/html";
				entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "ISO8859_1");
						writer.write("<html><body><p>404: Not Found</p></body></html>");
						writer.flush();
					}
				});
			}

			((EntityTemplate)entity).setContentType(contentType);

			response.setEntity(entity);
		}
	}
	
	class DoorsHandler implements HttpRequestHandler {

		public DoorsHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (portes)");
			Log.d(TAG, "Got a HTTP request for server (doors)");

			String contentType = "text/html";
			HttpEntity entity = new EntityTemplate(new ContentProducer() {
				public void writeTo(final OutputStream outstream) throws java.io.IOException {
					OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
					JSONArray array = service.getDoors().getJSONArray();
					writer.write(array.toString());
					writer.flush();
				}
			});

			((EntityTemplate)entity).setContentType(contentType);

			response.setEntity(entity);
		}
	}
	
	class PushRegHandler implements HttpRequestHandler {

		public PushRegHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (push-register)");
			Log.d(TAG, "Got a HTTP request for server (push-register)");

			String contentType = "text/html";
			String Uri = request.getRequestLine().getUri();

			Pattern pattern = Pattern.compile("/pushreg\\?key=(.+)");
			Matcher matcher = pattern.matcher(Uri);
			if (matcher.matches()) {
				String key = matcher.group(1);
				service.addPushDevice(key);

				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
						writer.write("OK");
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);
				response.setEntity(entity);
			} else {
				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
						writer.write("ERROR: missing parameters");
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);

				response.setEntity(entity);
			}
		}
	}
	
	class ConfigHandler implements HttpRequestHandler {

		public ConfigHandler(){
			// Do nothing
		}

		public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws org.apache.http.HttpException, java.io.IOException {
			service.addLog("Requete HTTP reçue (config)");
			Log.d(TAG, "Got a HTTP request for server (config)");

			String contentType = "text/html";
			String Uri = request.getRequestLine().getUri();

			Pattern pattern = Pattern.compile("/config\\?days=(\\d+)");
			Matcher matcher = pattern.matcher(Uri);
			if (matcher.matches()) {
				plotDays = Integer.parseInt(matcher.group(1));

				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {

						InputStream is = service.getResources().openRawResource(R.raw.reload);
			            int size = is.available();
			            byte[] buffer = new byte[size];
			            is.read(buffer);
			            is.close();
			            
			            OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
			            writer.write(new String(buffer));
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);
				response.setEntity(entity);
			} else {
				HttpEntity entity = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws java.io.IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
						writer.write("ERROR: missing parameters");
						writer.flush();
					}
				});
				((EntityTemplate)entity).setContentType(contentType);

				response.setEntity(entity);
			}
		}
	}
}