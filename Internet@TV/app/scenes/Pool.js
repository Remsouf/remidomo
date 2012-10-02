function ScenePool(options) {
   this.options = options;

}

ScenePool.prototype.initialize = function () {
	alert("ScenePool.initialize()");
	// this function will be called only once when the scene manager show this scene first time
	// initialize the scene controls and styles, and initialize your variables here 
	// scene HTML and CSS will be loaded before this function is called

}

ScenePool.prototype.handleShow = function () {
	graph = document.getElementById('GraphPool');
	graph.style.backgroundImage = "url(http://remidomo.hd.free.fr:2012/img/poolplot?dummy=" + new Date().getTime() + ")";
}

ScenePool.prototype.handleHide = function () {
	alert("ScenePool.handleHide()");
	// this function will be called when the scene manager hide this scene  
}

ScenePool.prototype.handleFocus = function () {
	alert("ScenePool.handleFocus()");
	// this function will be called when the scene manager focus this scene
}

ScenePool.prototype.handleBlur = function () {
	alert("ScenePool.handleBlur()");
	// this function will be called when the scene manager move focus to another scene from this scene
}

ScenePool.prototype.handleKeyDown = function () {
	var keyCode = event.keyCode;
	alert("ScenePool.handleKeyDown(" + keyCode + ")");

	switch (keyCode) {
		case $.sfKey.RETURN:
			$.sfScene.hide('Pool');
			$.sfScene.focus('ActionBar');
			widgetAPI.blockNavigation(event);
			break;

		default:
			// Do nothing
			break;
	}
}
