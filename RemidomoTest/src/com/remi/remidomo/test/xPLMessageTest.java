package com.remi.remidomo.test;

import com.remi.remidomo.reloaded.data.xPLMessage;

import android.test.AndroidTestCase;

public class xPLMessageTest extends AndroidTestCase {

	public void testEmpty() {
		try {
			new xPLMessage("");
			fail("Expected xPLParseException");
		} catch (xPLMessage.xPLParseException e) {
			assertTrue(true);
		}
	}
	
	public void testGarbage() {
		try {
			new xPLMessage("Blahblahblah");
			fail("Expected xPLParseException");
		} catch (xPLMessage.xPLParseException e) {
			assertTrue(true);
		}
	}
	
	public void testBadCommand() {
		try {
			String input = "xpl-cmn\n{\nhop=1\nsource=myhouse\ntarget=server\n}";
			new xPLMessage(input);
			fail("Expected xPLParseException");
		} catch (xPLMessage.xPLParseException e) {
			assertTrue(true);
		}
	}
	
	public void testCommandIncomplete() {
		try {
			String input = "xpl-cmnd\n{\nhop=1\nsource=myhouse\ntarget=server\n}";
			new xPLMessage(input);
			fail("Expected xPLParseException");
		} catch (xPLMessage.xPLParseException e) {
			assertTrue(true);
		}
	}
	
	public void testCommand() {
		try {
			String input = "xpl-cmnd\n{\nhop=1\nsource=myhouse\ntarget=server\n}\nx10.basic\n{\n}";
			xPLMessage msg = new xPLMessage(input);
			assertEquals(xPLMessage.MessageType.COMMAND, msg.getType());
			assertEquals(1, msg.getHopCount());
			assertEquals("myhouse", msg.getSource());
			assertEquals("server", msg.getTarget());
		} catch (xPLMessage.xPLParseException e) {
			fail("Unexpected xPLParseException " + e);
		}
	}
	
	public void testStat() {
		try {
			String input = "xpl-stat\n{\nhop=1\nsource=myhouse\ntarget=server\n}\nx10.basic\n{\n}";
			xPLMessage msg = new xPLMessage(input);
			assertEquals(xPLMessage.MessageType.STATUS, msg.getType());
			assertEquals(1, msg.getHopCount());
			assertEquals("myhouse", msg.getSource());
			assertEquals("server", msg.getTarget());
		} catch (xPLMessage.xPLParseException e) {
			fail("Unexpected xPLParseException " + e);
		}
	}
	
	public void testTrigger() {
		try {
			String input = "xpl-trig\n{\nhop=1\nsource=myhouse\ntarget=server\n}\nx10.basic\n{\n}";
			xPLMessage msg = new xPLMessage(input);
			assertEquals(xPLMessage.MessageType.TRIGGER, msg.getType());
			assertEquals(1, msg.getHopCount());
			assertEquals("myhouse", msg.getSource());
			assertEquals("server", msg.getTarget());
		} catch (xPLMessage.xPLParseException e) {
			fail("Unexpected xPLParseException " + e);
		}
	}
	
	public void testHop() {
		try {
			String input = "xpl-cmnd\n{\nhop=150\nsource=myhouse\ntarget=server\n}\nx10.basic\n{\n}";
			xPLMessage msg = new xPLMessage(input);
			assertEquals(xPLMessage.MessageType.COMMAND, msg.getType());
			assertEquals(150, msg.getHopCount());
			assertEquals("myhouse", msg.getSource());
			assertEquals("server", msg.getTarget());
		} catch (xPLMessage.xPLParseException e) {
			fail("Unexpected xPLParseException " + e);
		}
	}
	
	public void testCommandValues() {
		try {
			String input = "xpl-cmnd\n{\nhop=150\nsource=myhouse\ntarget=server\n}\nx10.basic\n{\ncommand=dim\ndevice=a1\nlevel=75\n}";
			xPLMessage msg = new xPLMessage(input);
			assertEquals(msg.getType(), xPLMessage.MessageType.COMMAND);
			assertEquals(msg.getHopCount(), 150);
			assertEquals("myhouse", msg.getSource());
			assertEquals("server", msg.getTarget());
			assertEquals("x10", msg.getSchemaClass());
			assertEquals("basic", msg.getSchemaType());
			assertTrue(msg.hasNamedValue("command"));
			assertEquals("dim", msg.getNamedValue("command"));
			assertTrue(msg.hasNamedValue("device"));
			assertEquals("a1", msg.getNamedValue("device"));
			assertTrue(msg.hasNamedValue("level"));
			assertEquals("75", msg.getNamedValue("level"));
			assertEquals(75, msg.getIntNamedValue("level"));
		} catch (xPLMessage.xPLParseException e) {
			fail("Unexpected xPLParseException " + e);
		}
	}
}
