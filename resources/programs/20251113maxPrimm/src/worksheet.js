let worksheets = [];
let currentIndex = 0;
let currentSelectedLanguage = "python";
let currentSubtaskId = "";
let activePeriods = [];
let currentActivePeriod = {
  subtaskId: null,
  start: "",
  end: "",
  totalTime: 0,
};

async function loadWorksheets() {
  let tocHtml = "";
  if (localStorage.getItem("currentSelectedLanguage")) {
    currentSelectedLanguage = localStorage.getItem("currentSelectedLanguage");
  }
  if (localStorage.getItem("activePeriods")) {
    activePeriods = JSON.parse(localStorage.getItem("activePeriods"));
  }
  worksheets.forEach((ws, index) => {
    const status = localStorage.getItem(ws.file) === "done" ? "✔️" : "";
    tocHtml += `<li class="mb-2"><strong><a href="#" onclick="loadWorksheet(${index})">${ws.title} ${status}</a></strong><ul>`;
    ws.tasks.forEach((task, taskIndex) => {
      tocHtml += `<li><a class="link-secondary" href="#task${taskIndex}" onclick="loadWorksheet(${index})">${task.title}</a></li>`;
    });
    tocHtml += `</ul></li>`;
  });
  document.getElementById("sidebar-content").innerHTML = tocHtml;
  if (localStorage.getItem("currentWorksheet")) {
    loadWorksheet(parseInt(localStorage.getItem("currentWorksheet")));
  } else {
    loadWorksheet(0);
  }
}

