const navToggle = document.getElementById("navToggle");
const sideNav = document.getElementById("sideNav");
const SECTION_IDS = ["intro", "section0", "lossless", "lossless-optional", "lossy", "filetypes", "final"];
const MAIN_FLOW_SECTION_IDS = ["intro", "section0", "lossless", "lossy", "filetypes", "final"];

function getMainFlowNeighbor(sectionId, direction) {
  const index = MAIN_FLOW_SECTION_IDS.indexOf(sectionId);
  if (index === -1) {
    return null;
  }
  const neighborIndex = direction === "next" ? index + 1 : index - 1;
  return MAIN_FLOW_SECTION_IDS[neighborIndex] || null;
}

function createSectionNavButton(label, targetId, extraClass) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "section-nav-btn" + (extraClass ? " " + extraClass : "");
  button.textContent = label;
  button.addEventListener("click", () => showSection(targetId));
  return button;
}

function setupSectionFooters() {
  const lang = window.LANG_DE || {};
  const prevLabel = getLangValue(lang, "nav.prev") || "";
  const nextLabel = getLangValue(lang, "nav.next") || "";
  const backToLosslessLabel = getLangValue(lang, "nav.backToLossless") || "";
  const sectionNavAriaLabel = getLangValue(lang, "nav.sectionNavAriaLabel") || "";

  document.querySelectorAll(".panel").forEach((panel) => {
    const sectionId = panel.id;
    if (!SECTION_IDS.includes(sectionId)) {
      return;
    }

    const nav = document.createElement("nav");
    nav.className = "section-footer-nav";
    nav.setAttribute("aria-label", sectionNavAriaLabel);

    if (sectionId === "lossless-optional") {
      nav.classList.add("section-footer-nav--single");
      nav.appendChild(createSectionNavButton(backToLosslessLabel, "lossless"));
    } else {
      const prevId = getMainFlowNeighbor(sectionId, "prev");
      const nextId = getMainFlowNeighbor(sectionId, "next");

      if (prevId) {
        nav.appendChild(createSectionNavButton(prevLabel, prevId, "section-nav-btn--prev"));
      }
      if (nextId) {
        nav.appendChild(createSectionNavButton(nextLabel, nextId, "section-nav-btn--next"));
      }
    }

    if (nav.childElementCount > 0) {
      panel.appendChild(nav);
    }
  });
}

function showSection(sectionId) {
  const targetId = SECTION_IDS.includes(sectionId) ? sectionId : "intro";

  document.querySelectorAll(".panel").forEach((panel) => {
    const isActive = panel.id === targetId;
    panel.classList.toggle("is-active", isActive);
    panel.toggleAttribute("hidden", !isActive);
  });

  document.querySelectorAll(".nav-link").forEach((link) => {
    const linkId = link.getAttribute("href")?.slice(1) || "";
    const isActive = linkId === targetId;
    link.classList.toggle("is-active", isActive);
    if (isActive) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  });

  if (location.hash !== `#${targetId}`) {
    history.replaceState(null, "", `#${targetId}`);
  }

  window.scrollTo(0, 0);

  window.requestAnimationFrame(function() {
    window.dispatchEvent(new Event("resize"));
  });
}

function setupSectionViews() {
  setupSectionFooters();

  document.querySelectorAll(".nav-link").forEach((link) => {
    link.addEventListener("click", (event) => {
      event.preventDefault();
      const sectionId = link.getAttribute("href")?.slice(1);
      if (!sectionId) {
        return;
      }
      showSection(sectionId);
      if (window.innerWidth < 960) {
        document.body.classList.remove("nav-open");
      }
    });
  });

  window.addEventListener("hashchange", () => {
    showSection(location.hash.slice(1));
  });

  showSection(location.hash.slice(1) || "intro");
}

function setupNavigation() {
  if (!navToggle || !sideNav) {
    return;
  }

  navToggle.addEventListener("click", () => {
    document.body.classList.toggle("nav-open");
  });
}
