function SceneThermo(options) {
   this.options = options;

}

SceneThermo.prototype.initialize = function () {
	alert("SceneThermo.initialize()");
	// this function will be called only once when the scene manager show this scene first time
	// initialize the scene controls and styles, and initialize your variables here 
	// scene HTML and CSS will be loaded before this function is called

}

SceneThermo.prototype.handleShow = function () {
	graph = document.getElementById('GraphThermo');
	graph.style.backgroundImage = "url(http://remidomo.hd.free.fr:2012/img/thermoplot?dummy=" + new Date().getTime() + ")";
}

SceneThermo.prototype.handleHide = function () {
	alert("SceneThermo.handleHide()");
	// this function will be called when the scene manager hide this scene  
}

SceneThermo.prototype.handleFocus = function () {
	alert("SceneThermo.handleFocus()");
	// this function will be called when the scene manager focus this scene
}

SceneThermo.prototype.handleBlur = function () {
	alert("SceneThermo.handleBlur()");
	// this function will be called when the scene manager move focus to another scene from this scene
}

SceneThermo.prototype.handleKeyDown = function () {
	var keyCode = event.keyCode;
	alert("SceneThermo.handleKeyDown(" + keyCode + ")");

	switch (keyCode) {
		case $.sfKey.RETURN:
			$.sfScene.hide('Thermo');
			$.sfScene.focus('ActionBar');
			widgetAPI.blockNavigation(event);
			break;

		default:
			// Do nothing
			break;
	}
}
