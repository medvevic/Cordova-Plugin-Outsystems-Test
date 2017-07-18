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

//  isDeviceFound: function (successCallback, errorCallback) {
//    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "isDeviceFound");
//  },

    available: function (callback) {
        cordova.exec(function (avail) {
          callback(avail ? true : false);
        }, function() { callback(false); }, "PassportScannerPlugin", "available", []);
    },

    isDeviceFound: function (callback) {
        cordova.exec(function (devFound) {
            callback(devFound ? true : false);
        }, function() { callback(false); }, "PassportScannerPlugin", "isDeviceFound", []);
    },

    keepAwake: function (successCallback, errorCallback) {
        cordova.exec(function (keepUnSleep) {
          callback(keepUnSleep ? true : false);
        }, function() { callback(false); }, "PassportScannerPlugin", "keepAwake", []);

        //cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "keepAwake");
    },

    allowSleepAgain: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "allowSleepAgain");
    },

    readPassport: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "readPassport");
    },

    getPassportData: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "getPassportData");
    },

//  isPassportInSlot: function (callback) {
//    cordova.exec(function (devFound) {
//      callback(devFound ? true : false);
//    }, function() { callback(false); }, "PassportScannerPlugin", "isPassportInSlot", []);
//  },

};

PassportScannerPlugin.install = function () {
  if (!window.plugins) {
    window.plugins = {};
  }

  window.plugins.PassportScannerPlugin = new PassportScannerPlugin();
  return window.plugins.PassportScannerPlugin;
};

cordova.addConstructor(PassportScannerPlugin.install);
