widgetAPI = new Common.API.Widget();
var tvKey = new Common.API.TVKeyValue();

var Main =
{

};

SetScreenRectTest = function() {
    var pluginsr = document.getElementById('pluginObjectWindow');
    var posmode = 0;
    var sizemode = 0;
   
    posmode = 7;  // PL_WINDOW_POSITION_MODE_CUSTOM
    sizemode = 6; // PL_WINDOW_RECT_SIZE_CUSTOM

    alert('POSMODE : ' + pluginsr.GetScreenRect_PosMode() + ', SIZEMODE : ' + pluginsr.GetScreenRect_SizeMode());
   
    pluginsr.SetScreenRect(0, 0, 960, 540); // custom area & custom size
    
    // Set Custom Mode
    pluginsr.SetScreenRect_PosSizeMode(posmode, sizemode);
	
	WindowPlugin.SetSource(PL_WINDOW_SOURCE_TV);
}

Main.onLoad = function()
{
	window.onShow = onShowEventHandler;
	widgetAPI.sendReadyEvent();
};

Main.onUnload = function()
{

};

onShowEventHandler = function() {
    SetScreenRectTest();
}