function initialiseWorksheet() {
  loadUserInput(currentIndex);

  // EventListeners for changing inputs and saving current states and sudent solutions
  document
    .querySelectorAll(".save-user-input, .save-user-feedback")
    .forEach((input) => {
      input.addEventListener("change", () => saveUserInput(currentIndex));
    });

  document
    .querySelectorAll(
      ".btn-subtask-multiple-choice-answer, .change-code-language, .btn-subtask-hint .btn-subtask-phrasing-helper"
    )
    .forEach((button) => {
      button.addEventListener("click", () => saveUserInput(currentIndex));
    });

  document.querySelectorAll(".accordion-button").forEach((button) => {
    button.addEventListener("click", () =>
      setTimeout(function () {
        saveUserInput(currentIndex);
      }, 1000)
    );
  });

  // Highlighting currently active subtask
  // Remove class .current-subtask of all other subtasks and only give it to the currently active one
  document.querySelectorAll(".subtask").forEach((input) => {
    input.addEventListener("click", function () {
      currentSubtaskId = this.getAttribute("data-subtask-id");
      currentSubtaskLink = this.getAttribute("id");
      startTrackingCurrentSubtask();
      if (showDebugLogs)
        console.log(
          "Setting current subtask to: ",
          this.getAttribute("data-subtask-id")
        );
      document
        .querySelectorAll(".subtask")
        .forEach((el) => el.classList.remove("current-subtask"));
      this.classList.add("current-subtask");
      // Set href of the "move to current subtask" button to current subtask
      const moveToCurrentSubtaskButton = document.getElementById(
        "move-to-current-subtask"
      );
      if (moveToCurrentSubtaskButton) {
        moveToCurrentSubtaskButton.classList.remove("hide-element");
        moveToCurrentSubtaskButton.setAttribute(
          "href",
          `#${currentSubtaskLink}`
        );
        if (showDebugLogs)
          console.log(
            `- - - Setting current subtask link to: #${currentSubtaskLink}`
          );
      }
      saveUserInput(currentIndex);
    });
  });

  // Check user answer on an .check-user-answer button click (correct answers are in data-correct-answer attribute
  // if multiple correct answers are possible, they are separated by a comma)
  document.querySelectorAll(".check-user-answer").forEach((button) => {
    button.addEventListener("click", function (e) {
      endTrackingCurrentSubtask(); // Stop tracking current subtask when checking the answer
      // Disable all remaining hint buttons for this subtask
      const subtaskHintButtons = document.querySelectorAll(
        `.btn-subtask-hint[data-subtask-id="${e.target.getAttribute(
          "data-subtask-id"
        )}"]`
      );
      subtaskHintButtons.forEach((btn) => {
        if (btn.getAttribute("data-hint-opened") === "false") {
          // Only disable buttons that have not been opened yet
          btn.setAttribute("disabled", "true");
        }
      });

      if (showDebugLogs)
        console.log(
          "Checking user answer for subtask: ",
          e.target.getAttribute("data-subtask-id")
        );
      const subtaskId = e.target.getAttribute("data-subtask-id");
      const subtaskInput = document.getElementById(subtaskId);
      if (!subtaskInput) {
        console.error(
          `Subtask input with id ${subtaskId} not found. Cannot check user answer.`
        );
        return;
      }
      const correctAnswers = e.target.getAttribute("data-correct-answers");
      if (!correctAnswers) {
        console.error(
          `No correct answers defined for subtask ${subtaskId}. Cannot check user answer.`
        );
        return;
      }
      const correctAnswersArray = correctAnswers
        .split(",")
        .map((ans) => ans.trim());
      const userAnswer = subtaskInput.value.trim(); // Get user answer and convert it to lowercase for case-insensitive comparison
      if (showDebugLogs)
        console.log(
          `User answer: "${userAnswer}", Correct answers: ${correctAnswersArray.join(
            ", "
          )}`
        );
      const isCorrect = correctAnswersArray.includes(userAnswer); // Check if user answer is in the correct answers array
      const feedbackElement = document.querySelector(`#${subtaskId}-feedback`);
      document.getElementById(subtaskId).setAttribute("disabled", "true"); // Disable the input field after checking the answer
      subtaskInput.setAttribute("data-answer-checked", "true");
      if (isCorrect) {
        e.target.innerHTML = `<i class="bi bi-check-lg"></i> Antwort korrekt`;
        e.target.classList.remove("btn-outline-secondary");
        e.target.classList.add("btn-outline-success");
        e.target.setAttribute("disabled", "true");
        subtaskInput.setAttribute("data-answer-checked-result", "true");
      } else {
        e.target.innerHTML = `<i class="bi bi-x-lg"></i> Antwort falsch`;
        e.target.classList.remove("btn-outline-secondary");
        e.target.classList.add("btn-outline-danger");
        e.target.setAttribute("disabled", "true");
        subtaskInput.setAttribute("data-answer-checked-result", "false");
      }
      if (feedbackElement) {
        feedbackElement.classList.remove("hide-element");
        feedbackElement.classList.add("show");
        if (isCorrect) {
          feedbackElement.innerHTML = `<span class="badge text-bg-success">Die Antwort ist korrekt.</span><br>${e.target.getAttribute(
            "data-feedback-text"
          )}`;
        } else {
          feedbackElement.innerHTML = `<span class="badge text-bg-danger">Die Antwort ist falsch.</span><br>${e.target.getAttribute(
            "data-feedback-text"
          )}<span class="text-muted mt-2">Die richtige Antwort ist:</span> ${correctAnswersArray.join(
            " oder "
          )}`;
        }
      }
    });
  });

  // Disabling and enabling subtask hint buttons
  document.querySelectorAll(".btn-subtask-hint").forEach((button) => {
    button.addEventListener("click", function (e) {
      if (showDebugLogs)
        console.log(
          "Disabling subtask hint and enabling next for ",
          e.target.getAttribute("data-subtask-id")
        );
      const subtaskId = e.target.getAttribute("data-subtask-id");
      const subtaskHintButtons = document.querySelectorAll(
        `.btn-subtask-hint[data-subtask-id="${subtaskId}"][data-hint-opened="false"]`
      );
      if (subtaskHintButtons.length > 0) {
        if (showDebugLogs)
          console.log("Found these hint buttons: ", subtaskHintButtons);
        // Wait for the specified timeout time (hintTimeout in settings.js) before enabling the next hint button and
        // show remaining seconds in the hint timeout span
        /* const hintTimeoutSpan = document.querySelector(
          `${subtaskHintButtons[0].getAttribute("data-bs-target")}-timeout`
        );
        if (hintTimeoutSpan) {
          hintTimeoutSpan.classList.remove("hide-element");
          let secondsLeft = hintTimeout;
          hintTimeoutSpan.innerHTML = `Warte ${secondsLeft}...`;
          const interval = setInterval(() => {
            secondsLeft--;
            hintTimeoutSpan.innerHTML = `Warte ${secondsLeft}...`;
            if (secondsLeft <= 0) {
              clearInterval(interval);
              hintTimeoutSpan.classList.add("hide-element");
              // Enable the next hint button
              subtaskHintButtons[0].removeAttribute("disabled");
            }
          }, 1000);
        }*/
      }
      this.disabled = true;
      subtaskHintButtons[0].removeAttribute("disabled");
      this.setAttribute("data-hint-opened", "true");
      saveUserInput(currentIndex);
    });
  });

  // Disabling and enabling phraseHelper buttons
  document
    .querySelectorAll(".btn-subtask-phrasing-helper")
    .forEach((button) => {
      button.addEventListener("click", function (e) {
        if (showDebugLogs)
          console.log(
            "Disabling phrasing helper for subtask: ",
            e.target.getAttribute("data-subtask-id")
          );
        this.disabled = true;
        this.setAttribute("data-hint-opened", "true");
        saveUserInput(currentIndex);
      });
    });

  // Setting multiple choice buttons to active
  document
    .querySelectorAll(".btn-subtask-multiple-choice-answer")
    .forEach((button) => {
      button.addEventListener("click", function (e) {
        endTrackingCurrentSubtask(); // Stop tracking current subtask when checking the answer
        // Disable all buttons of this subtask
        const subtaskId = e.target.getAttribute("data-subtask-id");
        const subtaskButtons = document.querySelectorAll(
          `.btn-subtask-multiple-choice-answer[data-subtask-id="${subtaskId}"]`
        );
        let correctAnswer = null;
        subtaskButtons.forEach((btn) => {
          btn.classList.remove("active");
          btn.setAttribute("disabled", "true");
          if (btn.value === "true") {
            correctAnswer = btn.innerHTML;
          }
        });
        this.classList.add("active");

        // Feedback: If answer was correct, set button to green, else red
        if (this.value === "true") {
          this.classList.remove("btn-outline-secondary");
          this.classList.add("btn-outline-success");
        } else {
          this.classList.remove("btn-outline-secondary");
          this.classList.add("btn-outline-danger");
        }

        // Display feedback
        let feedbackText = "";
        console.log(
          "(MC) Trying to display feedback in element:",
          `#${subtaskId}-multiple-choice-feedback`
        );
        const feedback = document.querySelector(
          `#${subtaskId}-multiple-choice-feedback`
        );
        if (feedback) {
          feedback.classList.remove("hide-element");
          feedback.classList.add("show");
          if (this.getAttribute("data-feedback-text")) {
            feedbackText = this.getAttribute("data-feedback-text");
          }
          if (this.value === "true") {
            feedback.querySelector(
              ".alert"
            ).innerHTML = `<span class="badge text-bg-success">Die Antwort ist korrekt.</span><br>${feedbackText}`;
          } else {
            feedback.querySelector(
              ".alert"
            ).innerHTML = `<span class="badge text-bg-danger">Die Antwort ist falsch.</span><br>${feedbackText}<div class="mt-2"><span class="text-muted">Die richtige Antwort ist:</span><br>${correctAnswer}</div>`;
          }
        }
      });
    });
  // if (showDebugLogs) showInputIDs();
}

