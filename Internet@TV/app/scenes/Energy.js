function SceneEnergy(options) {
   this.options = options;

}

SceneEnergy.prototype.initialize = function () {
	alert("SceneEnergy.initialize()");
	// this function will be called only once when the scene manager show this scene first time
	// initialize the scene controls and styles, and initialize your variables here 
	// scene HTML and CSS will be loaded before this function is called

}

SceneEnergy.prototype.handleShow = function () {
	graph = document.getElementById('GraphEnergy');
	graph.style.backgroundImage = "url(http://remidomo.hd.free.fr:2012/img/powerplot?dummy=" + new Date().getTime() + ")";
}

SceneEnergy.prototype.handleHide = function () {
	alert("SceneEnergy.handleHide()");
	// this function will be called when the scene manager hide this scene  
}

SceneEnergy.prototype.handleFocus = function () {
	alert("SceneEnergy.handleFocus()");
	// this function will be called when the scene manager focus this scene
}

SceneEnergy.prototype.handleBlur = function () {
	alert("SceneEnergy.handleBlur()");
	// this function will be called when the scene manager move focus to another scene from this scene
}

SceneEnergy.prototype.handleKeyDown = function () {
	var keyCode = event.keyCode;
	alert("SceneEnergy.handleKeyDown(" + keyCode + ")");

	switch (keyCode) {
		case $.sfKey.RETURN:
			$.sfScene.hide('Energy');
			$.sfScene.focus('ActionBar');
			widgetAPI.blockNavigation(event);
			break;

		default:
			// Do nothing
			break;
	}
}
