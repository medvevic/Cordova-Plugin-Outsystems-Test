function PassportScannerPlugin() {
}

//exports.findDevices = function (successCallback, errorCallback) {
//    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "findDevices");
//};

PassportScannerPlugin.prototype = {

  hasUsbHostFeature: function (successCallback, errorCallback, options) {
    cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "hasUsbHostFeature", []);

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

    findDevices: function (successCallback, errorCallback, options) {
        cordova.exec(successCallback, errorCallback, "PassportScannerPlugin", "findDevices", []);
    },

    getDevices: function(options, callback) {
      cordova.exec(
          function(devices) {  // successCallback
            callback(devices);
          },
          function(msg) {  // errorCallback
            callbackWithError('Get devices failed: ' + msg, callback);
          },
          'PassportScannerPlugin',
          'getDevices',
          [options]
          );
    };

/*
    scan: function (params, success, failure)
    {
        argscheck.checkArgs('*fF', 'CsZBar.scan', arguments);

        params = params || {};
        if(params.text_title === undefined) params.text_title = "Scan QR Code";
        if(params.text_instructions === undefined) params.text_instructions = "Please point your camera at the QR code.";
        if(params.camera != "front") params.camera = "back";
        if(params.flash != "on" && params.flash != "off") params.flash = "auto";

        exec(success, failure, 'CsZBar', 'scan', [params]);
    },
*/



};

PassportScannerPlugin.install = function () {
  if (!window.plugins) {
    window.plugins = {};
  }

  window.plugins.PassportScannerPlugin = new PassportScannerPlugin();
  return window.plugins.PassportScannerPlugin;
};

cordova.addConstructor(PassportScannerPlugin.install);
