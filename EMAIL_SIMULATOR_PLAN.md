# Email Simulator Implementation Plan

## Current State

### Implemented Files

#### Model Classes (`core/shared`)
- `Mail.scala` - Mail model with id, sender, subject, body, folder, timestamp, etc.
- `InboxState.scala` - Inbox state management with folder tracking and mail operations
- `MailInteraction.scala` - Read-only mail interaction
- `MailEditor.scala` - Editable mail interaction (same structure)

#### HTML Renderers (`client/main`)
- `HtmlMailInteractionRenderer.scala` - Renders MailInteraction
- `HtmlMailEditorRenderer.scala` - Renders MailEditor
- `MailInteractionCSS.scala` - CSS for MailInteraction
- `EmailSimulatorCSS.scala` - CSS for MailEditor

#### CSS File (`homepage/css/workbook`)
- `email-simulator.css` - External CSS file with all styles

#### WebElements (`client/webElements/editor`)
- `MailEditor.scala` - Editor component (in `code/MailEditor` folder)
- `WebEditorConfig.scala` - Editor configuration

### Test Coverage

#### Unit Tests
- `InboxStateSpec.scala` - Comprehensive InboxState tests (25 tests)
- `HtmlMailInteractionRendererSpec.scala` - Renderer tests
- `MailInteractionRendererSpec.scala` - CSS and rendering tests
- `MailEditorRendererSpec.scala` - Editor renderer tests

## Key Design Decisions

### 1. Architecture
- **Model**: Pure Scala case classes in `core` module
- **Renderer**: HTML rendering with Laminar in `client` module
- **Editor**: Based on `EvaEditor` pattern with `SimpleWebEditor` trait

### 2. CSS Organization
- Separate CSS objects: `MailInteractionCSS` and `EmailSimulatorCSS`
- Dedicated CSS file in `homepage/css/workbook/email-simulator.css`
- CSS follows BEM-like naming convention

### 3. File Locations
```
modules/
├── core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/emailSimulator/
│   ├── Mail.scala
│   ├── InboxState.scala
│   ├── MailInteraction.scala
│   └── MailEditor.scala
└── client/src/main/scala/it/evadid/homepage/
    ├── workbook/htmlRenderer/interactionRenderer/emailSimulator/
    │   ├── HtmlMailInteractionRenderer.scala
    │   ├── HtmlMailEditorRenderer.scala
    │   ├── MailInteractionCSS.scala
    │   └── EmailSimulatorCSS.scala
    └── webElements/editor/code/MailEditor/
        └── MailEditor.scala
```

## Next Steps

### 1. Visual Style Matching
- [ ] Update CSS to match Python original's Qt stylesheet
- [ ] Ensure folder tree styling matches QTreeWidget
- [ ] Match button styles from style.qss

### 2. Interaction Features
- [ ] Add click handlers for mail selection
- [ ] Add folder selection handlers
- [ ] Add mail action buttons (read, archive, delete)

### 3. Editor Functionality
- [ ] Implement mail editing capabilities
- [ ] Add form for new mail creation
- [ ] Implement save functionality

### 4. Additional Tests
- [ ] Add tests for mail action handlers
- [ ] Add integration tests
- [ ] Add CSS class verification tests

## Python Original Reference

The Python implementation uses PySide6 with:
- QMainWindow with top bar layout
- Folder tree (QTreeWidget)
- Mail list (QListWidget)
- Mail viewer (QTextBrowser)
- Action buttons (archiv, mark, delete)

## Current Differences

1. **No folder tree** - Currently using folder cards instead of tree
2. **No mail viewer** - Only preview line shown
3. **No email interaction** - Click handlers not yet implemented
4. **Different visual style** - Qt stylesheet vs CSS
5. **No status bar** - Missing top bar with status indicators

## Status

- [x] Model classes implemented
- [x] HTML renderers implemented
- [x] CSS files created
- [x] Editor structure created
- [x] Unit tests added
- [x] Code committed and pushed
- [ ] Visual style matching to be completed
- [ ] Interaction handlers to be implemented