document.addEventListener("DOMContentLoaded", function () {
  initialiseWorksheet();
  document.getElementById("subject-name").innerHTML = subjectName;
  document.getElementById("course-name").innerHTML = courseName;
  document.getElementById("sidebar-label").innerHTML = courseName;
});

function loadUserInput(currentWorksheet) {
  const worksheetDataKey = "worksheetData";
  const worksheetData =
    JSON.parse(localStorage.getItem(worksheetDataKey)) || {};
  const sheetData = worksheetData[currentWorksheet];
  if (showDebugLogs)
    console.log("Loading user input for worksheet: ", currentWorksheet);

  if (!sheetData) return;

  const worksheet = worksheets[currentWorksheet];

  // Load code helpers state
  const codeHelpers = sheetData.codeHelpers || [];
  codeHelpers.forEach((helperId) => {
    const helper = document.getElementById(helperId);
    if (helper) {
      const helperBody = document.getElementById(helperId + "-body");
      if (helperBody) {
        helperBody.classList.add("show");
        helperBody.setAttribute("aria-expanded", "true");
      }
      const button = document.querySelector(
        `#${helperId} .accordion-header.accordion-button`
      );
      if (button) {
        button.setAttribute("aria-expanded", "true");
        button.classList.remove("collapsed");
      }
    }
    if (showDebugLogs)
      console.log(`Loading code helper into ${helperId} (open)`);
  });

  // Load task-related data
  worksheet.tasks.forEach((task, taskIndex) => {
    const taskData = sheetData.tasks[taskIndex];
    if (showDebugLogs) console.log(`Loading task data for task ${taskIndex}`);
    if (!taskData) return;

    // Load feedback
    if (taskData.feedback) {
      const feedbackInput = document.getElementById(
        `task-feedback-${worksheet.titleTechnical}-${taskIndex}-answer-${taskData.feedback}`
      );
      if (feedbackInput) feedbackInput.checked = true;
      if (showDebugLogs)
        console.log(
          `- Task has feedback, load it into #task-feedback-${worksheet.titleTechnical}-${taskIndex}-answer-${taskData.feedback}`
        );
    }

    if (taskData.feedbackCorrect) {
      const feedbackInputCorrect = document.getElementById(
        `task-feedback-correct-${worksheet.titleTechnical}-${taskIndex}-answer-${taskData.feedbackCorrect}`
      );
      if (feedbackInputCorrect) feedbackInputCorrect.checked = true;
      if (showDebugLogs)
        console.log(
          `- Task has feedback-correct, load it into #task-feedback-correct-${worksheet.titleTechnical}-${taskIndex}-answer-${taskData.feedbackCorrect}`
        );
    }

    // Load feedback text
    if (taskData.feedbackText) {
      const feedbackTextInput = document.getElementById(
        `task-feedback-${worksheet.titleTechnical}-${taskIndex}-answer-text`
      );
      feedbackTextInput.value = taskData.feedbackText || "";
      if (showDebugLogs)
        console.log(
          `- Task has feedback text, load it into #task-feedback-${worksheet.titleTechnical}-${taskIndex}-answer-text`
        );
    }

    // Open task helpers that have been opened before
    taskData.helpers.forEach((helperId) => {
      const helper = document.getElementById(helperId);
      if (helper) {
        const button = helper.querySelector(
          ".accordion-header .accordion-button"
        );
        if (button) {
          button.setAttribute("aria-expanded", "true");
          button.classList.remove("collapsed");
        }
        const body = helper.querySelector(".accordion-collapse");
        if (body) {
          body.classList.add("show");
          body.setAttribute("aria-expanded", "true");
        }
        if (showDebugLogs) console.log(`- Task has open helper: #${helperId}`);
      }
    });

    // Load subtasks
    task.subtasks.forEach((subtask, subtaskIndex) => {
      const subtaskData = taskData.subtasks[subtaskIndex];
      if (!subtaskData) return;
      if (showDebugLogs)
        console.log(
          `- - Loading subtask data for task ${taskIndex} subtask ${subtaskIndex}: ${subtaskData.studentSolution}`
        );

      // Add class .current-subtask to worksheetData.currentSubtaskId
      if (
        `${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}` ===
        sheetData.currentSubtaskId
      ) {
        const subtaskElement = document.querySelector(
          `.subtask[data-subtask-id="${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
        );
        if (subtaskElement) {
          currentSubtaskId = `${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}`;
          subtaskElement.classList.add("current-subtask");
          // Set href of the "move to current subtask" button to current subtask
          const moveToCurrentSubtaskButton = document.getElementById(
            "move-to-current-subtask"
          );
          if (moveToCurrentSubtaskButton) {
            moveToCurrentSubtaskButton.classList.remove("hide-element");
            moveToCurrentSubtaskButton.setAttribute(
              "href",
              `#subtask-${taskIndex}-${subtaskIndex}`
            );
          }
          if (showDebugLogs)
            console.log(
              `- - - Setting current subtask to: ${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}`
            );
        }
      }

      // Load student solution
      const subtaskInput = document.getElementById(
        `task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}`
      );
      if (subtaskInput) {
        // If the subtask has data-answer-type="textShortCheckable", check if the subtask was already checked (subtask.answerChecked), if yes: set data-answer-checked to true
        // and check if answer was  correct or not correct (subtask.answerCheckedResult), set data-answer-checked-result and display the feedback
        if (
          subtaskInput.getAttribute("data-answer-type") === "textShortCheckable"
        ) {
          if (subtaskData.answerChecked === "true") {
            subtaskInput.setAttribute("data-answer-checked", "true");
            subtaskInput.setAttribute(
              "data-answer-checked-result",
              subtaskData.answerCheckedResult
            );
            subtaskInput.setAttribute("disabled", "true");
            if (showDebugLogs)
              console.log(
                `- - - Loading answer checked state for task ${taskIndex} subtask ${subtaskIndex}:`,
                subtaskData.answerChecked,
                subtaskData.answerCheckedResult
              );
            const checkButton = document.querySelector(
              `.check-user-answer[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
            );
            const feedbackElement = document.querySelector(
              `#task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-feedback`
            );
            if (checkButton) {
              checkButton.setAttribute("disabled", "true");
              const correctAnswersArray = checkButton
                .getAttribute("data-correct-answers")
                .split(",")
                .map((ans) => ans.trim());
              if (subtaskData.answerCheckedResult === "true") {
                checkButton.innerHTML = `<i class="bi bi-check-lg"></i> Antwort korrekt`;
                checkButton.classList.remove("btn-outline-secondary");
                checkButton.classList.add("btn-outline-success");
                if (feedbackElement) {
                  feedbackElement.classList.remove("hide-element");
                  feedbackElement.classList.add("show");
                  feedbackElement.innerHTML =
                    `<span class="badge text-bg-success">Die Antwort ist korrekt.</span>` +
                    "<br>" +
                    checkButton.getAttribute("data-feedback-text");
                }
              } else {
                checkButton.innerHTML = `<i class="bi bi-x-lg"></i> Antwort falsch`;
                checkButton.classList.remove("btn-outline-secondary");
                checkButton.classList.add("btn-outline-danger");
                if (feedbackElement) {
                  feedbackElement.classList.remove("hide-element");
                  feedbackElement.classList.add("show");
                  feedbackElement.innerHTML =
                    `<span class="badge text-bg-danger">Die Antwort ist falsch.</span>` +
                    "<br>" +
                    checkButton.getAttribute("data-feedback-text") +
                    `<div class="mt-2"><span class="text-muted">Die richtige Antwort ist:</span> ${correctAnswersArray.join(
                      " oder "
                    )}</div>`;
                }
              }
            }
            // Disable all remaining hint buttons for this subtask
            const subtaskHintButtons = document.querySelectorAll(
              `.btn-subtask-hint[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
            );
            subtaskHintButtons.forEach((btn) => {
              if (btn.getAttribute("data-hint-opened") === "false") {
                // Only disable buttons that have not been opened yet
                btn.setAttribute("disabled", "true");
                btn.setAttribute("data-hint-opened", "true"); // Set data-hint-opened to true to prevent further clicks
              }
            });
          } else {
            subtaskInput.setAttribute("data-answer-checked", "false");
            subtaskInput.setAttribute("data-answer-checked-result", "null");
          }
        }

        // If the subtask has data-answer-type="multiple-choice", load the multiple choice state
        if (
          subtaskInput.getAttribute("data-answer-type") === "multipleChoice"
        ) {
          // If the subtask has a multiple answer state, load it
          if (subtaskData.multipleChoice.selectedAnswer) {
            // Disable all multiple choice buttons
            if (showDebugLogs)
              console.log(
                `Search for buttons with following selector: .btn-subtask-multiple-choice-answer[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
              );
            const mcButtons = document.querySelectorAll(
              `.btn-subtask-multiple-choice-answer[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
            );
            let = correctAnswer = "";
            mcButtons.forEach((btn) => {
              btn.setAttribute("disabled", true);
              btn.classList.remove("active");
              btn.classList.remove("btn-outline-secondary");
              if (btn.value === "true") {
                correctAnswer = btn.innerHTML;
              }
            });

            // Display selected answer
            const selectedAnswer = document.getElementById(
              subtaskData.multipleChoice.selectedAnswer
            );
            if (selectedAnswer) {
              if (selectedAnswer.getAttribute("value") === "true") {
                selectedAnswer.classList.remove("btn-outline-secondary");
                selectedAnswer.classList.add("btn-outline-success");
                selectedAnswer.classList.add("active");
              } else {
                selectedAnswer.classList.remove("btn-outline-secondary");
                selectedAnswer.classList.add("btn-outline-danger");
                selectedAnswer.classList.add("active");
              }
              if (showDebugLogs)
                console.log(
                  `- - - Loading selected answer into ${subtaskData.multipleChoice.selectedAnswer}`
                );

              // Show feedback for selected answer
              const feedback = document.querySelector(
                `#task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-multiple-choice-feedback`
              );
              if (feedback) {
                feedback.classList.remove("hide-element");
                feedback.classList.add("show");
                if (selectedAnswer.getAttribute("value") === "true") {
                  feedback.querySelector(".alert").innerHTML =
                    `<span class="badge text-bg-success">Die Antwort ist korrekt.</span>` +
                    "<br>" +
                    selectedAnswer.getAttribute("data-feedback-text");
                } else {
                  feedback.querySelector(".alert").innerHTML =
                    `<span class="badge text-bg-danger">Die Antwort ist falsch.</span>` +
                    "<br>" +
                    selectedAnswer.getAttribute("data-feedback-text") +
                    `<div class="mt-2"><span class="text-muted">Die richtige Antwort ist:</span><br>${correctAnswer}</div>`;
                }
              }
              if (showDebugLogs)
                console.log(
                  `- - - Loading multiple choice feedback into #task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-multiple-choice-feedback`
                );
            }
          }
        } else {
          // If the subtask is not a multiple choice, load the student solution
          subtaskInput.value = subtaskData.studentSolution;
          if (showDebugLogs)
            console.log(
              `- - - Loading student solution into #task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}`
            );
        }
      }

      // Load phraseHelper state
      const phrasingHelper = document.querySelector(
        `.btn-subtask-phrasing-helper[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
      );
      if (phrasingHelper) {
        if (subtaskData.phrasingHelperOpened) {
          phrasingHelper.setAttribute("data-hint-opened", "true");
          phrasingHelper.setAttribute("disabled", "true");
          const phrasingHelperBody = document.querySelector(
            `#task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-phrasingHelper`
          );
          if (phrasingHelperBody) {
            phrasingHelperBody.classList.add("show");
            phrasingHelperBody.setAttribute("aria-expanded", "true");
          }
          if (showDebugLogs)
            console.log(
              `- - - Loading phrasing helper state for task ${taskIndex} subtask ${subtaskIndex}: opened`
            );
        } else {
          phrasingHelper.setAttribute("data-hint-opened", "false");
          if (showDebugLogs)
            console.log(
              `- - - Loading phrasing helper state for task ${taskIndex} subtask ${subtaskIndex}: not opened`
            );
        }
      }

      // Load subtask hints states:
      // Disable all already opened hints, only enable hints that have not been opened yet
      const subtaskHintButtons = document.querySelectorAll(
        `.btn-subtask-hint[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
      );
      let previousHintOpened = false;
      subtaskHintButtons.forEach((button, hintIndex) => {
        if (subtaskData.hintsOpened[hintIndex]) {
          if (showDebugLogs)
            console.log(
              "- - - - hint with index was open, set to disabled:",
              hintIndex
            );
          button.setAttribute("data-hint-opened", "true");
          button.setAttribute("disabled", "true");

          // Open collapsed hint
          const hintBody = document.querySelector(
            `#task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-hint-${hintIndex}`
          );
          if (hintBody) {
            hintBody.classList.add("show");
            hintBody.setAttribute("aria-expanded", "true");
          }
          previousHintOpened = true;
        } else {
          if (showDebugLogs) {
            if (previousHintOpened) {
              console.log(
                "- - - - hint with index has not been opened yet and is the next to be opened:",
                hintIndex
              );
              button.disabled = false;
              previousHintOpened = false;
            } else {
              console.log(
                "- - - - hint with index has not been opened yet, let it stay disabled:",
                hintIndex
              );
            }
          }
        }
        if (showDebugLogs)
          console.log(
            `- - - Loading subtask hint state for task ${taskIndex} subtask ${subtaskIndex} hint ${hintIndex}:`,
            subtaskData.hintsOpened[hintIndex]
          );
      });

      // Load multiple choice state
      if (subtaskData.hintMultipleChoice) {
        if (subtaskData.hintMultipleChoice.opened) {
          const mcContainer = document.getElementById(
            `task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-mc`
          );
          if (mcContainer) mcContainer.classList.add("show");
          if (showDebugLogs)
            console.log(
              `- - - Loading multiple choice state into #task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-mc`
            );
        }

        if (subtaskData.hintMultipleChoice.selectedAnswer) {
          // Disable all multiple choice buttons
          if (showDebugLogs)
            console.log(
              `Search for buttons with following selector: .btn-subtask-multiple-choice-answer[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
            );
          const mcButtons = document.querySelectorAll(
            `.btn-subtask-multiple-choice-answer[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
          );
          let = correctAnswer = "";
          mcButtons.forEach((btn) => {
            btn.setAttribute("disabled", true);
            btn.classList.remove("active");
            btn.classList.remove("btn-outline-secondary");
            if (btn.value === "true") {
              correctAnswer = btn.innerHTML;
            }
          });

          // Display selected answer
          const selectedAnswer = document.getElementById(
            subtaskData.hintMultipleChoice.selectedAnswer
          );
          if (selectedAnswer) {
            if (selectedAnswer.getAttribute("value") === "true") {
              selectedAnswer.classList.remove("btn-outline-secondary");
              selectedAnswer.classList.add("btn-outline-success");
              selectedAnswer.classList.add("active");
            } else {
              selectedAnswer.classList.remove("btn-outline-secondary");
              selectedAnswer.classList.add("btn-outline-danger");
              selectedAnswer.classList.add("active");
            }
            if (showDebugLogs)
              console.log(
                `- - - Loading selected answer into ${subtaskData.hintMultipleChoice.selectedAnswer}`
              );

            // Show feedback for selected answer
            const feedback = document.querySelector(
              `#task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-multiple-choice-feedback`
            );
            if (feedback) {
              feedback.classList.remove("hide-element");
              feedback.classList.add("show");
              if (selectedAnswer.getAttribute("value") === "true") {
                feedback.querySelector(".alert").innerHTML =
                  `<span class="badge text-bg-success">Die Antwort ist korrekt.</span>` +
                  "<br>" +
                  selectedAnswer.getAttribute("data-feedback-text");
              } else {
                feedback.querySelector(".alert").innerHTML =
                  `<span class="badge text-bg-danger">Die Antwort ist falsch.</span>` +
                  "<br>" +
                  selectedAnswer.getAttribute("data-feedback-text") +
                  `<div class="mt-2"><span class="text-muted">Die richtige Antwort ist:</span><br>${correctAnswer}</div>`;
              }
            }
            if (showDebugLogs)
              console.log(
                `- - - Loading multiple choice feedback into #task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-multiple-choice-feedback`
              );
          }
        }
      }
    });
  });

  // Load code helpers (add aria-expanded="true" to the button)
  sheetData.codeHelpers.forEach((helperId) => {
    const helper = document.getElementById(helperId);
    if (helper) {
      const button = document.querySelector(`#${helperId} .accordion-button`);
      if (button) {
        button.setAttribute("aria-expanded", "true");
        button.classList.remove("collapsed");
      }
      const body = document.querySelector(`#${helperId} .accordion-body`);
      if (body) {
        body.classList.add = "show";
        body.setAttribute("aria-expanded", "true");
      }
      if (showDebugLogs)
        console.log(`- Loading code helper into ${helperId} (open)`);
    }
  });

  // Show and hide elements based on the current selected programming language
  document.querySelectorAll(".language-specific").forEach((element) => {
    if (
      element.classList.contains("task-language-" + currentSelectedLanguage)
    ) {
      element.classList.remove("hide-element");
    } else {
      element.classList.add("hide-element");
    }
  });
}

