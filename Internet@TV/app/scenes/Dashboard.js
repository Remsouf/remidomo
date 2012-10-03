function SceneDashboard(options) {
   this.options = options;

}

SceneDashboard.prototype.initialize = function () {
	alert("SceneDashboard.initialize()");
	// this function will be called only once when the scene manager show this scene first time
	// initialize the scene controls and styles, and initialize your variables here 
	// scene HTML and CSS will be loaded before this function is called

}

SceneDashboard.prototype.handleShow = function () {
    poolTemp = "?";
	extTemp = "?";
	extHumidity = "?";
	verandaTemp = "?";
	verandaHumidity = "?";
	power = "?";
	energyCumul = "?";

	$.getJSON("http://remidomo.hd.free.fr:2012/dashboard", function(json) {
		/* Thermo */
		thermo = json.thermo;
		poolTemp = thermo.pool.temperature;
		extTemp = thermo.ext.temperature;
		extHumidity = thermo.ext.humidity;
		verandaTemp = thermo.veranda.temperature;
		verandaHumidity = thermo.veranda.humidity;

		document.getElementById('PoolTemp').innerHTML = poolTemp + "&deg;C";

		document.getElementById('ExtTemp').innerHTML = extTemp + "&deg;C";
		document.getElementById('ExtHumidity').innerHTML = extHumidity + "%";

		document.getElementById('VerandaTemp').innerHTML = verandaTemp + "&deg;C";
		document.getElementById('VerandaHumidity').innerHTML = verandaHumidity + "%";
		
		/* Energy */
		energyInfo = json.energy;
		power = energyInfo.power;
		energyCumul = energyInfo.energy;
		powerDiv = document.getElementById('Power');
		powerDiv.innerHTML = power + "kW";
		document.getElementById('Cumul').innerHTML = energyCumul + "kWh";
		tarif = energyInfo.tarif;
		if (tarif == "hc") {
			powerDiv.style.color = "#0000ff";
		} else if (tarif == "hp") {
			powerDiv.style.color = "#ff0000";
		} else {
			powerDiv.style.color = "#ffffff";
		}
	});
}

SceneDashboard.prototype.handleHide = function () {
	alert("SceneDashboard.handleHide()");
	// this function will be called when the scene manager hide this scene  
}

SceneDashboard.prototype.handleFocus = function () {
	alert("SceneDashboard.handleFocus()");
	// this function will be called when the scene manager focus this scene
}

SceneDashboard.prototype.handleBlur = function () {
	alert("SceneDashboard.handleBlur()");
	// this function will be called when the scene manager move focus to another scene from this scene
}

SceneDashboard.prototype.handleKeyDown = function () {
	var keyCode = event.keyCode;
	alert("SceneDashboard.handleKeyDown(" + keyCode + ")");

	switch (keyCode) {
		case $.sfKey.RETURN:
			$.sfScene.hide('Dashboard');
			$.sfScene.focus('ActionBar');
			widgetAPI.blockNavigation(event);
			break;

		default:
			// Do nothing
			break;
	}
}
