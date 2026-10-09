# Workbook interaction roadmap

The newer [PDF/ZIP migration inventory](docs/digital-workbook-migration.md) covers all linked downloads with page references and current implementation status. Use it for implementation planning; the suggestions below retain the earlier content roadmap.

This document inventories teaching materials for the phishing, bitcoin and evacuation workbooks and proposes future digital exercises. It is a content roadmap, not a list of implemented APIs or committed requirements.

The digital phishing mailbox and local writing practice are implemented; see [Email simulator](docs/email-simulator.md) and `homepage/phishingWorkbook/index.html`. Bitcoin/evacuation suggestions and the explanatory phishing chapters below still need authoring and integration. Existing general-purpose interactions should be reused before adding new types.

## Overview

The workbooks are designed for late secondary school students (grades 9-10) with varying time requirements:
- **Phishing**: ~6 hours (45 minutes each)
- **Bitcoin**: Not specified
- **Evacuation (Gitterautomat)**: Not specified

---

## 1. Phishing Workbook

**Location**: `resources/workbookresources/phishing/Arbeitsheft_Phishing/`

**Target Group**: Late secondary school (grades 9-10)

**Duration**: ~6 hours

### Module Structure

#### A. Einstieg (Introduction) - Teacher Slides
- **Format**: PowerPoint slide deck (FürLehrkraft/Einstieg.pptx)
- **Interactions**: None (teacher-facing)

#### B. E-Mail Simulator
**Location**: `E-Mail-Simulation/`

- **Original resource**: Python with PySide6
- **Current web implementation**: `MailInteraction` for sorting and `MailEditor` for local composing, reply and forwarding. Five exclusive folders, actual sender addresses, simulated link destinations/attachments and final-placement grading are implemented. The original 15 teaching messages are imported without executing Python.
- **Digital entry page**: `homepage/phishingWorkbook/index.html`

**Possible extensions beyond the current mailbox**:
1. **Email Client Simulator**
   - Visual email inbox interface
   - Ability to view email headers (show/hide)
   - Display sender name vs. actual sender
   - Show/hide URL previews
   - Interactive "click to open" email content

2. **Email Analysis Tasks**
   - Multiple choice questions about:
     - Suspicious elements (look and feel)
     - Display names vs. actual addresses
     - Hidden URLs
     - Domain spoofing detection
   - Drag-and-drop: Sort email elements into categories (safe/suspicious)
   - Text input: Identify suspicious phrases
   - Hotspot interaction: Click on suspicious elements in email screenshot

3. **Feedback System**
   - GPT-assisted feedback on analysis
   - Auto-validation for technical questions
   - Step-by-step guidance

#### C. Module Workbook (WorkbookPhishing.pdf)

##### Technik_1: Netzwerkübertragung (Network Transmission)
- **Multiple Choice Questions** about:
  - How emails travel through networks
  - TCP/IP basics
  - DNS lookup process
- **True/False** statements with explanations
- **Diagram Labeling**: Annotate network flow diagram
- **Drag-and-Drop**: Sequence network steps

##### Technik_2: Domainspoofing
- **Multiple Choice**: Identify spoofed domains
- **Text Matching**: Match domain names with their visual appearance
- **Hotspot**: Click on subtle differences in domain names
- **Fill-in-the-blanks**: Complete domain analysis sentences

##### Vertiefung_1: Psychologische_Hintergruende
- **Multiple Choice**: Behavioral psychology concepts
- **Ranking**: Order psychological manipulation steps
- **Case Study Analysis**: Read scenario, select appropriate response
- **Short Answer**: Explain psychological tactics

##### Vertiefung_2: Spearphishing
- **Multiple Choice**: Identify spearphishing indicators
- **Matching**: Match attack techniques to descriptions
- **Scenario Evaluation**: Read personalized emails, identify spearphishing
- **Text Comparison**: Compare general vs. targeted phishing

