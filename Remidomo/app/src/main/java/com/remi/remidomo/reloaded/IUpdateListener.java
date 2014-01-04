package com.remi.remidomo.reloaded;

public interface IUpdateListener {
	void updateLog();
	void updateTrains();
	void updateMeteo();
	void updateThermo();
	void updateSwitches();
	void updateDoors();
	void updateEnergy();
	void resetLeds();
	void errorLeds();
	void blinkLeds();
	void flashLeds();
	void startRefreshAnim();
	void stopRefreshAnim();
	void postToast(String text);
}