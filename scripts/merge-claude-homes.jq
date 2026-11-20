# Merges the Funda and Pararius results of the Claude scheduled task into homes.json.
# Input: the current homes.json. $results[0]: {"funda": [homes] or null, "pararius": [homes] or null}.
# null means the source failed: its entries stay exactly as they are.

def label_is_kept:
  (.energy_label // "unknown") as $label
  | ["A+++++", "A++++", "A+++", "A++", "A+", "A", "B", "unknown"] | any(. == $label);

# Keeps only the known fields, drops label C or worse and duplicate URLs.
def clean_results:
  map(select(label_is_kept) | .energy_label = (.energy_label // "unknown"))
  | unique_by(.url);

def merge_source($name; $fresh):
  [.[] | select(.source == $name)] as $previous
  | ($previous | map({key: .url, value: .first_seen}) | from_entries) as $firstSeen
  | ($fresh | map(.url)) as $freshUrls
  | [.[] | select(.source != $name)]
    + ($fresh | map({
        source: $name, address, price_eur, living_area_m2, bedrooms, energy_label, status, url,
        first_seen: ($firstSeen[.url] // $today), last_seen: $today, no_longer_listed: false
      }))
    + ($previous
        | map(select(.url as $url | $freshUrls | any(. == $url) | not))
        | map(.no_longer_listed = true));

reduce ("funda", "pararius") as $name (.;
  $results[0][$name] as $fresh
  | if $fresh == null then . else merge_source($name; $fresh | clean_results) end)
