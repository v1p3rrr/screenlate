// QuickJS has no console. Yomitan warns through it when a text processor's variants are cut at 4096 (Serbo-Croatian
// text with many c, s and z), and without one the warning throws and the lookup fails. The messages carry the
// looked-up text, which logs must not, so they are dropped.
if (typeof globalThis.console === 'undefined') {
    const ignore = () => {};
    globalThis.console = {log: ignore, info: ignore, warn: ignore, error: ignore, debug: ignore};
}