function saveUserInput(currentWorksheet) {
  console.log("Saving user input for worksheet: ", currentWorksheet);
  const worksheetDataKey = "worksheetData";
  const worksheetData =
    JSON.parse(localStorage.getItem(worksheetDataKey)) || {};

  const worksheet = worksheets[currentWorksheet];

  // Save worksheet data
  if (!worksheetData[currentWorksheet]) {
    worksheetData[currentWorksheet] = {
      title: worksheet.title,
      titleTechnical: worksheet.titleTechnical,
      selectedProgrammingLanguage: currentSelectedLanguage,
      currentSubtaskId: currentSubtaskId,
      codeHelpers: [],
      tasks: {},
    };
  }

  // Save current subtask
  worksheetData[currentWorksheet].currentSubtaskId = currentSubtaskId;

  // Save code helper state
  codeHelpers = Array.from(
    document.querySelectorAll(`#code-helpers .accordion-collapse.show`)
  ).map((helper) => {
    const parent = helper.closest(".accordion-item");
    if (parent) {
      return parent.id;
    }
    return null;
  });
  codeHelpers = codeHelpers.filter((helper) => helper !== null);
  worksheetData[currentWorksheet].codeHelpers = codeHelpers;
  if (showDebugLogs)
    console.log(
      "Saving code helper state: ",
      worksheetData[currentWorksheet].codeHelpers
    );

  // Save task-related data
  worksheet.tasks.forEach((task, taskIndex) => {
    if (!worksheetData[currentWorksheet].tasks[taskIndex]) {
      worksheetData[currentWorksheet].tasks[taskIndex] = {
        title: task.title,
        subtasks: {},
        helpers: [],
      };
    }

    // Save feedback
    const feedbackInput = document.querySelector(
      `input[name="task-feedback-${worksheet.titleTechnical}-${taskIndex}"]:checked`
    );
    worksheetData[currentWorksheet].tasks[taskIndex].feedback = feedbackInput
      ? feedbackInput.value
      : null;

    const feedbackCorrectInput = document.querySelector(
      `input[name="task-feedback-correct-${worksheet.titleTechnical}-${taskIndex}"]:checked`
    );
    worksheetData[currentWorksheet].tasks[taskIndex].feedbackCorrect =
      feedbackCorrectInput ? feedbackCorrectInput.value : null;

    const feedbackText = document.getElementById(
      `task-feedback-${worksheet.titleTechnical}-${taskIndex}-answer-text`
    );
    if (feedbackText) {
      worksheetData[currentWorksheet].tasks[taskIndex].feedbackText =
        feedbackText.value;
    }

    // If this task has helpers (class .accordion-helpers or id #task-${currentWorksheet.titleTechnical}-${taskIndex}-helpers),
    // save task helper state (open or closed)
    const taskHelpers = document.querySelector(
      `#task-${worksheet.titleTechnical}-${taskIndex}-helpers`
    );
    if (taskHelpers) {
      openedHelpers = Array.from(
        taskHelpers.querySelectorAll(`.accordion-collapse.show`)
      ).map((helper) => {
        const parent = helper.closest(".accordion-item");
        if (parent) {
          return parent.id;
        }
        return null;
      });
      openedHelpers = openedHelpers.filter((helper) => helper !== null);
      worksheetData[currentWorksheet].tasks[taskIndex].helpers =
        openedHelpers.length > 0 ? openedHelpers : [];
    }
    if (showDebugLogs)
      console.log(
        `- - Saving task helper state for task ${taskIndex}:`,
        worksheetData[currentWorksheet].tasks[taskIndex].helpers
      );

    // Save subtasks
    task.subtasks.forEach((subtask, subtaskIndex) => {
      if (
        !worksheetData[currentWorksheet].tasks[taskIndex].subtasks[subtaskIndex]
      ) {
        worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
          subtaskIndex
        ] = {
          task: subtask.task,
          studentSolution: null,
          multipleChoice: {
            selectedAnswer: null,
            correct: null,
          },
          hintMultipleChoice: {
            opened: false,
            selectedAnswer: null,
            correct: null,
          },
          hintsOpened: [],
        };
      }

      // Save student solution
      const subtaskInput = document.getElementById(
        `task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}`
      );
      // If the subtask has data-answer-type="multiple-choice", save the multiple choice state and the selected answer
      if (subtaskInput) {
        // If the subtask has data-answer-type="textShortCheckable", save if the subtask was already checked (data-answer-checked) and if it was a correct or incorrect result (data-answer-checked-result)
        if (
          subtaskInput.getAttribute("data-answer-type") === "textShortCheckable"
        ) {
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].answerChecked = subtaskInput.getAttribute("data-answer-checked");
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].answerCheckedResult = subtaskInput.getAttribute(
            "data-answer-checked-result"
          );
          if (showDebugLogs)
            console.log(
              `- - - Saving answer checked state for task ${taskIndex} subtask ${subtaskIndex}:`,
              worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
                subtaskIndex
              ].answerChecked,
              worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
                subtaskIndex
              ].answerCheckedResult
            );
        }

        if (
          subtaskInput.getAttribute("data-answer-type") === "multipleChoice"
        ) {
          // Check all multiple choice buttons (.btn-subtask-multiple-choice-answer) if they are active
          // If they are active, save the selected answer and if it was correct
          const selectedAnswer = Array.from(
            document.querySelectorAll(
              `.btn-subtask-multiple-choice-answer[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
            )
          ).find((btn) => btn.classList.contains("active"));
          if (selectedAnswer) {
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].multipleChoice.selectedAnswer = selectedAnswer.id;
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].multipleChoice.correct =
              selectedAnswer.getAttribute("value") === "true";
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].multipleChoice.opened = true;
            if (showDebugLogs)
              console.log(
                `- - - Saving multiple choice answer for task ${taskIndex} subtask ${subtaskIndex}:`,
                worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
                  subtaskIndex
                ].multipleChoice.selectedAnswer,
                worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
                  subtaskIndex
                ].multipleChoice.correct
              );
          } else {
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].multipleChoice.selectedAnswer = null;
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].multipleChoice.correct = null;
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].multipleChoice.opened = false;
          }
        } else {
          // If the subtask is not a multiple choice, save the student solution
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].studentSolution = subtaskInput.value;
          if (showDebugLogs)
            console.log(
              `- - - Saving student answer for task ${taskIndex} subtask ${subtaskIndex}`
            );
        }
      }

      // Save subtask phrasing helper state
      const phrasingHelper = document.querySelector(
        `.btn-subtask-phrasing-helper[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
      );
      if (phrasingHelper) {
        if (phrasingHelper.getAttribute("data-hint-opened") === "true") {
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].phrasingHelperOpened = true;
          if (showDebugLogs)
            console.log(
              `- - - Saving phrasing helper state for task ${taskIndex} subtask ${subtaskIndex}: opened`
            );
        } else {
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].phrasingHelperOpened = false;
          if (showDebugLogs)
            console.log(
              `- - - Saving phrasing helper state for task ${taskIndex} subtask ${subtaskIndex}: not opened`
            );
        }
      }

      // Save subtask hints state (open or closed), check for data-hint-opened
      const subtaskHintButtons = document.querySelectorAll(
        `.btn-subtask-hint[data-subtask-id="task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}"]`
      );
      subtaskHintButtons.forEach((button, hintIndex) => {
        if (button.getAttribute("data-hint-opened") === "true") {
          if (
            !worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].hintsOpened
          ) {
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].hintsOpened = [];
          }
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintsOpened[hintIndex] = true;
        } else {
          if (
            !worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].hintsOpened
          ) {
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].hintsOpened = [];
          }
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintsOpened[hintIndex] = false;
        }
        if (showDebugLogs)
          console.log(
            `- - - Saving subtask hint state for task ${taskIndex} subtask ${subtaskIndex} hint ${hintIndex}:`,
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].hintsOpened[hintIndex]
          );
      });

      // Save multiple choice state
      const mcContainer = document.getElementById(
        `task-${worksheet.titleTechnical}-${taskIndex}-${subtaskIndex}-mc`
      );
      if (mcContainer) {
        if (
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintMultipleChoice
        ) {
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintMultipleChoice.opened = mcContainer.classList.contains("show");
          console.log(
            `- - - Saved hintMultipleChoice.open for task ${taskIndex} subtask ${subtaskIndex}:`,
            worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
              subtaskIndex
            ].hintMultipleChoice.opened
          );

          // If an answer was selected, save which one and if it was correct
          const selectedAnswer = Array.from(
            mcContainer.querySelectorAll(".btn-subtask-multiple-choice-answer")
          ).find((btn) => btn.classList.contains("active"));
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintMultipleChoice.selectedAnswer = selectedAnswer
            ? selectedAnswer.innerHTML
            : null;
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintMultipleChoice.correct = selectedAnswer
            ? selectedAnswer.value === "true"
              ? true
              : false
            : null;
          if (showDebugLogs)
            console.log(
              `- - - Saved hintMultipleChoice.selectedAnswer for task ${taskIndex} subtask ${subtaskIndex}:`,
              worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
                subtaskIndex
              ].hintMultipleChoice.selectedAnswer
            );
          if (showDebugLogs)
            console.log(
              `- - - Saved hintMultipleChoice.correct for task ${taskIndex} subtask ${subtaskIndex}:`,
              worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
                subtaskIndex
              ].hintMultipleChoice.correct
            );
        }

        if (mcContainer.classList.contains("show")) {
          const selectedAnswer = Array.from(
            mcContainer.querySelectorAll(".btn-subtask-multiple-choice-answer")
          ).find((btn) => btn.classList.contains("active"));
          worksheetData[currentWorksheet].tasks[taskIndex].subtasks[
            subtaskIndex
          ].hintMultipleChoice.selectedAnswer = selectedAnswer
            ? selectedAnswer.id
            : null;
        }
      }
    });
  });

  localStorage.setItem(worksheetDataKey, JSON.stringify(worksheetData));
}

