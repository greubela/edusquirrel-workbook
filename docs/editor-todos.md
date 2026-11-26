# Editor follow-ups

## Remaining architecture work

- `flatMapSwitch` can end on an outdated value when its source changes in consecutive transactions (`LabelSelectionSpec`). Texts chosen from a finite key set use `LabelSelection` (`formatted` fills `{field}` placeholders); texts of a `LanguageMap` are `localizedText` of it and the page language. Switches remain only over open sets of content IDs that load on demand: `SimpleTextEditor`'s configured placeholder and `HtmlLoginElement`'s status (keys from authentication failures, changing only across asynchronous steps).
