package com.remi.remidomo;

public interface IUpdateListener {
	void updateLog();
	void updateTrains();
	void updateMeteo();
	void updateThermo();
	void updateSwitches();
	void updateDoors();
	void resetLeds();
	void errorLeds();
	void blinkLeds();
	void flashLeds();
	void startRefreshAnim();
	void stopRefreshAnim();
}