// Start or end tracking the current subtask
function startTrackingCurrentSubtask() {
  if (
    currentActivePeriod.subtaskId !== currentSubtaskId &&
    currentActivePeriod.subtaskId !== null
  ) {
    // If the current subtask is different from the one we are tracking, stop tracking the old one
    endTrackingCurrentSubtask();

    // Start tracking the new subtask
    currentActivePeriod = {
      subtaskId: currentSubtaskId,
      start: new Date(),
      end: null,
      totalTime: 0,
    };
    if (showDebugLogs)
      console.log(
        "Starting tracking of current subtask ",
        currentActivePeriod.subtaskId,
        " at ",
        currentActivePeriod.start
      );
  } else if (currentActivePeriod.subtaskId === null) {
    // If we are not tracking any subtask, start tracking the current one
    currentActivePeriod.subtaskId = currentSubtaskId;
    currentActivePeriod.start = new Date();
    if (showDebugLogs)
      console.log(
        "Starting tracking of current subtask ",
        currentActivePeriod.subtaskId,
        " at ",
        currentActivePeriod.start
      );
  }
}

function endTrackingCurrentSubtask() {
  if (currentActivePeriod.subtaskId !== null) {
    // If we are tracking a subtask, end tracking it
    currentActivePeriod.end = new Date();
    currentActivePeriod.totalTime =
      (currentActivePeriod.end - currentActivePeriod.start) / 1000; // in seconds
    activePeriods.push(currentActivePeriod);
    if (showDebugLogs)
      console.log(
        "Ending tracking of current subtask ",
        currentActivePeriod.subtaskId,
        " at ",
        currentActivePeriod.end
      );
    localStorage.setItem("activePeriods", JSON.stringify(activePeriods));

    // Reset current active period
    currentActivePeriod = {
      subtaskId: null,
      start: null,
      end: null,
      totalTime: 0,
    };
  }
}

