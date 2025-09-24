package interactionPlugins.automaton

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}

object AutomatonStyles {

  val styles: L.Element = styleTag(
    s"""
       |.automaton-exercise .automaton-toolbar { 
       |  display: flex;
       |  flex-wrap: wrap;
       |  gap: var(--space-sm);
       |  align-items: center;
       |  margin-bottom: var(--space-md);
       |}
       |
       |.automaton-exercise .automaton-toolbar button {
       |  background: var(--color-surface-muted);
       |  border: 1px solid var(--color-border);
       |  color: var(--color-text-primary);
       |  padding: var(--space-xs) var(--space-md);
       |  border-radius: var(--radius-sm);
       |  font-size: 0.85rem;
       |  cursor: pointer;
       |  transition: background 0.2s ease, color 0.2s ease;
       |  line-height: 1.2;
       |}
       |
       |.automaton-exercise .automaton-toolbar button.active,
       |.automaton-exercise .automaton-toolbar button:hover {
       |  background: var(--color-primary);
       |  color: var(--color-text-inverse);
       |}
       |
       |.automaton-exercise .automaton-toolbar .automaton-layout-controls {
       |  display: inline-flex;
       |  align-items: center;
       |  gap: var(--space-xs);
       |  flex-wrap: wrap;
       |}
       |
       |.automaton-exercise .automaton-toolbar select {
       |  border: 1px solid var(--color-border);
       |  background: var(--color-surface);
       |  color: var(--color-text-primary);
       |  border-radius: var(--radius-sm);
       |  padding: var(--space-xs) var(--space-sm);
       |  font-size: 0.85rem;
       |}
       |
       |.automaton-toolbar .automaton-transformation-group {
       |  display: inline-flex;
       |  flex-wrap: wrap;
       |  gap: var(--space-xs);
       |}
       |
       |.automaton-editor-component {
       |  display: flex;
       |  flex-direction: column;
       |  gap: var(--space-sm);
       |}
       |
       |.automaton-mode-toggle {
       |  display: inline-flex;
       |  border-radius: var(--radius-sm);
       |  overflow: hidden;
       |  border: 1px solid var(--color-border);
       |}
       |
       |.automaton-mode-toggle button {
       |  border: none;
       |  background: transparent;
       |  color: var(--color-text-secondary);
       |  padding: var(--space-xs) var(--space-md);
       |}
       |
       |.automaton-mode-toggle button.active {
       |  background: var(--color-primary);
       |  color: var(--color-text-inverse);
       |}
       |
       |.automaton-editor-area {
       |  position: relative;
       |  border: 1px dashed var(--color-border);
       |  border-radius: var(--radius-md);
       |  background: var(--color-surface-muted);
       |  height: 380px;
       |  overflow: hidden;
       |}
       |
       |.automaton-editor-area .automaton-transition-layer {
       |  position: absolute;
       |  inset: 0;
       |  width: 100%;
       |  height: 100%;
       |  pointer-events: none;
       |}
       |
       |.automaton-editor-area .automaton-label-layer,
       |.automaton-editor-area .automaton-nodes-layer {
       |  position: absolute;
       |  inset: 0;
       |  pointer-events: none;
       |}
       |
       |.automaton-node {
       |  position: absolute;
       |  width: 64px;
       |  height: 64px;
       |  border-radius: 50%;
       |  border: 3px solid var(--color-border-strong);
       |  background: var(--color-surface);
       |  display: flex;
       |  align-items: center;
       |  justify-content: center;
       |  font-weight: 600;
       |  color: var(--color-text-primary);
       |  box-shadow: var(--shadow-soft);
       |  pointer-events: auto;
       |  transition: transform 0.1s ease, box-shadow 0.1s ease;
       |}
       |
       |.automaton-node.accepting {
       |  box-shadow: 0 0 0 4px var(--color-primary) inset;
       |}
       |
       |.automaton-node.start::before {
       |  content: '';
       |  position: absolute;
       |  left: -18px;
       |  width: 0;
       |  height: 0;
       |  border-top: 10px solid transparent;
       |  border-bottom: 10px solid transparent;
       |  border-right: 18px solid var(--color-primary);
       |}
       |
       |.automaton-node.pending,
       |.automaton-node:hover {
       |  transform: scale(1.04);
       |  box-shadow: 0 6px 16px rgba(12, 51, 89, 0.2);
       |}
       |
       |.automaton-node.selected {
       |  box-shadow: 0 0 0 3px var(--color-primary) inset, 0 8px 20px rgba(12, 51, 89, 0.2);
       |}
       |
       |.automaton-transition-label {
       |  position: absolute;
       |  transform: translate(-50%, -50%);
       |  background: var(--color-surface);
       |  border: 1px solid var(--color-border);
       |  border-radius: var(--radius-sm);
       |  padding: 2px 6px;
       |  font-size: 0.75rem;
       |  color: var(--color-text-secondary);
       |  pointer-events: auto;
       |  white-space: nowrap;
       |}
       |
       |.automaton-context-menu {
       |  position: absolute;
       |  background: var(--color-surface);
       |  border: 1px solid var(--color-border);
       |  border-radius: var(--radius-sm);
       |  box-shadow: var(--shadow-strong);
       |  display: flex;
       |  flex-direction: column;
       |  min-width: 180px;
       |  z-index: 20;
       |}
       |
       |.automaton-context-menu button {
       |  border: none;
       |  background: transparent;
       |  padding: var(--space-xs) var(--space-md);
       |  text-align: left;
       |  font-size: 0.85rem;
       |  color: var(--color-text-primary);
       |}
       |
       |.automaton-context-menu button:hover {
       |  background: var(--color-surface-muted);
       |}
       |
       |.connection-preview {
       |  fill: none;
       |  stroke: var(--color-primary);
       |  stroke-width: 2.5px;
       |  stroke-dasharray: 6 6;
       |  pointer-events: none;
       |}
       |
       |.automaton-node-actions {
       |  position: absolute;
       |  left: var(--space-md);
       |  bottom: var(--space-md);
       |  display: flex;
       |  flex-direction: column;
       |  gap: var(--space-xs);
       |  padding: var(--space-sm) var(--space-md);
       |  background: var(--color-surface);
       |  border: 1px solid var(--color-border);
       |  border-radius: var(--radius-md);
       |  box-shadow: var(--shadow-soft);
       |  max-width: 240px;
       |  pointer-events: auto;
       |  z-index: 15;
       |}
       |
       |.automaton-node-actions .title {
       |  font-weight: 600;
       |  color: var(--color-text-primary);
       |}
       |
       |.automaton-node-actions .actions {
       |  display: flex;
       |  flex-direction: column;
       |  gap: var(--space-xs);
       |}
       |
       |.automaton-node-actions button {
       |  border: none;
       |  background: var(--color-surface-muted);
       |  border-radius: var(--radius-sm);
       |  padding: var(--space-xs) var(--space-sm);
       |  color: var(--color-text-primary);
       |  cursor: pointer;
       |  transition: background 0.2s ease, color 0.2s ease;
       |}
       |
       |.automaton-node-actions button:hover:not(:disabled) {
       |  background: var(--color-primary);
       |  color: var(--color-text-inverse);
       |}
       |
       |.automaton-node-actions button:disabled {
       |  cursor: default;
       |  opacity: 0.6;
       |}
       |
       |.automaton-node-actions button.danger {
       |  background: var(--color-danger-muted);
       |  color: var(--color-danger-strong);
       |}
       |
       |.automaton-node-actions button.danger:hover {
       |  background: var(--color-danger);
       |  color: var(--color-text-inverse);
       |}
       |
       |.automaton-simulator {
       |  display: flex;
       |  flex-direction: column;
       |  gap: var(--space-sm);
       |}
       |
       |.automaton-simulator .input-row {
       |  display: flex;
       |  gap: var(--space-sm);
       |  align-items: center;
       |}
       |
       |.automaton-simulator input[type="text"] {
       |  flex: 1 1 auto;
       |  border-radius: var(--radius-sm);
       |  border: 1px solid var(--color-border);
       |  padding: var(--space-xs) var(--space-sm);
       |  font-size: 0.95rem;
       |}
       |
       |.automaton-simulator-controls {
       |  display: flex;
       |  gap: var(--space-xs);
       |  flex-wrap: wrap;
       |}
       |
       |.automaton-step-list {
       |  border-radius: var(--radius-sm);
       |  border: 1px solid var(--color-border);
       |  background: var(--color-interaction-background);
       |  max-height: 180px;
       |  overflow-y: auto;
       |  padding: var(--space-xs);
       |}
       |
       |.automaton-step-list li {
       |  list-style: none;
       |  padding: 4px 6px;
       |  border-radius: var(--radius-xs);
       |  font-size: 0.8rem;
       |  color: var(--color-text-secondary);
       |}
       |
       |.automaton-step-list li.active {
       |  background: var(--color-primary-muted);
       |  color: var(--color-text-primary);
       |}
       |
       |.automaton-hint-list,
       |.automaton-test-list {
       |  display: flex;
       |  flex-direction: column;
       |  gap: var(--space-xs);
       |}
       |
       |.automaton-hints {
       |  display: flex;
       |  flex-direction: column;
       |  gap: var(--space-xs);
       |}
       |
       |.automaton-hint-list li,
       |.automaton-test-list li {
       |  list-style: none;
       |  padding: 6px 10px;
       |  border-radius: var(--radius-sm);
       |  background: var(--color-surface-muted);
       |  color: var(--color-text-secondary);
       |}
       |
       |.automaton-test-result-summary {
       |  display: flex;
       |  justify-content: space-between;
       |  align-items: center;
       |  background: var(--color-surface-muted);
       |  border-radius: var(--radius-sm);
       |  padding: var(--space-xs) var(--space-sm);
       |  font-size: 0.85rem;
       |  color: var(--color-text-secondary);
       |}
       |
       |.automaton-test-result {
       |  display: flex;
       |  justify-content: space-between;
       |  align-items: center;
       |  padding: 6px 10px;
       |  border-radius: var(--radius-sm);
       |  font-size: 0.85rem;
       |}
       |
       |.automaton-test-result.pass {
       |  background: rgba(0, 159, 77, 0.1);
       |  color: var(--color-success);
       |}
       |
       |.automaton-test-result.fail {
       |  background: rgba(255, 77, 77, 0.12);
       |  color: var(--color-error);
       |}
       |
       |.automaton-empty-placeholder {
       |  color: var(--color-text-secondary);
       |  font-size: 0.85rem;
       |}
       |
       |.automaton-transition-layer path {
       |  stroke: var(--color-text-secondary);
       |  stroke-width: 2px;
       |  fill: none;
       |  marker-end: url(#automaton-arrow);
       |}
       |
       |.automaton-transition-layer path.self-loop {
       |  marker-end: url(#automaton-arrow);
       |}
       |""".stripMargin
  )
}
