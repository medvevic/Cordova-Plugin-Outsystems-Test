function PassportScannerPlugin() {
}

// Wait for device API libraries to load
function onLoad() {
    document.addEventListener("deviceready", onDeviceReady, false);
}

// device APIs are available
function onDeviceReady() {
    document.addEventListener("resume", onResume, false);
    // Add similar listeners for other events
}

function onResume() {
    // Handle the resume event
}

PassportScannerPlugin.prototype = {

  available: function (callback) {
    cordova.exec(function (avail) {
      callback(avail ? true : false);
    }, function() { callback(false); }, "PassportScannerPlugin", "available", []);
  },

  isDeviceFound: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "isDeviceFound");
  },

//  isDeviceFound: function (callback) {
//    cordova.exec(function (devFound) {
//      callback(devFound ? true : false);
//    }, function() { callback(false); }, "PassportScannerPlugin", "isDeviceFound", []);
//  },

  readPassport: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "readPassport");
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