// Pin code
document
  .getElementById("pin-code-button")
  .addEventListener("click", function () {
    const button = document.getElementById("pin-code-button");
    // const moveButton = document.getElementById("move-code-button");
    const codeCard = document.getElementById("card-code");
    if (button.classList.contains("active")) {
      button.classList.remove("active");
      button.setAttribute("title", "Code an der rechten Seite anheften");
      codeCard.classList.remove("code-pinned");
      codeCard.classList.remove("col-md-4");
      // moveButton.classList.add("hide-element");
    } else {
      button.classList.add("active");
      button.setAttribute(
        "title",
        "Code wieder an ursprünglicher Position anzeigen"
      );
      codeCard.classList.add("code-pinned");
      codeCard.classList.add("col-md-4");
      // moveButton.classList.remove("hide-element");
    }
  });

// Change code language
document
  .getElementById("worksheet-code-language-buttons")
  .addEventListener("click", function (e) {
    if (e.target.classList.contains("change-code-language")) {
      currentSelectedLanguage = e.target.getAttribute("data-code-language");
      localStorage.setItem("currentSelectedLanguage", currentSelectedLanguage);
      if (showDebugLogs)
        console.log("Saving selected language: ", currentSelectedLanguage);
      document.querySelectorAll(".change-code-language").forEach((button) => {
        button.classList.remove("active");
      });
      e.target.classList.add("active");
      document.getElementById(
        "worksheet-code"
      ).innerHTML = `<code class="language-${currentSelectedLanguage}">${worksheets[currentIndex].code[currentSelectedLanguage]}</code>`;
      hljs.highlightAll();
      hljs.initLineNumbersOnLoad({
        singleLine: true,
      });
      getCodeHelpers(
        currentIndex,
        worksheets[currentIndex],
        currentSelectedLanguage
      );

      // For each element with class "language-specific", show or hide based on currentSelectedLanguage
      document.querySelectorAll(".language-specific").forEach((element) => {
        if (
          element.classList.contains("task-language-" + currentSelectedLanguage)
        ) {
          element.classList.remove("hide-element");
        } else {
          element.classList.add("hide-element");
        }
      });
    }
  });

