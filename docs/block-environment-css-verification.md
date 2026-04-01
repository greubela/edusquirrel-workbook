# Block Environment CSS verification

This note verifies that the recent `homepage/css/workbook.css` pruning did **not** remove selectors that are still used by classes in the Scala `interactionPlugins/blockEnvironment` package.

## Method

1. Collected CSS class names used in `src/main/scala/interactionPlugins/blockEnvironment/**/*.scala` (from `className := "..."` literals).
2. Compared class selectors present in:
   - pre-prune `workbook.css` (`git show 242b7b7:homepage/css/workbook.css`), and
   - current `workbook.css`.
3. Computed removed selector set and checked intersection with block-environment class names.
4. Verified where block-environment classes are styled (`homepage/css/workbook.css` vs `homepage/css/blockEditor.css`).

## Result

- Removed selectors from `workbook.css`: **101**
- Intersection with class names used in `interactionPlugins/blockEnvironment`: **none**

So, no selectors removed from `workbook.css` were still referenced by block-environment class names.

## Notes

- Most block-environment-specific styling is in `homepage/css/blockEditor.css`.
- `workbook.css` now mainly contains shared workbook/editor shell styles.
