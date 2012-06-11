package com.remi.remidomo;

import java.util.TimerTask;

public class ClientTask extends TimerTask {
	
	private RDService service;
    
	public ClientTask(RDService service) {
		this.service = service;
	}

	@Override
	public void run() {
		if (service.callback != null) {
			service.callback.startRefreshAnim();
		}

		service.getSwitches().syncWithServer();
		service.getSensors().syncWithServer();
		service.getDoors().syncWithServer();

		if (service.callback != null) {
			service.callback.stopRefreshAnim();
		}
	} // end run
}
