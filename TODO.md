# To do

## Editor

- Dirty state on-close handler asking user to confirm close.
- Save and cancel buttons.
- Ajax call to save content.
- POST handler for ajax call.
- Use `:db.fn/cas` to commit. If fail, show error message with link to edit in new tab (showing any unanticipated changes) and maintain dirty state.
- Use `::dd/annotate-tx` fn.

## Ideas

Display edit history.
- Add config to track tx annotation ident, display ident in history.
- display diffs https://codemirror.net/demo/merge.html

UI to toggle deprecated flag in detail view.

UI to create new documentation entities, using a configurable ident namespace e.g. `:doc.<section>/<topic>`.
- Add `::dd/allow-new-doc-entity-pred` configuration option.
