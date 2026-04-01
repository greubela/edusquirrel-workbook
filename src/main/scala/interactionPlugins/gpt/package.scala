package interactionPlugins

/**
 * Canonical GPT interaction UI composition path:
 * `GptExerciseFactory` builds each exercise section with `HtmlBasicTextInteraction`
 * and a `GptButtonLine`, which wires `HtmlGPTMessenger` and `HtmlGptGrader`.
 */
package object gpt