// Copy code to clipboard
document
  .getElementById("copy-code-to-clipboard")
  .addEventListener("click", function () {
    var code = worksheets[currentIndex].code[currentSelectedLanguage];
    navigator.clipboard.writeText(code);
    // Small animation on button
    var button = document.getElementById("copy-code-to-clipboard");
    button.classList.add("btn-outline-success");
    var buttonText = button.innerHTML;
    button.innerHTML = '<i class="bi bi-check"></i> Code kopiert!';
    setTimeout(function () {
      button.classList.remove("btn-outline-success");
      button.innerHTML = buttonText;
    }, 1000);
  });

// Export code and download
document.getElementById("export-code").addEventListener("click", function () {
  var code = worksheets[currentIndex].code[currentSelectedLanguage];
  const dataStr = `data:text/${currentSelectedLanguage};charset=utf-8,${encodeURIComponent(
    code
  )}`;
  const downloadAnchor = document.createElement("a");
  downloadAnchor.setAttribute("href", dataStr);
  downloadAnchor.setAttribute(
    "download",
    worksheets[currentIndex].codeFilename[currentSelectedLanguage]
  );
  document.body.appendChild(downloadAnchor);
  downloadAnchor.click();
  document.body.removeChild(downloadAnchor);
});

// Export user data as JSON file
document.getElementById("export-data").addEventListener("click", function () {
  // Retrieve the hierarchical data from localStorage
  const worksheetData = JSON.parse(localStorage.getItem("worksheetData")) || {};
  endTrackingCurrentSubtask();
  worksheetData["activePeriods"] =
    JSON.parse(localStorage.getItem("activePeriods")) || {};

  // Convert the data to a JSON string with indentation for readability
  const dataStr =
    "data:text/json;charset=utf-8," +
    encodeURIComponent(JSON.stringify(worksheetData, null, 2));

  // Create a temporary anchor element to trigger the download
  const downloadAnchor = document.createElement("a");
  downloadAnchor.setAttribute("href", dataStr);
  downloadAnchor.setAttribute(
    "download",
    `AB_${subjectName}_${courseName}.json`
  );

  // Append the anchor to the document, trigger the download, and remove the anchor
  document.body.appendChild(downloadAnchor);
  downloadAnchor.click();
  document.body.removeChild(downloadAnchor);
});

// Print worksheet
document
  .getElementById("print-worksheet")
  .addEventListener("click", function () {
    window.print();
  });

// Debugging helpers
// Show id of input and textarea elements
function showInputIDs() {
  (function showInputAndTextareaIDs() {
    const style = `
      font-size: 12px;
      color: white;
      background: rgba(0, 0, 0, 0.7);
      padding: 2px 4px;
      margin-top: 2px;
      display: inline-block;
      border-radius: 4px;
      font-family: monospace;
    `;

    document.querySelectorAll('input[type="text"], textarea').forEach((el) => {
      const id = el.id;
      if (id) {
        const label = document.createElement("div");
        label.textContent = `ID: ${id}`;
        label.setAttribute("style", style);
        el.insertAdjacentElement("afterend", label);
      }
    });

    console.log("IDs wurden angezeigt.");
  })();
}
