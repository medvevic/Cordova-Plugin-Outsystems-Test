function PassportScannerPlugin() {
  // track PassportScannerPlugin state
}

PassportScannerPlugin.prototype = {

  findDevices: function (successCallback, errorCallback, options) {
    var opts = options || {};
    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "findDevices", [opts]);
  },
};

PassportScannerPlugin.install = function () {
  if (!window.plugins) {
    window.plugins = {};
  }

  window.plugins.PassportScannerPlugin = new PassportScannerPlugin();
  return window.plugins.PassportScannerPlugin;
};

cordova.addConstructor(PassportScannerPlugin.install);