// Load worksheet content
async function loadWorksheet(index) {
  localStorage.setItem("currentWorksheet", index);
  currentIndex = index;
  data = worksheets[index];

  if (showDebugLogs) console.log("Loading worksheet", index, data.title);

  // Worksheet header
  document.title = `Arbeitsblatt ${index + 1}: ${data.title}`;
  document.getElementById("worksheet-number").innerHTML = `Arbeitsblatt ${
    index + 1
  }`;
  document.getElementById(
    "worksheet-title"
  ).innerHTML = `<h1>${data.title}</h1>`;
  document.getElementById("worksheet-description").innerHTML = data.description;
  if (data.image) {
    document
      .querySelector("#worksheet-image-wrapper")
      .classList.remove("hide-element");
    document.getElementById(
      "worksheet-image"
    ).innerHTML = `<img src="${data.image}" class="figure-img img-fluid" alt="${data.imageAlt}" >
            <figcaption class="figure-caption" id="worksheet-image-description">${data.imageDescription}</figcaption>`;
  } else {
    document
      .querySelector("#worksheet-image-wrapper")
      .classList.add("hide-element");
  }

  // Worksheet navigation
  // If there is only one worksheet, hide the navigation
  // Otherwise: Check if there is a next or previous worksheet and show #worksheet-link-back and/or #worksheet-link-forward
  if (worksheets.length === 1) {
    document
      .querySelector("#worksheet-link-back")
      .classList.add("hide-element");
    document
      .querySelector("#worksheet-link-forward")
      .classList.add("hide-element");
  } else {
    if (index > 0) {
      document
        .querySelector("#worksheet-link-back")
        .classList.remove("hide-element");
      document
        .querySelector("#worksheet-link-back")
        .setAttribute("onclick", `loadWorksheet(${index - 1})`);
    } else {
      document
        .querySelector("#worksheet-link-back")
        .classList.add("hide-element");
    }
    if (index < worksheets.length - 1) {
      document
        .querySelector("#worksheet-link-forward")
        .classList.remove("hide-element");
      document
        .querySelector("#worksheet-link-forward")
        .setAttribute("onclick", `loadWorksheet(${index + 1})`);
    } else {
      document
        .querySelector("#worksheet-link-forward")
        .classList.add("hide-element");
    }
  }

  // Worksheet code
  if (showDebugLogs) {
    console.log(
      "Available programming languages: ",
      Object.keys(data.code).length
    );
    console.log("Selected programming language: ", currentSelectedLanguage);
  }
  if (data.code) {
    document
      .querySelector("#worksheet-code-container")
      .classList.remove("hide-element");
    // Code
    if (Object.keys(data.code).length === 1) {
      // if only one language version given, disable language buttons
      document.getElementById(
        "worksheet-code-language-buttons"
      ).innerHTML = `<button class="btn btn-sm btn-outline-secondary btn-disabled" disabled>${
        languageNames[data.primaryLanguage]
      }</button>`;
      currentSelectedLanguage = data.primaryLanguage;
    } else {
      let languageButtonsHtml = "";
      for (const language in data.code) {
        if (
          language === currentSelectedLanguage ||
          (currentSelectedLanguage === "" && language === data.primaryLanguage)
        ) {
          languageButtonsHtml += `<button class="btn btn-sm btn-outline-secondary active change-code-language" data-code-language="${language}" id="worksheet-${index}-btn-language-${language}">${languageNames[language]}</button>`;
        } else {
          languageButtonsHtml += `<button class="btn btn-sm btn-outline-secondary change-code-language" data-code-language="${language}" id="worksheet-${index}-btn-language-${language}">${languageNames[language]}</button>`;
        }
      }
      document.getElementById("worksheet-code-language-buttons").innerHTML =
        languageButtonsHtml;
    }
    if (currentSelectedLanguage === "") {
      currentSelectedLanguage = data.primaryLanguage;
    }
    document.getElementById(
      "worksheet-code"
    ).innerHTML = `<code class="language-${data.primaryLanguage}">${data.code[currentSelectedLanguage]}</code>`;

    // Code highlighting
    hljs.highlightAll();
    hljs.initLineNumbersOnLoad({
      singleLine: true,
    });

    // Code helpers
    getCodeHelpers(index, data, currentSelectedLanguage);
  } else {
    document
      .querySelector("#worksheet-code-container")
      .classList.add("hide-element");
  }

  // Tasks
  let taskHtml = "";
  if (data.tasks) {
    data.tasks.forEach((task, i) => {
      taskHtml += `<!-- Task ${i} -->
                <div class="row task-row mt-5">
                    <!-- Task -->
                    <div class="col-md-12 mb-3">
                        <h4 class="border-bottom pb-3" id="task${i}">${task.title} <a href="#task${i}" class="link-secondary"><i class="bi bi-hash"></i></a></h4>
                    </div>`;
      if (task.description) {
        taskHtml += `<div class="col-md-12 mb-3">
                        <p>${task.description}</p>
                    </div>`;
      }

      taskHtml += `<!-- Task Content -->
                   <div class="col-md-8">
                        <ol type="a">`;

      // Subtasks
      task.subtasks.forEach((subtask, j) => {
        taskHtml += `<li class="mb-4 subtask" id="subtask-${i}-${j}" data-subtask-type="${subtask.answerType}" data-worksheet-id="${index}" data-task-id="${i}" data-subtask-id="${data.titleTechnical}-${i}-${j}">${subtask.task}`;
        if (subtask.answerType === "textShort") {
          taskHtml += `<input type="text" class="form-control save-user-input" id="task-${data.titleTechnical}-${i}-${j}" data-answer-type="${subtask.answerType}">`;
        } else if (subtask.answerType === "textShortCheckable") {
          taskHtml += `<div class="input-group"><input type="text" class="form-control save-user-input" id="task-${
            data.titleTechnical
          }-${i}-${j}" data-answer-type="${
            subtask.answerType
          }" data-answer-checked="false" data-answer-checked-result="null"><button type="button" class="check-user-answer btn btn-outline-secondary" data-subtask-id="task-${
            data.titleTechnical
          }-${i}-${j}" data-correct-answers="${subtask.correctAnswers.join(
            ","
          )}" data-feedback-text="${
            subtask.feedbackText || ""
          }">Antwort prüfen</button></div><div class="alert alert-light mt-2 hide-element" id="task-${
            data.titleTechnical
          }-${i}-${j}-feedback"></div>`;
        } else if (subtask.answerType === "text") {
          taskHtml += `<textarea class="form-control save-user-input" id="task-${data.titleTechnical}-${i}-${j}" data-answer-type="${subtask.answerType}"></textarea>`;
        } else if (subtask.answerType === "textLong") {
          taskHtml += `<textarea class="form-control textarea-lg save-user-input" id="task-${data.titleTechnical}-${i}-${j}" data-answer-type="${subtask.answerType}"></textarea>`;
        } else if (subtask.answerType === "code") {
          taskHtml += `<textarea class="form-control textarea-lg textarea-code save-user-input" id="task-${data.titleTechnical}-${i}-${j}" data-answer-type="${subtask.answerType}"></textarea>`;
        } else if (subtask.answerType === "multipleChoice") {
          taskHtml += `<div class="row gap-2 mt-3 btn-subtask-multiple-choice" id="task-${data.titleTechnical}-${i}-${j}" data-answer-type="${subtask.answerType}">`;
          subtask.choices.forEach((mc, k) => {
            taskHtml += `<div class="col d-grid"><button class="btn btn-outline-secondary btn-subtask-multiple-choice-answer" data-subtask-id="task-${
              data.titleTechnical
            }-${i}-${j}" id="task-${
              data.titleTechnical
            }-${i}-${j}-${k}" value="${mc.correct}" data-feedback-text="${
              mc.feedbackText || ""
            }">${mc.text}</button></div>`;
          });
          taskHtml += `<div class="subtask-multiple-choice-feedback hide-element" data-subtask-id="task-${data.titleTechnical}-${i}-${j}" id="task-${data.titleTechnical}-${i}-${j}-multiple-choice-feedback">
                    <div class="alert alert-light mt-2"></div>
                  </div>`;
        }

        // Subtask phrasingHelpers
        if (subtask.phrasingHelpers) {
          taskHtml += `<button class="btn btn-sm btn-outline-secondary btn-subtask-phrasing-helper btn-subtask-${
            data.titleTechnical
          }-${i}-${j}-hint mt-2" data-bs-toggle="collapse" data-subtask-id="task-${
            data.titleTechnical
          }-${i}-${j}" data-hint-opened="false" data-bs-target="#task-${
            data.titleTechnical
          }-${i}-${j}-phrasingHelper" aria-expanded="false" aria-controls="task-${
            data.titleTechnical
          }-${i}-${j}-phrasingHelper">Hilfe: Formulierungen <i class="bi bi-chevron-down"></i></button>
                          <div class="collapse" id="task-${
                            data.titleTechnical
                          }-${i}-${j}-phrasingHelper">
                              <div class="alert alert-light alert-hint mt-2"><ul>
                                  ${subtask.phrasingHelpers
                                    .map((phrase) => `<li>${phrase}</li>`)
                                    .join("")}
                                  </ul>
                              </div>
                          </div>`;
        }

        // Subtask hints
        let multipleChoiceDisabled = "";
        if (subtask.hints) {
          subtask.hints.forEach((hint, k) => {
            multipleChoiceDisabled = "disabled";
            let buttonDisabled = "disabled";
            if (k === 0) buttonDisabled = "";

            taskHtml += `<button class="btn btn-sm btn-outline-secondary btn-subtask-hint btn-subtask-${
              data.titleTechnical
            }-${i}-${j}-hint mt-2" data-bs-toggle="collapse" data-subtask-id="task-${
              data.titleTechnical
            }-${i}-${j}" data-hint-opened="false" data-bs-target="#task-${
              data.titleTechnical
            }-${i}-${j}-hint-${k}" aria-expanded="false" aria-controls="task-${
              data.titleTechnical
            }-${i}-${j}-hint-${k}" ${buttonDisabled}>Hilfe: Tipp ${
              k + 1
            } <i class="bi bi-chevron-down"></i><span class="badge rounded-pill text-bg-secondary badge-hint-wait hide-element" id="task-${
              data.titleTechnical
            }-${i}-${j}-hint-${k}-timeout">Warte X...</span></button>
                            <div class="collapse" id="task-${
                              data.titleTechnical
                            }-${i}-${j}-hint-${k}">
                                <div class="alert alert-light alert-hint mt-2">
                                    ${hint}
                                </div>
                            </div>`;
          });
        }
        if (subtask.multipleChoice) {
          taskHtml += `<button class="btn btn-sm btn-outline-secondary btn-subtask-hint btn-subtask-${data.titleTechnical}-${i}-${j}-hint btn-subtask-multiple-choice mt-2" data-subtask-id="task-${data.titleTechnical}-${i}-${j}" data-hint-opened="false" data-bs-toggle="collapse" data-bs-target="#task-${data.titleTechnical}-${i}-${j}-mc" aria-expanded="false" aria-controls="task-${data.titleTechnical}-${i}-${j}-mc" ${multipleChoiceDisabled}>Hilfe: Antwortmöglichkeiten <i class="bi bi-chevron-down"></i></button>`;
          taskHtml += `<div class="collapse" id="task-${data.titleTechnical}-${i}-${j}-mc">
                        <div class="d-grid gap-2 mt-3 helpers">`;
          subtask.choices.forEach((mc, k) => {
            taskHtml += `<button class="btn btn-outline-secondary btn-subtask-multiple-choice-answer" data-subtask-id="task-${
              data.titleTechnical
            }-${i}-${j}" id="task-${
              data.titleTechnical
            }-${i}-${j}-${k}" value="${mc.correct}" data-feedback-text="${
              mc.feedbackText || ""
            }">${mc.text}</button>`;
          });
          taskHtml += `</div>
                  <div class="subtask-multiple-choice-feedback hide-element" data-subtask-id="task-${data.titleTechnical}-${i}-${j}" id="task-${data.titleTechnical}-${i}-${j}-multiple-choice-feedback">
                    <div class="alert alert-light mt-2"></div>
                  </div>
                </div>`;
        }
        taskHtml += `</li>`;
      });
      if (askForFeedback) {
        taskHtml += `<li class="mt-5 task-feedback-wrapper" style="list-style-type: none;">
                    <div class="alert alert-light">
                        <strong>Feedback zur Aufgabe</strong> 

                        <div class="row mt-2 pb-2 border-bottom">
                          <div class="col-md-9">
                            Wie hat Ihnen die Aufgabe gefallen?<br>(-- = gar nicht gut, ++ = sehr gut)
                          </div>
                          <div class="col-md-3">
                            <div class="btn-group btn-group-sm btn-task-feedback float-end" role="group" aria-label="Basic radio toggle button group">
                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-${data.titleTechnical}-${i}" id="task-feedback-${data.titleTechnical}-${i}-answer-1" autocomplete="off" value="1">
                                <label class="btn btn-outline-dark" for="task-feedback-${data.titleTechnical}-${i}-answer-1">--</label>
                                
                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-${data.titleTechnical}-${i}" id="task-feedback-${data.titleTechnical}-${i}-answer-2" autocomplete="off" value="2">
                                <label class="btn btn-outline-dark" for="task-feedback-${data.titleTechnical}-${i}-answer-2">-</label>
                                
                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-${data.titleTechnical}-${i}" id="task-feedback-${data.titleTechnical}-${i}-answer-3" autocomplete="off" value="3">
                                <label class="btn btn-outline-dark" for="task-feedback-${data.titleTechnical}-${i}-answer-3">o</label>

                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-${data.titleTechnical}-${i}" id="task-feedback-${data.titleTechnical}-${i}-answer-4" autocomplete="off" value="4">
                                <label class="btn btn-outline-dark" for="task-feedback-${data.titleTechnical}-${i}-answer-4">+</label>

                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-${data.titleTechnical}-${i}" id="task-feedback-${data.titleTechnical}-${i}-answer-5" autocomplete="off" value="5">
                                <label class="btn btn-outline-dark" for="task-feedback-${data.titleTechnical}-${i}-answer-5">++</label>
                            </div>
                          </div>
                        </div>

                        <div class="row mt-2 pb-2 border-bottom">
                          <div class="col-md-9">
                            Wie sicher Sind sie sich, dass Ihre Antwort korrekt ist?<br>(-- = unsicher, ++ = sicher)
                          </div>
                          <div class="col-md-3">
                            <div class="btn-group btn-group-sm btn-task-feedback float-end" role="group" aria-label="Basic radio toggle button group">
                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-correct-${data.titleTechnical}-${i}" id="task-feedback-correct-${data.titleTechnical}-${i}-answer-1" autocomplete="off" value="1">
                                <label class="btn btn-outline-dark" for="task-feedback-correct-${data.titleTechnical}-${i}-answer-1">--</label>
                                
                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-correct-${data.titleTechnical}-${i}" id="task-feedback-correct-${data.titleTechnical}-${i}-answer-2" autocomplete="off" value="2">
                                <label class="btn btn-outline-dark" for="task-feedback-correct-${data.titleTechnical}-${i}-answer-2">-</label>
                                
                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-correct-${data.titleTechnical}-${i}" id="task-feedback-correct-${data.titleTechnical}-${i}-answer-3" autocomplete="off" value="3">
                                <label class="btn btn-outline-dark" for="task-feedback-correct-${data.titleTechnical}-${i}-answer-3">o</label>

                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-correct-${data.titleTechnical}-${i}" id="task-feedback-correct-${data.titleTechnical}-${i}-answer-4" autocomplete="off" value="4">
                                <label class="btn btn-outline-dark" for="task-feedback-correct-${data.titleTechnical}-${i}-answer-4">+</label>

                                <input type="radio" class="btn-check save-user-feedback" name="task-feedback-correct-${data.titleTechnical}-${i}" id="task-feedback-correct-${data.titleTechnical}-${i}-answer-5" autocomplete="off" value="5">
                                <label class="btn btn-outline-dark" for="task-feedback-correct-${data.titleTechnical}-${i}-answer-5">++</label>
                            </div>
                          </div>
                        </div>

                        <div class="row mt-2">
                          <div class="col-md-12">
                            Gab es Probleme beim Bearbeiten der Aufgabe?
                            <textarea class="form-control save-user-feedback" name="task-feedback-text-${data.titleTechnical}-${i}" id="task-feedback-${data.titleTechnical}-${i}-answer-text" placeholder="Hier können Sie Feedback zur Aufgabe hinterlassen."></textarea>
                          </div>
                        </div>
                          
                    </div>
                </li>`;
      }
      taskHtml += `</ol>
                </div><!-- End of Task Content -->`;

      // Task helpers
      if (task.helpers) {
        taskHtml += `<!-- Task Helpers -->
                    <div class="col-md-4">
                        <h6>Weitere Erklärungen</h6>
                        <div class="accordion accordion-helpers" id="task-${data.titleTechnical}-${i}-helpers">`;
        task.helpers.forEach((helper, j) => {
          taskHtml += `<div class="accordion-item" id="task-${data.titleTechnical}-${i}-helper-${j}">
                        <h2 class="accordion-header">
                            <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#task-${data.titleTechnical}-${i}-helper-${j}-body" aria-expanded="false" aria-controls="helper2-1">
                                ${helper.title}
                            </button>
                        </h2>
                        <div id="task-${data.titleTechnical}-${i}-helper-${j}-body" class="accordion-collapse collapse">
                            <div class="accordion-body">
                                ${helper.content}
                            </div>
                        </div>
                    </div>`;
        });
        taskHtml += `</div><!-- End of Task Helpers -->`;
      }

      taskHtml += `</div>
                </div><!-- End of Task ${i} -->`;
    });
  }
  document.getElementById("worksheet-tasks").innerHTML = taskHtml;

  // Citations
  let citationsHtml = "";
  if (data.citations) {
    document
      .querySelector("#worksheet-citations-wrapper")
      .classList.remove("hide-element");
    data.citations.forEach((citation, i) => {
      citationsHtml += `<p id="${citation.short}">${citation.citation}</p>`;
    });
    document.getElementById("worksheet-citations").innerHTML = citationsHtml;
  } else {
    document
      .querySelector("#worksheet-citations-wrapper")
      .classList.add("hide-element");
  }
  loadUserInput(index);
  initialiseWorksheet();
}

