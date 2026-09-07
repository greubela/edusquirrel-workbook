function setupStepSlideshow(containerId, steps, labels) {
  const container = document.getElementById(containerId);
  if (!container || !steps || steps.length === 0) { return null; }

  const t = labels || {};
  let index = 0;

  container.className = (container.className + " step-slideshow").trim();
  container.innerHTML =
    '<div class="step-slideshow-stage" id="' + containerId + '-stage"></div>' +
    '<p class="step-slideshow-caption" id="' + containerId + '-caption"></p>' +
    '<div class="step-slideshow-controls">' +
      '<button type="button" class="step-slideshow-prev">' + (t.prev || "") + '</button>' +
      '<span class="step-slideshow-counter" id="' + containerId + '-counter"></span>' +
      '<button type="button" class="step-slideshow-next">' + (t.next || "") + '</button>' +
    '</div>';

  const stage = document.getElementById(containerId + "-stage");
  const caption = document.getElementById(containerId + "-caption");
  const counter = document.getElementById(containerId + "-counter");
  const prevBtn = container.querySelector(".step-slideshow-prev");
  const nextBtn = container.querySelector(".step-slideshow-next");

  function render() {
    const step = steps[index];
    if (step.html) {
      stage.innerHTML = step.html;
    } else if (step.src) {
      stage.innerHTML = '<img src="' + step.src + '" alt="' + (step.alt || "") + '" class="step-slideshow-img">';
    } else if (step.canvas) {
      stage.innerHTML = "";
      stage.appendChild(step.canvas);
    }
    caption.textContent = step.caption || "";
    counter.textContent = (index + 1) + " / " + steps.length;
    prevBtn.disabled = index === 0;
    nextBtn.disabled = index === steps.length - 1;
  }

  prevBtn.addEventListener("click", function() {
    if (index > 0) { index--; render(); }
  });
  nextBtn.addEventListener("click", function() {
    if (index < steps.length - 1) { index++; render(); }
  });

  render();

  return {
    goTo: function(i) {
      index = Math.max(0, Math.min(steps.length - 1, i));
      render();
    }
  };
}
