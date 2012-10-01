function SceneActionBar(options) {
   this.options = options;

   var index;
   var selection;
}

SceneActionBar.prototype.initialize = function () {
	alert("SceneActionBar.initialize()");
	// this function will be called only once when the scene manager show this scene first time
	// initialize the scene controls and styles, and initialize your variables here 
	// scene HTML and CSS will be loaded before this function is called

	$('#Button_Dashboard').sfButton({text:'', width:'100px'});
	$('#Button_Thermo').sfButton({text:'', width:'40px'});
	$('#Button_Pool').sfButton({text:'', width:'40px'});
	$('#Button_Elec').sfButton({text:'', width:'40px'});
	$('#Button_Exit').sfButton({text:'', width:'40px'});
 
    index = new Array('#Button_Dashboard', '#Button_Thermo',
					  '#Button_Pool', '#Button_Elec', '#Button_Exit');

    this.selection = 0;
}

SceneActionBar.prototype.handleShow = function () {
	alert("SceneActionBar.handleShow()");
    $('#Button_Dashboard').sfButton('focus');       // get focus
	// this function will be called when the scene manager show this scene 
}

SceneActionBar.prototype.handleHide = function () {
	alert("SceneActionBar.handleHide()");
	// this function will be called when the scene manager hide this scene  
}

SceneActionBar.prototype.handleFocus = function () {
	alert("SceneActionBar.handleFocus()");
	// this function will be called when the scene manager focus this scene
}

SceneActionBar.prototype.handleBlur = function () {
	alert("SceneActionBar.handleBlur()");
	// this function will be called when the scene manager move focus to another scene from this scene
}

SceneActionBar.prototype.handleKeyDown = function (keyCode) {
	alert("SceneActionBar.handleKeyDown(" + keyCode + ")");
	switch (keyCode) {
		case $.sfKey.LEFT:
			$(index[this.selection]).sfButton('blur');
			if (this.selection > 0) {
				this.selection = this.selection - 1;
			}
			$(index[this.selection]).sfButton('focus');
			break;

		case $.sfKey.RIGHT:
            $(index[this.selection]).sfButton('blur');
            if (this.selection < $(index).length-1) {
				this.selection = this.selection + 1;
			}
            $(index[this.selection]).sfButton('focus');
			break;

		case $.sfKey.ENTER:
            if (this.selection == 0) {
              alert('dashboard');
            } else if (this.selection == 1) {
			  $.sfScene.show('Thermo');
			  $.sfScene.focus('Thermo');
            } else if (this.selection == 2) {
              $.sfScene.show('Pool');
			  $.sfScene.focus('Pool');
            } else if (this.selection == 3) {
              $.sfScene.show('Energy');
			  $.sfScene.focus('Energy');
			} else if (this.selection == 4) {
			  $.sfScene.hide('ActionBar');
			  /* Cascade Return ! */
			  widgetAPI.sendReturnEvent();
			}
			break;
			
		case $.sfKey.RETURN:
			$.sfScene.hide('ActionBar');
			widgetAPI.sendExitEvent();
			break;

		default:
			// Do nothing
			break;
	}
}
