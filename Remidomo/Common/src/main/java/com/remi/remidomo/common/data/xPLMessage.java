package com.remi.remidomo.common.data;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class xPLMessage {
	
	private final static String TAG = "Remidomo-Common";
	
	public enum MessageType { UNKNOWN, STATUS, COMMAND, TRIGGER };
	
	public int hopCount = 0;
	private MessageType msgType = MessageType.UNKNOWN;
	private String schemaClass = "";
	private String schemaType = "";
	private String msgSource = null;
	private String msgTarget = null;
	private ArrayList<NamedValue> namedValues = new ArrayList<NamedValue>();

	public static class NamedValue {
		private String theName;
		private String theValue;
		
		public NamedValue(String name, String value) {
			theName = name;
			theValue = value;
		}
		
		public String getName() { return theName; }
		public String getVal() { return theValue; }
		public int getInt() { return Integer.parseInt(theValue); }
		public float getFloat() { return Float.parseFloat(theValue); }
		
		public String toString() {
			return "(" + theName + "," + theValue + ")";
		}
	}

	public static class xPLParseException extends Exception {
		static final long serialVersionUID=0;

		public xPLParseException() {
			super("Error occurred parsing an XPL message");
		}

		public xPLParseException(String message)  {
			super(message);
		}
	}

	public xPLMessage(String textMessage) throws xPLParseException {
	    if ((textMessage == null) || (textMessage.length() < 13)) {
	    	throw new xPLParseException("Message is empty or too short");
	    }
	    
	    String theToken = null;
	    String theValue = null;
	    int lineCount = 0;
	    
	    hopCount = 0;
	    
	    // Create line based tokenizer -- this makes our lives a bit easier
	    try {
	      StringTokenizer tokenizer = new StringTokenizer(textMessage, "\n");
	    
	      // See what kind of message this is
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (theToken.equalsIgnoreCase("xpl-cmnd")) 
	    	  msgType = MessageType.COMMAND;
	      else if (theToken.equalsIgnoreCase("xpl-trig"))
	    	  msgType = MessageType.TRIGGER;
	      else if (theToken.equalsIgnoreCase("xpl-stat"))
	    	  msgType = MessageType.STATUS;
	      else 
	        throw new xPLParseException("Unknown message header/type: " + theToken);
	      
	      // Next should be a header intro
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (!theToken.equals("{")) throw new xPLParseException("Missing header { after message type -- improperly formatted message");
	      
	      // Read in hop count
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (((theValue = parseNamedValue(theToken, "hop")) == null) || (theValue.length() == 0))
	        throw new xPLParseException("Missing/invalid hop= in message header");
	      
	      try {
	        hopCount = Integer.parseInt(theValue);
	      } catch (Exception anyError) {
	        throw new xPLParseException("Invalid/non-numeric hop count in message header");
	      }
	      
	      // Read in the source to the message 
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (((theValue = parseNamedValue(theToken, "source")) == null) || (theValue.length() == 0))
	        throw new xPLParseException("Missing/invalid source= in message header");
	      
	      // Lookup the source
	      if ((msgSource = theValue) == null) {
	        throw new xPLParseException("Invalid/improperly formatted source in message header");
	      }

	      // Read in the target to the message 
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (((theValue = parseNamedValue(theToken, "target")) == null) || (theValue.length() == 0))
	        throw new xPLParseException("Missing/invalid target= in message header");
	      
	      // Lookup the source
	      if ((msgTarget = theValue) == null) {
	        throw new xPLParseException("Invalid/improperly formatted target in message header");
	      }
	      
	      // Next should be a header close
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (!theToken.equals("}")) throw new xPLParseException("Missing header closing of } after header data -- improperly formatted message");

	      // Next should be the schema class/type
	      theToken = tokenizer.nextToken(); lineCount++;
	      int delimPtr = theToken.indexOf('.');
	      if (delimPtr == -1) throw new xPLParseException("Missing/improperly formatted message schema class/type in message");
	      if ((schemaClass = theToken.substring(0, delimPtr)).length() == 0) throw new xPLParseException("Empty/missing schema class in message");
	      if ((schemaType = theToken.substring(delimPtr + 1)).length() == 0) throw new xPLParseException("Empty/missing schema type in message");

	      // Next should be the message body start
	      theToken = tokenizer.nextToken(); lineCount++;
	      if (!theToken.equals("{")) throw new xPLParseException("Missing body section open { after header -- improperly formatted message");
	      
	      // Hand Name/Value parsing off 
	      lineCount = parseNameValuePairs(tokenizer, lineCount);
	    } catch (java.util.NoSuchElementException noMoreTokens) {
	      throw new xPLParseException("Prematurely ran out of message tokens/lines -- short/invalid message, " + lineCount + " lines read OK");
	    } catch (xPLParseException parseError) {
	      throw parseError;
	    } catch (Exception anyOtherException) {
	      throw new xPLParseException("Unexpected error parsing message -- " + anyOtherException.getMessage());
	    }
	}
	
	// Parse the passed string for the named value.  The name is expected to
	// start the string.  If the name does not start the string or does not
	// match, null is returned.  The name is case insensitive.  If there is
	// a match, the value is returned.  In the event there is no value 
	// (i.e. name=), an empty string (not null) is returned
	private String parseNamedValue(String theText, String theName) {
		// Get name/value delimiter
		int delimPtr = theText.indexOf('=');
		if (delimPtr == -1) return null;

		// Extract the string for direct testing
		String testName = theText.substring(0, delimPtr);

		// If there is no match, ignore this
		if (!testName.equalsIgnoreCase(theName)) return null;

		// Extract the value
		return theText.substring(delimPtr + 1);
	}
	
	private int parseNameValuePairs(StringTokenizer tokenizer, int lineCount) throws xPLParseException, java.util.NoSuchElementException {
	    String theToken = null, theName = null, theValue = null;
	    int delimPtr = 0;
	        
	    while (tokenizer.hasMoreTokens()) {
	      theToken = tokenizer.nextToken();  lineCount++;
	      
	      // Handle a terminal token
	      if (theToken.equals("}")) return lineCount;
	        
	      // Look for delimiter
	      if ((delimPtr = theToken.indexOf('=')) == -1) throw new xPLParseException("Empty/Missing name/value pair in message body on line " + lineCount);

	      // Break up parts
	      if ((theName = theToken.substring(0, delimPtr)).length() == 0) throw new xPLParseException("Missing name in name/value pair in message body on line " + lineCount);
	      theValue = theToken.substring(delimPtr + 1);
	    
	      // Add this to the list
	      namedValues.add(new NamedValue(theName, theValue));
	    }

	    return lineCount;
	  }
	
	public int getHopCount() { return hopCount; }	
	public MessageType getType() { return msgType; }
	public String getSchemaClass() { return schemaClass; }
	public String getSchemaType() { return schemaType; }	
	public String getSource() { return msgSource; }	
	public String getTarget() { return msgTarget; }
	
	public boolean hasNamedValue(String name) {
		for (int i=0; i<namedValues.size(); i++) {
			if (name.equals(namedValues.get(i).getName())) {
				return true;
			}
		}
		return false;
	}
	
	public String getNamedValue(String name) {
		if (hasNamedValue(name)) {
			for (int i=0; i<namedValues.size(); i++) {
				if (name.equals(namedValues.get(i).getName())) {
					return namedValues.get(i).theValue;
				}
			}
		}
		return null;
	}
	
	public int getIntNamedValue(String name) {
		for (int i=0; i<namedValues.size(); i++) {
			if (name.equals(namedValues.get(i).getName())) {
				return namedValues.get(i).getInt();
			}
		}
		return 0;
	}
	
	public float getFloatNamedValue(String name) {
		for (int i=0; i<namedValues.size(); i++) {
			if (name.equals(namedValues.get(i).getName())) {
				return namedValues.get(i).getFloat();
			}
		}
		return (float)0.0;
	}
}