##### Vertiefung_3: Schadsoftware (Malware)
- **Multiple Choice**: Malware types and symptoms
- **Sequence Ordering**: Malware infection steps
- **Drag-and-Drop**: Match malware to removal techniques
- **Risk Assessment**: Rating scale for infection likelihood

##### Vertiefung_4: Spionage (Espionage)
- **Multiple Choice**: Espionage techniques
- **Case Study**: Analyze espionage email examples
- **Source Evaluation**: Assess credibility of information sources
- **Reflection Questions**: Long-form written responses

##### Warnzeichen_1: Look_and_Feel
- **Image Comparison**: Side-by-side legitimate vs. fake
- **Multiple Choice**: Identify visual red flags
- **Drag-and-Drop**: Sort elements by legitimacy
- **Hotspot**: Click on suspicious design elements

##### Warnzeichen_2: Anzeigenamen (Display Names)
- **Multiple Choice**: Identify fake display names
- **Text Matching**: Match email addresses with display names
- **True/False**: Display name safety statements
- **Fill-in-the-blanks**: Complete safety guidelines

##### Warnzeichen_3: Versteckte_URLs
- **Hotspot Interaction**: Click on hidden URLs in text
- **Multiple Choice**: URL analysis questions
- **Text Extraction**: Extract URLs from email text
- **URL Validation**: Determine if URL is safe

##### Warnzeichen_4: Domainspoofing (Domain Spoofing)
- **Multiple Choice**: Domain analysis questions
- **Drag-and-Drop**: Compare legitimate vs. spoofed domains
- **Text Comparison**: Find subtle character differences
- **Quiz**: Domain name verification

---

## 2. Bitcoin Workbook

**Location**: `resources/workbookresources/bitcoin/WorkbookBitcoin/`

**Key Files**:
- `Workbook_Bitcoin_Lernendenversion.pdf` - Student version
- `Workbook_Bitcoin_Hilfekarten.pdf` - Help cards
- `Workbook_Bitcoin_Lösungsvorschläge.pdf` - Solution suggestions

### Interaction Types Required

#### A. Conceptual Understanding
- **Multiple Choice Questions**: Bitcoin basics, blockchain, mining
- **True/False**: Common misconceptions
- **Matching**: Match terms to definitions
- **Fill-in-the-blanks**: Key concepts and processes

#### B. Technical Visualization
- **Drag-and-Drop**: Sequence blockchain blocks
- **Diagram Completion**: Fill in transaction flow diagram
- **Interactive Timeline**: Order Bitcoin events chronologically
- **Visual Comparison**: Compare traditional vs. digital payment

#### C. Security Concepts
- **Scenario Evaluation**: Read security scenarios, rate risk levels
- **Multiple Choice**: Best security practices
- **Drag-and-Drop**: Sort security measures by effectiveness
- **Hotspot**: Identify insecure wallet configurations

#### D. Wallet Management
- **Simulation**: Virtual wallet creation process
- **Input Fields**: Enter seed phrases (simulated)
- **Validation**: Check wallet address format
- **Step-by-step Wizard**: Complete wallet setup

#### E. Transaction Analysis
- **Transaction Explorer**: View transaction details
- **Input/Output Matching**: Match inputs to outputs
- **Fee Calculator**: Calculate transaction fees
- **Time Estimation**: Estimate confirmation times

#### F. Mining Concept
- **Simulation**: Interactive mining puzzle
- **Multiple Choice**: Proof-of-work explanation
- **Diagram Completion**: Mining process flow
- **True/False**: Mining misconceptions

#### G. Risk Assessment
- **Case Study Analysis**: Evaluate cryptocurrency risks
- **Ranking**: Order risk levels
- **Multiple Choice**: Risk mitigation strategies
- **Written Reflection**: Personal risk assessment

---

## 3. Evacuation Workbook (Gitterautomat / Grid Automaton)

**Location**: `resources/workbookpdfs/20211110EvakuierungGitterautomat.pdf`

