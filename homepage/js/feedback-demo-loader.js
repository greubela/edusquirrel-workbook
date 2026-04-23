(function () {
  var cacheBust = "v=" + Date.now();
  var origin = window.location.origin || "";
  var paths = [
    "../js/app/main.js",
    "./js/app/main.js",
    origin + "/js/app/main.js",
    origin + "/target/scala-3.3.3/workbookapp-fastopt/main.js",
    "../target/scala-3.3.3/workbookapp-fastopt/main.js",
    "/target/scala-3.3.3/workbookapp-fastopt/main.js",
    "./../target/scala-3.3.3/workbookapp-fastopt/main.js"
  ];

  function loadAt(index) {
    if (index >= paths.length) {
      return;
    }

    var script = document.createElement("script");
    script.type = "module";
    script.src = paths[index] + "?" + cacheBust;
    script.onerror = function () {
      loadAt(index + 1);
    };
    document.head.appendChild(script);
  }

  function waitForCodeMirror(maxMs) {
    var start = Date.now();
    return new Promise(function (resolve) {
      function tick() {
        if (window.EduSquirrelCodeMirrorReady && typeof window.EduSquirrelCodeMirrorReady.then === "function") {
          window.EduSquirrelCodeMirrorReady.then(resolve, resolve);
          return;
        }
        if (Date.now() - start > maxMs) { resolve(); return; }
        setTimeout(tick, 30);
      }
      tick();
    });
  }

  function startLoading() {
    waitForCodeMirror(8000).then(function () { loadAt(0); });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", startLoading);
  } else {
    startLoading();
  }
})();
