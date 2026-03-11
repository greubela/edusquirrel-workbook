(function () {
  var cacheBust = "v=" + Date.now();
  var origin = window.location.origin || "";
  var paths = [
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
    script.defer = true;
    script.src = paths[index] + "?" + cacheBust;
    script.onerror = function () {
      loadAt(index + 1);
    };
    document.head.appendChild(script);
  }

  function startLoading() {
    loadAt(0);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", startLoading);
  } else {
    startLoading();
  }
})();