**Target**: Computer science concepts, pathfinding algorithms

### Interaction Types Required

#### A. Grid Simulation
- **Interactive Grid**: Click cells to set starting points
- **Drag-and-Drop**: Place obstacles on grid
- **Multiple Choice**: Select evacuation goals
- **Configuration Panel**: Set simulation parameters

#### B. Pathfinding Algorithms
- **Step-by-step Visualization**: Watch algorithm execution
- **Interactive Play/Pause**: Control simulation speed
- **Multiple Choice**: Predict algorithm behavior
- **Hotspot**: Click to explain algorithm decisions

#### C. Algorithm Comparison
- **Drag-and-Drop**: Match algorithms to use cases
- **Multiple Choice**: Compare Dijkstra, BFS, A*
- **Scenario Selection**: Choose appropriate algorithm
- **Performance Comparison**: Analyze time complexity

#### D. Graph Theory Concepts
- **Node/Edge Manipulation**: Create custom graphs
- **Multiple Choice**: Graph theory terminology
- **Matching**: Match concepts to definitions
- **True/False**: Graph theory misconceptions

#### E. Simulation Parameters
- **Input Fields**: Configure simulation settings
- **Slider Controls**: Adjust speed, population, etc.
- **Dropdown Menus**: Select strategies
- **Toggle Switches**: Enable/disable features

#### F. Case Studies
- **Read Scenario**: Analyze evacuation scenarios
- **Hotspot**: Identify problematic areas
- **Multiple Choice**: Select best evacuation route
- **Written Analysis**: Explain reasoning

---

## Implementation Guidelines

### Interaction Types to Implement

1. **Multiple Choice**
   - Single and multiple selection
   - Visual feedback for correct/incorrect
   - Explanation bubbles

2. **Text Input**
   - Validation with regex patterns
   - Auto-formatting (e.g., addresses)
   - GPT-assisted feedback

3. **Drag-and-Drop**
   - Sort items into categories
   - Match items to definitions
   - Sequence items chronologically

4. **Hotspot/Click Interaction**
   - Click on image elements
   - Hover for information
   - Zone detection

5. **Code Editor**
   - Python/JavaScript syntax highlighting
   - Execution preview
   - Error suggestions

6. **Diagram Editor**
   - Connect nodes
   - Add labels
   - Visual customization

7. **Slider/Range Input**
   - Numeric values
   - Visual progress indicators

8. **Radio/Checkbox Groups**
   - Single or multiple selection
   - "Other" option with text input

9. **Image Annotation**
   - Draw on images
   - Add markers
   - Save annotations

10. **Timeline/Sequence**
    - Order events chronologically
    - Interactive timeline
    - Visual progress

### Common Patterns

- **Step-by-step wizards** for complex tasks
- **Progress indicators** showing completion
- **Save/Resume** functionality for long workbooks
- **GPT-assisted feedback** for open-ended questions
- **Instant validation** for technical questions
- **Visual feedback** for all interactions

### Technical Requirements

- **Frontend**: Scala.js with Laminar
- **Backend**: JVM for server-side processing
- **Storage**: InteractionVariable-backed progress with browser persistence and existing synchronization infrastructure; standalone simulator use does not require a database
- **AI Integration**: OpenAI API for intelligent feedback

---

## File References

- Phishing simulator: `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/emailSimulator/`
- Digital phishing content: `modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreatePhishingWorkbook.scala`
- Programming Exercise: `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/basic/HtmlProgrammingExerciseRenderer.scala`
- Reorder Interaction: `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/reorderExercise/HtmlReorderInteractionRenderer.scala`
- Sorting Interaction: `modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/sortingExercise/HtmlSortingInteractionRenderer.scala`

---

## Testing Considerations

1. **Unit Tests**: Test individual interaction components
2. **Integration Tests**: Test interaction workflows
3. **User Acceptance Testing**: Validate with target audience
4. **Performance Tests**: Ensure smooth execution with large grids
