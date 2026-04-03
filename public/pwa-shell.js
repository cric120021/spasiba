(function () {
  var SPLASH_SESSION_KEY = "spasiba_splash_seen_v1";

  function applyShellTheme() {
    document.documentElement.style.background = "#000";

    if (document.body) {
      document.body.style.background = "#000";

      try {
        if (window.sessionStorage.getItem(SPLASH_SESSION_KEY) === "1") {
          document.body.classList.add("app-ready");
        }
      } catch (_error) {
        // Ignore storage issues; boot should stay resilient.
      }
    }
  }

  applyShellTheme();

  if (!document.body) {
    document.addEventListener("DOMContentLoaded", applyShellTheme, { once: true });
  }
})();
