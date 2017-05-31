function PassportScannerPlugin() {
}

PassportScannerPlugin.prototype = {

  available: function (callback) {
    cordova.exec(function (avail) {
      callback(avail ? true : false);
    }, function() { callback(false); }, "PassportScannerPlugin", "available", []);
  },

//  findDevices: function (callback) {
//    cordova.exec(function (found) {
//      callback(found ? true : false);
//    }, function() { callback(false); }, "PassportScannerPlugin", "findDevices", []);
//  },

//  findDevices: function (callback) {
//    cordova.exec(function (passportScannerString) {
//      callback(passportScannerString);
//    }, function() { callback(passportScannerString); }, "PassportScannerPlugin", "findDevices", []);
//  },

//  findDevices: function (successCallback, errorCallback, options) {
//    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "findDevices", []);
//  },

  findDevices: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "findDevices");
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
