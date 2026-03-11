/* Theme toggle , dark / light mode */
(function () {
  try {
    var saved = localStorage.getItem('fd-theme');
    if (saved === 'light' || saved === 'dark') {
      document.documentElement.setAttribute('data-theme', saved);
    }
  } catch (e) {}
})();

document.addEventListener('DOMContentLoaded', function () {
  var root = document.documentElement;
  var btn  = document.getElementById('fdThemeToggle');
  if (!btn) return;

  btn.addEventListener('click', function () {
    var current = root.getAttribute('data-theme') || 'dark';
    var next    = current === 'light' ? 'dark' : 'light';

    document.body.classList.add('fd-theme-switching');
    root.setAttribute('data-theme', next);
    try { localStorage.setItem('fd-theme', next); } catch (e) {}

    setTimeout(function () {
      document.body.classList.remove('fd-theme-switching');
    }, 380);
  });
});
