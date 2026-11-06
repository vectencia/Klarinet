(function () {
  var launchers = config.customLaunchers || {};
  Object.keys(launchers).forEach(function (name) {
    var launcher = launchers[name];
    if (!launcher.flags) launcher.flags = [];
    launcher.flags.push("--autoplay-policy=no-user-gesture-required");
    launcher.flags.push("--use-fake-device-for-media-stream");
    launcher.flags.push("--use-fake-ui-for-media-stream");
  });
})();
