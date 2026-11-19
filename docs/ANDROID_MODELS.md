# Data Models

References: [PRD §8–9](design-kit/PRD.md) and [API §2, §5–6](design-kit/API.md).

`model/` holds pure Kotlin rules. It never stores Compose state, a `Context`, or credentials.

- **Enums.** `WasteCategory`, `DisposalMethod`, and `QuantityKind` use the canonical API names. The UI maps them to Indonesian labels through string resources.
- **Quantities.** `Quantity.Weight` and `Quantity.Count` are mutually exclusive. The factories enforce limits, and `fromFields` returns `null` for payloads with:
  - both values or neither;
  - a value of the wrong kind;
  - a value out of range.
- **Parsing.** `parseQuantity` accepts ASCII digits with surrounding whitespace trimmed, plus one decimal comma or point in kilogram mode only.
  - Thousands separators are not supported, so `1.001` means 1.001 kg (1001 g) and `1.000,00` is rejected.
  - Grams and item counts are always whole numbers.
  - Kilograms allow up to three decimals and convert exactly to grams, without floating point.
- **Unit switching.** `QuantityInput.changeUnit` converts between grams and kilograms exactly. Switching between weight and count clears the value, as does switching weight units while the current value is invalid.
- **Request fields.** `Quantity.toRequestFields()` produces `quantity_kind`, `weight_g`, and `item_count`. The unused field is always an explicit JSON `null`, so a `PATCH` that changes the kind never keeps the old amount. Kilograms are a display and input choice only; payloads always carry integer grams.
- **Summaries.** `WasteSummary` takes server values and never recomputes aggregates from a local list. `handled` and `avoided` each keep grams and item counts separately, and a `null` rate means there were no handling activities.
- **Formatting.** `WasteFormat` returns numbers and unit enums separately so the UI can use resource labels.
  - Detail weights show up to three kilogram decimals, and summaries show two.
  - Percentages show one decimal when needed, and a `null` percentage stays `null`.
- **Dates.** Activity dates are `LocalDate` values, not UTC timestamps. Server timestamps are formatted in the account's time zone.
  - `ActivityDates` works from server dates and instants and never silently uses the device clock.
  - 7- and 30-day ranges are inclusive.
  - Creates allow today and the previous 30 days; edits may keep an older existing date.

`java.time` is available through core library desugaring.

## Tests

```sh
./gradlew :app:testDebugUnitTest --tests 'com.example.wastenot.model.*'
```

The tests cover:

- limits and the exclusive choice of weight or count;
- invalid input;
- gram and kilogram round trips;
- display rounding and `null` rates;
- the PRD §9.4 server fixture;
- day rollover, account time zones, and DST;
- create and edit date ranges.