function getCodeHelpers(index, data, language) {
  if (data.codeHelpers[language]) {
    document
      .querySelector("#code-helpers-wrapper")
      .classList.remove("hide-element");
    let codeHelpersHtml = "";
    data.codeHelpers[language].forEach((helper, i) => {
      let codeHelpersId = `helper${index}-${language}-${i}`;
      let codeHelpersBodyShow = "";
      let codeHelpersButtonCollapse = "collapsed";
      let codeHelpersButtonAriaExpanded = "false";
      codeHelpersHtml += `<div class="accordion-item" id="${codeHelpersId}" >
                    <h2 class="accordion-header">
                        <button class="accordion-button ${codeHelpersButtonCollapse}" type="button" data-bs-toggle="collapse" data-bs-target="#${codeHelpersId}-body" aria-expanded="${codeHelpersButtonAriaExpanded}" aria-controls="${codeHelpersId}-body">
                            ${helper.title}
                        </button>
                    </h2>
                    <div id="${codeHelpersId}-body" class="accordion-collapse collapse ${codeHelpersBodyShow}">
                        <div class="accordion-body">
                            ${helper.content}
                        </div>
                    </div>
                </div>`;
      i++;
    });
    document.getElementById("code-helpers").innerHTML = codeHelpersHtml;
    // loadUserInput(index);
  } else {
    document
      .querySelector("#code-helpers-wrapper")
      .classList.add("hide-element");
  }
}
