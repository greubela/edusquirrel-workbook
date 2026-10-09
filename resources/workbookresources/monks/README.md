# Mons Komputarius scene artwork

The digital workbook is built by CreateMonksWorkbook, launched at
homepage/monksWorkbook/index.html, and localized through
resources/languageMaps/eva/monksworkbook/.

Source: Andre Greubel, **Rekursion mit den Mönchen von Mons Komputarius**,
version dated 29.04.2025, supplied as “2025 04 29 Rekursion Mönche angefangen.pdf”.
The theater script is on pages 5–6; the counting continuation is on page 3.

Images 01–05 already existed. Images 06–18 were generated with imagegen using
the existing sepia panels as visual references, then reviewed for scene content.
The separate artwork under resources/img/art/marie/monkworkbook/ is preserved;
it uses a different style and is not mixed into this slideshow.

| Image | Scene | Used by panels |
| --- | --- | --- |
| Image01.jpg | Journey to Mons Komputarius | arrival |
| Image02.jpg | Hasty entrance into the temple | entrance |
| Image03.jpg | Impatient request to sort the cards | impatience |
| Image04.jpg | Conversation about the journey | journey |
| Image05.jpg | Asking the master for help | help |
| Image06.png | Putting unsorted cards on the novice's tray | tray |
| Image07.png | Master retains the largest card, passes the smaller problem | largest |
| Image08.png | Each waiting monk retains one card | smaller |
| Image09.png | Empty tray reaches the traveler | empty |
| Image10.png | Last monk puts the first card on the empty tray | firstreturn |
| Image11.png | Preceding monk puts his card left of the returned cards | combine |
| Image12.png | Master completes the sorted result | sorted |
| Image13.png | Traveler leaves calmly and notices his surroundings | farewell |
| Image14.png | Child shows the returned traveler a tower | family |
| Image15.png | One brick removed, one tally mark, smaller tower remains | count |
| Image16.png | Master teaches patience while the traveler listens | patience |
| Image17.png | Traveler gives the empty tray back; novice returns to the last monk | giveback |
| Image18.png | Traveler gratefully realizes he can apply the method alone | understood |

The numerical example ends **9, 7, 4, 2** from left to right: descending order,
because the script puts each retained maximum on the left during the return.
Numerals are illustrative additions; the PDF does not prescribe exact card values.
Every one of the 18 panels uses a distinct image resource. The model test checks this to prevent accidental reuse.

This first digital edition includes all tasks 1a–1g and 2a–2j, the two original
explanations, two navigable slideshows (16 theater panels, 2 continuation panels),
and fifteen independent text responses with stable task IDs. Tasks 2c–2d keep
the paper-sketch option and add a textual record of each state. Drawing directly
inside the workbook and automatic assessment are future work.

Run the model and serialization checks with:
sbt 'client/testOnly *MonksWorkbookSpec'

After building the client bundle, use the repository's normal local site assembly
and open /monksWorkbook/. The homepage also contains a card linking to it.
