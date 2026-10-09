// QuickJS has no Intl. The translator only builds a collator for sorting results, which the candidate path never uses.
if (typeof globalThis.Intl === 'undefined') {
    globalThis.Intl = {Collator: class { compare(a, b) { return a < b ? -1 : (a > b ? 1 : 0); } }};
}
